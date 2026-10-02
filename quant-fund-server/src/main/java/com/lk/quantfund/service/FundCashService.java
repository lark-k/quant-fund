package com.lk.quantfund.service;

import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-account/fund cash book. Historical completed trades are never replayed on initialization. */
@Service
public class FundCashService {
    private final JdbcTemplate jdbc;
    public FundCashService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Fund(String fundCode, String fundName, BigDecimal balance, boolean archived,
                       BigDecimal pendingBuy, BigDecimal pendingSell) {}
    public record Snapshot(long version, BigDecimal unallocated, BigDecimal total, List<Fund> funds) {}
    public record AnalysisContext(BigDecimal cashBalance, BigDecimal holdingShares, BigDecimal holdingAmount,
            int pendingTrades, String lastTradeDate, long snapshotVersion, String snapshotAt) {}

    /** A short, consistent snapshot; no network requests or simulated trades while holding the lock. */
    @Transactional
    public AnalysisContext analysisContext(Long user, Long account, String code) {
        lock(user, account);
        BigDecimal cash = jdbc.queryForObject("SELECT COALESCE(SUM(balance),0) FROM fund_cash_balance WHERE account_id=? AND fund_code=?", BigDecimal.class,account,code);
        BigDecimal shares = jdbc.queryForObject("SELECT COALESCE(SUM(holding_share),0) FROM fund_holding WHERE user_id=? AND account_id=? AND fund_code=? AND deleted=0", BigDecimal.class,user,account,code);
        BigDecimal amount = jdbc.queryForObject("SELECT COALESCE(SUM(holding_amount),0) FROM fund_holding WHERE user_id=? AND account_id=? AND fund_code=? AND deleted=0", BigDecimal.class,user,account,code);
        // Also protect source proceeds when the linked conversion-in has not completed yet.
        int pending = jdbc.queryForObject("""
            SELECT COUNT(*) FROM trade_record t WHERE t.user_id=? AND t.account_id=? AND t.deleted=0
            AND t.trade_status='PROCESSING' AND (t.fund_code=? OR t.related_trade_id IN
              (SELECT s.id FROM trade_record s WHERE s.user_id=? AND s.account_id=? AND s.fund_code=? AND s.deleted=0))
            """, Integer.class,user,account,code,user,account,code);
        java.sql.Timestamp last = jdbc.queryForObject("""
            SELECT MAX(CASE WHEN update_time>trade_time THEN update_time ELSE trade_time END)
            FROM trade_record WHERE user_id=? AND account_id=? AND fund_code=? AND deleted=0 AND trade_status='COMPLETED'
            AND trade_type IN ('BUY','SELL','REGULAR_INVEST','CONVERT_IN','CONVERT_OUT')
            """, java.sql.Timestamp.class,user,account,code);
        Long version = jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM fund_cash_account WHERE account_id=?",Long.class,account);
        return new AnalysisContext(cash,shares,amount,pending,last==null?null:last.toLocalDateTime().toLocalDate().toString(),version,
                java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString());
    }
    public record Allocation(@NotBlank String fundCode,
            @NotNull @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer=13, fraction=2) BigDecimal balance) {}
    public record Update(@NotNull @PositiveOrZero Long version,
            @NotNull @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer=13, fraction=2) BigDecimal unallocated,
            @NotNull @Size(max=2000) List<@Valid Allocation> funds) {}

    /** Also serializes trade confirmation and manual edits for the same account. */
    @Transactional
    public void lock(Long user, Long account) {
        var rows = jdbc.queryForList("SELECT id FROM portfolio_account WHERE id=? AND user_id=? AND deleted=0 FOR UPDATE", account, user);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "账户不存在或无权访问");
    }

    private void initialize(Long user, Long account) {
        lock(user, account);
        jdbc.update("""
            INSERT INTO fund_cash_account(account_id,user_id,unallocated,version)
            SELECT id,user_id,COALESCE(cash_amount,0),0 FROM portfolio_account WHERE id=? AND user_id=?
            AND NOT EXISTS(SELECT 1 FROM fund_cash_account WHERE account_id=?)
            """, account, user, account);
        jdbc.update("""
            INSERT INTO fund_cash_balance(account_id,fund_code,fund_name,balance)
            SELECT account_id,fund_code,MAX(fund_name),0 FROM fund_holding h
            WHERE account_id=? AND user_id=? AND deleted=0
            AND NOT EXISTS(SELECT 1 FROM fund_cash_balance b WHERE b.account_id=h.account_id AND b.fund_code=h.fund_code)
            GROUP BY account_id,fund_code
            """, account, user);
    }

    @Transactional
    public Snapshot get(Long user, Long account) {
        initialize(user, account);
        return snapshot(user, account);
    }

    private Snapshot snapshot(Long user, Long account) {
        var funds = jdbc.query("""
            SELECT b.*, CASE WHEN EXISTS(SELECT 1 FROM fund_holding h WHERE h.account_id=b.account_id
                AND h.user_id=? AND h.fund_code=b.fund_code AND h.deleted=0) THEN 0 ELSE 1 END archived,
                COALESCE((SELECT SUM(t.trade_amount+t.trade_fee) FROM trade_record t WHERE t.account_id=b.account_id
                  AND t.user_id=? AND t.fund_code=b.fund_code AND t.deleted=0 AND t.trade_status='PROCESSING'
                  AND t.trade_type IN ('BUY','REGULAR_INVEST')),0) pending_buy,
                COALESCE((SELECT SUM(t.trade_amount-t.trade_fee) FROM trade_record t WHERE t.account_id=b.account_id
                  AND t.user_id=? AND t.fund_code=b.fund_code AND t.deleted=0 AND t.trade_status='PROCESSING'
                  AND t.trade_type='SELL'),0) pending_sell
            FROM fund_cash_balance b WHERE b.account_id=? ORDER BY archived,b.fund_code
            """, (r,n) -> new Fund(r.getString("fund_code"),r.getString("fund_name"),r.getBigDecimal("balance"),
                r.getBoolean("archived"),r.getBigDecimal("pending_buy"),r.getBigDecimal("pending_sell")), user,user,user,account);
        return jdbc.queryForObject("SELECT * FROM fund_cash_account WHERE account_id=?", (r,n) -> {
            BigDecimal unallocated = r.getBigDecimal("unallocated");
            return new Snapshot(r.getLong("version"), unallocated,
                funds.stream().map(Fund::balance).reduce(unallocated, BigDecimal::add),funds);
        }, account);
    }

    @Transactional
    public Snapshot save(Long user, Long account, Update request) {
        initialize(user, account);
        Snapshot old = snapshot(user, account);
        if (request.version() != old.version()) throw new BusinessException(ErrorCode.REPEAT_SUBMIT, "现金或交易已更新，请重新打开弹窗后调整");
        Set<String> codes = new HashSet<>();
        for (Allocation row : request.funds()) {
            if (!codes.add(row.fundCode())) throw new BusinessException(ErrorCode.BAD_REQUEST, "基金不能重复");
        }
        if (!codes.equals(new HashSet<>(old.funds().stream().map(Fund::fundCode).toList())))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "持仓列表已变化，请重新打开弹窗");
        Map<String, BigDecimal> previous = new HashMap<>();
        old.funds().forEach(f -> previous.put(f.fundCode(), f.balance()));
        String key = "manual:" + UUID.randomUUID();
        for (Allocation row : request.funds()) {
            jdbc.update("UPDATE fund_cash_balance SET balance=? WHERE account_id=? AND fund_code=?", row.balance(), account, row.fundCode());
            event(key+":"+row.fundCode(), user, account, row.fundCode(), row.balance().subtract(previous.get(row.fundCode())));
        }
        event(key+":unallocated", user, account, "", request.unallocated().subtract(old.unallocated()));
        jdbc.update("UPDATE fund_cash_account SET unallocated=?,version=version+1 WHERE account_id=?", request.unallocated(),account);
        syncTotal(user, account);
        return snapshot(user, account);
    }

    /** Returns false for a duplicate callback. All writes share the holding/trade transaction. */
    @Transactional
    public boolean completed(TradeRecord trade) {
        if (!"COMPLETED".equals(trade.getTradeStatus())) return false;
        Long user=trade.getUserId(), account=trade.getAccountId();
        initialize(user, account);
        String key="trade:"+trade.getId();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM fund_cash_event WHERE event_key=?", Integer.class,key)>0) return false;
        boolean buy=Set.of("BUY","REGULAR_INVEST","CONVERT_IN").contains(trade.getTradeType());
        BigDecimal amount=nz(trade.getTradeAmount()), fee=nz(trade.getTradeFee());
        BigDecimal delta=buy ? amount.add(fee).negate() : amount.subtract(fee).max(BigDecimal.ZERO);
        ensureFund(account,trade.getFundCode(),trade.getFundName());
        // A linked conversion transfers the source proceeds into the destination budget before purchasing.
        if ("CONVERT_IN".equals(trade.getTradeType()) && trade.getRelatedTradeId()!=null) {
            var sources=jdbc.queryForList("SELECT fund_code FROM trade_record WHERE id=? AND user_id=? AND account_id=? AND trade_type='CONVERT_OUT' AND trade_status='COMPLETED' AND deleted=0",
                trade.getRelatedTradeId(),user,account);
            if (sources.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST,"转出交易尚未完成，暂不能结算转入");
            String source=(String)sources.getFirst().get("fund_code");
            ensureFund(account,source,source);
            change(account,source,delta);
            change(account,trade.getFundCode(),delta.negate());
            event(key+":transfer-out",user,account,source,delta);
            event(key+":transfer-in",user,account,trade.getFundCode(),delta.negate());
        }
        change(account,trade.getFundCode(),delta);
        event(key,user,account,trade.getFundCode(),delta);
        jdbc.update("UPDATE fund_cash_account SET version=version+1 WHERE account_id=?",account);
        syncTotal(user,account);
        return true;
    }

    private void ensureFund(Long account,String code,String name) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM fund_cash_balance WHERE account_id=? AND fund_code=?", Integer.class,account,code)==0)
            jdbc.update("INSERT INTO fund_cash_balance(account_id,fund_code,fund_name,balance) VALUES(?,?,?,0)",account,code,name);
    }
    private void change(Long account,String code,BigDecimal delta) {
        // Never clamp: a recorded purchase beyond its budget is an explicit funding deficit.
        jdbc.update("UPDATE fund_cash_balance SET balance=balance+? WHERE account_id=? AND fund_code=?",delta,account,code);
    }
    private void event(String key,Long user,Long account,String code,BigDecimal delta) {
        jdbc.update("INSERT INTO fund_cash_event(event_key,user_id,account_id,fund_code,delta) VALUES(?,?,?,?,?)",key,user,account,code,delta);
    }
    private void syncTotal(Long user,Long account) {
        BigDecimal total=jdbc.queryForObject("SELECT unallocated+COALESCE((SELECT SUM(balance) FROM fund_cash_balance WHERE account_id=?),0) FROM fund_cash_account WHERE account_id=?",BigDecimal.class,account,account);
        jdbc.update("UPDATE portfolio_account SET cash_amount=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",total,account,user);
    }
    public boolean initialized(Long account) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM fund_cash_account WHERE account_id=?",Integer.class,account)>0;
    }
    private BigDecimal nz(BigDecimal n) { return n==null ? BigDecimal.ZERO : n.setScale(4,RoundingMode.HALF_UP); }
}
