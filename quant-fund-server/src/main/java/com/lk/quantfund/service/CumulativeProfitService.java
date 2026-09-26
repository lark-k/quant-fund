package com.lk.quantfund.service;

import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.TradeRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A separate, official-NAV-only book. No writes to the existing financial tables.
 * A disposal exchanges book value for net proceeds; a NAV mark recognises only
 * the change in the remaining book. This avoids counting sold profits twice.
 */
@Service
public class CumulativeProfitService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final JdbcTemplate jdbc;

    public CumulativeProfitService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record FundProfit(Long accountId, String fundCode, BigDecimal cumulativeProfit,
                             LocalDate officialNavDate, boolean archived) {}
    public record Overview(BigDecimal totalCumulativeProfit, BigDecimal historicalProfit,
                           List<FundProfit> funds) {}
    private record Nav(LocalDate date, BigDecimal value) {}
    private record Position(Long holdingId, Long userId, Long accountId, String fundCode,
                            BigDecimal shares, BigDecimal book, LocalDate date,
                            BigDecimal nav, LocalDate minimumDate, boolean tracking) {}

    @Transactional
    public Overview overview(Long userId) {
        lockUser(userId);
        // Covers pre-upgrade holdings without applying any personalised seed to other users.
        List<FundHolding> holdings = jdbc.query("SELECT * FROM fund_holding WHERE user_id=? AND deleted=0",
                (rs, n) -> {
                    FundHolding h = new FundHolding();
                    h.setId(rs.getLong("id")); h.setUserId(userId); h.setAccountId(rs.getLong("account_id"));
                    h.setFundCode(rs.getString("fund_code")); h.setHoldingShare(rs.getBigDecimal("holding_share"));
                    h.setHoldingCost(rs.getBigDecimal("holding_cost"));
                    h.setHoldingAmount(rs.getBigDecimal("holding_amount"));
                    h.setLatestOfficialNav(rs.getBigDecimal("latest_official_nav"));
                    return h;
                }, userId);
        for (FundHolding h : holdings) ensurePosition(h, true);
        List<Long> ids = jdbc.queryForList("SELECT holding_id FROM cumulative_profit_position WHERE user_id=? AND tracking=1",
                Long.class, userId);
        for (Long id : ids) mark(position(id));
        List<FundProfit> funds = jdbc.query("""
                SELECT f.*, (SELECT MAX(p.nav_date) FROM cumulative_profit_position p
                    WHERE p.user_id=f.user_id AND p.account_id=f.account_id AND p.fund_code=f.fund_code) AS official_date,
                    CASE WHEN EXISTS (SELECT 1 FROM fund_holding h WHERE h.user_id=f.user_id
                    AND h.account_id=f.account_id AND h.fund_code=f.fund_code AND h.deleted=0) THEN 0 ELSE 1 END AS archived
                FROM cumulative_profit_fund f WHERE f.user_id=? ORDER BY f.account_id, f.fund_code
                """, (rs, n) -> new FundProfit(rs.getLong("account_id"), rs.getString("fund_code"),
                money(rs.getBigDecimal("profit")), date(rs.getDate("official_date")), rs.getInt("archived") == 1), userId);
        BigDecimal history = jdbc.queryForObject("SELECT opening_history FROM cumulative_profit_user WHERE user_id=?", BigDecimal.class, userId);
        BigDecimal total = nz(history);
        for (FundProfit f : funds) {
            total = total.add(f.cumulativeProfit());
            if (f.archived()) history = history.add(f.cumulativeProfit());
        }
        return new Overview(money(total), money(history), funds);
    }

    /** Call before editing/deleting a holding, or before applying a confirmed trade. */
    @Transactional
    public void capture(FundHolding holding) {
        lockUser(holding.getUserId());
        ensurePosition(holding, true);
        mark(position(holding.getId()));
    }

    /** New manual positions and corrections change exposure, never lifetime profit. */
    @Transactional
    public void rebase(FundHolding holding) {
        lockUser(holding.getUserId());
        Position old = position(holding.getId());
        if (old == null) {
            ensurePosition(holding, false);
            return;
        }
        // A renamed code/account starts a new exposure; the previous fund's profit stays in its book.
        ensureFund(holding.getUserId(), holding.getAccountId(), holding.getFundCode());
        Nav nav = latestNav(holding.getFundCode());
        BigDecimal price = nav == null ? nz(holding.getLatestOfficialNav()) : nav.value();
        BigDecimal shares = exposure(holding, price);
        jdbc.update("""
                UPDATE cumulative_profit_position SET account_id=?,fund_code=?,shares=?,book_value=?,
                    nav_date=?,unit_nav=?,minimum_nav_date=?,tracking=1 WHERE holding_id=?
                """, holding.getAccountId(), holding.getFundCode(), shares, precise(shares.multiply(price)),
                sqlDate(nav == null ? null : nav.date()), price, sqlDate(nav == null ? null : nav.date()), holding.getId());
        event(position(holding.getId()), "CORRECTION", ZERO, "correction:" + UUID.randomUUID());
    }

    @Transactional
    public void archive(FundHolding holding) {
        capture(holding);
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM trade_record WHERE holding_id=? AND user_id=? AND deleted=0 AND trade_status='PROCESSING'",
                Integer.class, holding.getId(), holding.getUserId());
        // A pending disposal still needs its book for final settlement after deletion.
        if (pending == null || pending == 0) {
            jdbc.update("UPDATE cumulative_profit_position SET tracking=0 WHERE holding_id=?", holding.getId());
        }
        event(position(holding.getId()), "ARCHIVE", ZERO, "archive:" + UUID.randomUUID());
    }

    @Transactional
    public void completedTrade(FundHolding before, TradeRecord trade, LocalDate effectiveNavDate) {
        lockUser(before.getUserId());
        String key = "trade:" + trade.getId();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM cumulative_profit_event WHERE event_key=?", Integer.class, key) > 0) return;
        ensurePosition(before, true);
        Position p = position(before.getId());
        mark(p);
        p = position(before.getId());
        boolean buy = List.of("BUY", "REGULAR_INVEST", "CONVERT_IN").contains(trade.getTradeType());
        BigDecimal shares = nz(trade.getTradeShare());
        if (shares.signum() <= 0 && nz(trade.getTradeNav()).signum() > 0) {
            shares = nz(trade.getTradeAmount()).divide(trade.getTradeNav(), 4, RoundingMode.HALF_UP);
        }
        // Amount-only clear is an existing supported workflow.
        if (!buy && shares.signum() <= 0) shares = p.shares();
        BigDecimal delta;
        BigDecimal remainingShares;
        BigDecimal book;
        if (buy) {
            remainingShares = p.shares().add(shares);
            book = p.book().add(nz(trade.getTradeAmount()));
            delta = nz(trade.getTradeFee()).negate();
        } else {
            BigDecimal removed = p.shares().signum() == 0 || shares.compareTo(p.shares()) >= 0
                    ? p.book() : p.book().multiply(shares).divide(p.shares(), 8, RoundingMode.HALF_UP);
            remainingShares = p.shares().subtract(shares).max(ZERO);
            book = p.book().subtract(removed);
            delta = nz(trade.getTradeAmount()).subtract(nz(trade.getTradeFee())).subtract(removed);
        }
        LocalDate minimum = later(p.minimumDate(), effectiveNavDate);
        jdbc.update("UPDATE cumulative_profit_position SET shares=?,book_value=?,minimum_nav_date=?,tracking=1 WHERE holding_id=?",
                remainingShares, book, sqlDate(minimum), p.holdingId());
        addProfit(p, delta);
        event(position(p.holdingId()), "TRADE", delta, key);
        // If the official NAV is already available, recognise the remaining shares at once.
        mark(position(p.holdingId()));
    }

    private void lockUser(Long userId) {
        jdbc.update("INSERT INTO cumulative_profit_user(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=user_id", userId);
        jdbc.queryForObject("SELECT user_id FROM cumulative_profit_user WHERE user_id=? FOR UPDATE", Long.class, userId);
    }

    private void ensureFund(Long userId, Long accountId, String code) {
        jdbc.update("INSERT INTO cumulative_profit_fund(user_id,account_id,fund_code) VALUES (?,?,?) ON DUPLICATE KEY UPDATE fund_code=fund_code",
                userId, accountId, code);
    }

    private void ensurePosition(FundHolding h, boolean includeOpeningProfit) {
        if (position(h.getId()) != null) return;
        ensureFund(h.getUserId(), h.getAccountId(), h.getFundCode());
        Nav nav = latestNav(h.getFundCode());
        BigDecimal price = nav == null ? nz(h.getLatestOfficialNav()) : nav.value();
        BigDecimal shares = exposure(h, price);
        BigDecimal book = precise(shares.multiply(price));
        jdbc.update("""
                INSERT INTO cumulative_profit_position(holding_id,user_id,account_id,fund_code,shares,book_value,nav_date,unit_nav,minimum_nav_date)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, h.getId(), h.getUserId(), h.getAccountId(), h.getFundCode(), shares, book,
                sqlDate(nav == null ? null : nav.date()), price, sqlDate(nav == null ? null : nav.date()));
        BigDecimal delta = includeOpeningProfit && price.signum() > 0 ? book.subtract(nz(h.getHoldingCost())) : ZERO;
        addProfit(position(h.getId()), delta);
        event(position(h.getId()), "OPENING", delta, "opening:" + h.getId());
    }

    private Position position(Long id) {
        List<Position> rows = jdbc.query("SELECT * FROM cumulative_profit_position WHERE holding_id=?", (rs, n) ->
                new Position(id, rs.getLong("user_id"), rs.getLong("account_id"), rs.getString("fund_code"),
                        rs.getBigDecimal("shares"), rs.getBigDecimal("book_value"), date(rs.getDate("nav_date")),
                        rs.getBigDecimal("unit_nav"), date(rs.getDate("minimum_nav_date")), rs.getInt("tracking") == 1), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Nav latestNav(String code) {
        List<Nav> rows = jdbc.query("SELECT nav_date,unit_nav FROM fund_nav_daily WHERE fund_code=? AND deleted=0 AND unit_nav>0 AND nav_date<=? AND (source_name IS NULL OR UPPER(source_name) NOT LIKE '%MOCK%') ORDER BY nav_date DESC LIMIT 1",
                (rs, n) -> new Nav(date(rs.getDate("nav_date")), rs.getBigDecimal("unit_nav")), code, sqlDate(LocalDate.now()));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void mark(Position p) {
        if (p == null || !p.tracking()) return;
        Nav nav = latestNav(p.fundCode());
        if (nav == null || (p.date() != null && nav.date().isBefore(p.date()))
                || (p.minimumDate() != null && nav.date().isBefore(p.minimumDate()))) return;
        BigDecimal book = precise(p.shares().multiply(nav.value()));
        BigDecimal delta = book.subtract(p.book());
        // An undated import has no verifiable previous NAV: establish a neutral first anchor.
        if (p.nav() == null || p.nav().signum() <= 0) delta = ZERO;
        if (nav.date().equals(p.date()) && nav.value().compareTo(nz(p.nav())) == 0 && delta.signum() == 0) return;
        jdbc.update("UPDATE cumulative_profit_position SET book_value=?,nav_date=?,unit_nav=? WHERE holding_id=?",
                book, sqlDate(nav.date()), nav.value(), p.holdingId());
        addProfit(p, delta);
        event(position(p.holdingId()), "OFFICIAL_NAV", delta, "nav:" + UUID.randomUUID());
    }

    private void addProfit(Position p, BigDecimal delta) {
        jdbc.update("UPDATE cumulative_profit_fund SET profit=profit+? WHERE user_id=? AND account_id=? AND fund_code=?",
                delta, p.userId(), p.accountId(), p.fundCode());
    }

    private void event(Position p, String type, BigDecimal delta, String key) {
        jdbc.update("""
                INSERT INTO cumulative_profit_event(event_key,user_id,account_id,fund_code,holding_id,event_type,nav_date,profit_delta,shares_after,book_value_after)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, key, p.userId(), p.accountId(), p.fundCode(), p.holdingId(), type, sqlDate(p.date()), delta, p.shares(), p.book());
    }

    private static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }
    private static BigDecimal precise(BigDecimal value) { return nz(value).setScale(8, RoundingMode.HALF_UP); }
    private static BigDecimal exposure(FundHolding holding, BigDecimal nav) {
        BigDecimal shares = nz(holding.getHoldingShare());
        if (shares.signum() <= 0 && nz(holding.getHoldingAmount()).signum() > 0 && nav.signum() > 0) {
            return holding.getHoldingAmount().divide(nav, 8, RoundingMode.HALF_UP);
        }
        return shares;
    }
    private static BigDecimal money(BigDecimal value) { return nz(value).setScale(2, RoundingMode.HALF_UP); }
    private static LocalDate date(Date value) { return value == null ? null : value.toLocalDate(); }
    private static Date sqlDate(LocalDate value) { return value == null ? null : Date.valueOf(value); }
    private static LocalDate later(LocalDate a, LocalDate b) { return a == null ? b : b == null || a.isAfter(b) ? a : b; }
}
