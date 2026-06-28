package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.dto.trade.InvestmentPlanRequest;
import com.lk.quantfund.entity.InvestmentPlan;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.InvestmentPlanMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.InvestmentPlanService;
import com.lk.quantfund.vo.trade.InvestmentPlanVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InvestmentPlanServiceImpl implements InvestmentPlanService {

    private final InvestmentPlanMapper investmentPlanMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final TradingCalendarService tradingCalendarService;

    public InvestmentPlanServiceImpl(InvestmentPlanMapper investmentPlanMapper,
                                     PortfolioAccountMapper portfolioAccountMapper,
                                     TradingCalendarService tradingCalendarService) {
        this.investmentPlanMapper = investmentPlanMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.tradingCalendarService = tradingCalendarService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPlanVO create(InvestmentPlanRequest request) {
        Long userId = UserContext.getUserId();
        ensureAccountOwned(userId, request.accountId());
        LocalDate nextDate = nextTradingDate(request.nextExecuteDate());
        LocalDateTime now = LocalDateTime.now();
        InvestmentPlan plan = new InvestmentPlan();
        plan.setUserId(userId);
        plan.setAccountId(request.accountId());
        plan.setFundCode(request.fundCode().trim());
        plan.setFundName(request.fundName().trim());
        plan.setPlanName(StringUtils.hasText(request.planName())
                ? request.planName().trim()
                : request.fundName().trim() + "定投");
        plan.setPlanType("REGULAR_INVEST");
        plan.setAmount(request.amount());
        plan.setFrequency(normalizeFrequency(request.frequency()));
        plan.setNextExecuteDate(nextDate);
        plan.setStatus(normalizeStatus(request.status()));
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        plan.setDeleted(0);
        investmentPlanMapper.insert(plan);
        return toVO(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPlanVO update(Long id, InvestmentPlanRequest request) {
        Long userId = UserContext.getUserId();
        InvestmentPlan plan = loadOwnedPlan(userId, id);
        ensureAccountOwned(userId, request.accountId());
        LocalDate nextDate = nextTradingDate(request.nextExecuteDate());
        plan.setAccountId(request.accountId());
        plan.setFundCode(request.fundCode().trim());
        plan.setFundName(request.fundName().trim());
        plan.setPlanName(StringUtils.hasText(request.planName())
                ? request.planName().trim()
                : request.fundName().trim() + "定投");
        plan.setAmount(request.amount());
        plan.setFrequency(normalizeFrequency(request.frequency()));
        plan.setNextExecuteDate(nextDate);
        plan.setStatus(normalizeStatus(request.status()));
        plan.setUpdateTime(LocalDateTime.now());
        investmentPlanMapper.updateById(plan);
        return toVO(plan);
    }

    @Override
    public List<InvestmentPlanVO> list(Long accountId) {
        Long userId = UserContext.getUserId();
        if (accountId != null) {
            ensureAccountOwned(userId, accountId);
        }
        return investmentPlanMapper.selectList(new LambdaQueryWrapper<InvestmentPlan>()
                        .eq(InvestmentPlan::getUserId, userId)
                        .eq(accountId != null, InvestmentPlan::getAccountId, accountId)
                        .orderByAsc(InvestmentPlan::getNextExecuteDate))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPlanVO updateStatus(Long id, String status) {
        Long userId = UserContext.getUserId();
        InvestmentPlan plan = loadOwnedPlan(userId, id);
        String oldStatus = normalizeStatus(plan.getStatus());
        String nextStatus = normalizeStatus(status);
        if ("PAUSED".equals(oldStatus) && "ENABLED".equals(nextStatus)) {
            plan.setNextExecuteDate(nextExecutableDateOnResume(plan, LocalDateTime.now()));
        }
        plan.setStatus(nextStatus);
        plan.setUpdateTime(LocalDateTime.now());
        investmentPlanMapper.updateById(plan);
        return toVO(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserContext.getUserId();
        InvestmentPlan plan = loadOwnedPlan(userId, id);
        investmentPlanMapper.deleteById(plan.getId());
    }

    private InvestmentPlan loadOwnedPlan(Long userId, Long id) {
        InvestmentPlan plan = investmentPlanMapper.selectOne(new LambdaQueryWrapper<InvestmentPlan>()
                .eq(InvestmentPlan::getId, id)
                .eq(InvestmentPlan::getUserId, userId)
                .last("LIMIT 1"));
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "investment plan not found");
        }
        return plan;
    }

    private void ensureAccountOwned(Long userId, Long accountId) {
        PortfolioAccount account = portfolioAccountMapper.selectOne(new LambdaQueryWrapper<PortfolioAccount>()
                .eq(PortfolioAccount::getId, accountId)
                .eq(PortfolioAccount::getUserId, userId)
                .last("LIMIT 1"));
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "portfolio account not found");
        }
    }

    private LocalDate nextTradingDate(LocalDate date) {
        LocalDate next = date;
        while (!tradingCalendarService.isTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private LocalDate nextExecutableDateOnResume(InvestmentPlan plan, LocalDateTime now) {
        LocalDate next = plan.getNextExecuteDate() == null ? now.toLocalDate() : plan.getNextExecuteDate();
        while (true) {
            next = nextTradingDate(next);
            if (!isMissedExecuteDate(next, now)) {
                return next;
            }
            next = addFrequency(next, plan.getFrequency());
        }
    }

    private boolean isMissedExecuteDate(LocalDate date, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        return date.isBefore(today)
                || (date.isEqual(today) && !now.toLocalTime().isBefore(LocalTime.of(9, 5)));
    }

    private LocalDate addFrequency(LocalDate date, String frequency) {
        return switch (normalizeFrequency(frequency)) {
            case "DAILY" -> date.plusDays(1);
            case "BIWEEKLY", "EVERY_TWO_WEEKS" -> date.plusWeeks(2);
            case "MONTHLY" -> date.plusMonths(1);
            default -> date.plusWeeks(1);
        };
    }

    private String normalizeFrequency(String frequency) {
        String value = frequency == null ? "" : frequency.trim().toUpperCase();
        return switch (value) {
            case "DAILY", "BIWEEKLY", "EVERY_TWO_WEEKS", "MONTHLY" -> value;
            default -> "WEEKLY";
        };
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        return "PAUSED".equals(value) || "DISABLED".equals(value) ? "PAUSED" : "ENABLED";
    }

    private InvestmentPlanVO toVO(InvestmentPlan plan) {
        return new InvestmentPlanVO(
                plan.getId(),
                plan.getAccountId(),
                plan.getFundCode(),
                plan.getFundName(),
                plan.getPlanName(),
                plan.getPlanType(),
                plan.getAmount(),
                plan.getFrequency(),
                plan.getNextExecuteDate(),
                plan.getStatus(),
                plan.getUpdateTime()
        );
    }
}
