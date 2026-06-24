package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("fund_holding")
public class FundHolding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long accountId;
    private String fundCode;
    private String fundName;
    private String fundType;
    private Integer activeFund;
    private BigDecimal holdingAmount;
    private BigDecimal holdingShare;
    private BigDecimal holdingCost;
    private BigDecimal currentEstimateNav;
    private BigDecimal latestOfficialNav;
    private BigDecimal holdingProfit;
    private BigDecimal holdingProfitRate;
    private BigDecimal dailyProfit;
    private Integer holdingDays;
    private String sourcePlatform;
    private Integer regularInvestment;
    private Integer coreHolding;
    private Integer watchFocus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    public Integer getActiveFund() { return activeFund; }
    public void setActiveFund(Integer activeFund) { this.activeFund = activeFund; }
    public BigDecimal getHoldingAmount() { return holdingAmount; }
    public void setHoldingAmount(BigDecimal holdingAmount) { this.holdingAmount = holdingAmount; }
    public BigDecimal getHoldingShare() { return holdingShare; }
    public void setHoldingShare(BigDecimal holdingShare) { this.holdingShare = holdingShare; }
    public BigDecimal getHoldingCost() { return holdingCost; }
    public void setHoldingCost(BigDecimal holdingCost) { this.holdingCost = holdingCost; }
    public BigDecimal getCurrentEstimateNav() { return currentEstimateNav; }
    public void setCurrentEstimateNav(BigDecimal currentEstimateNav) { this.currentEstimateNav = currentEstimateNav; }
    public BigDecimal getLatestOfficialNav() { return latestOfficialNav; }
    public void setLatestOfficialNav(BigDecimal latestOfficialNav) { this.latestOfficialNav = latestOfficialNav; }
    public BigDecimal getHoldingProfit() { return holdingProfit; }
    public void setHoldingProfit(BigDecimal holdingProfit) { this.holdingProfit = holdingProfit; }
    public BigDecimal getHoldingProfitRate() { return holdingProfitRate; }
    public void setHoldingProfitRate(BigDecimal holdingProfitRate) { this.holdingProfitRate = holdingProfitRate; }
    public BigDecimal getDailyProfit() { return dailyProfit; }
    public void setDailyProfit(BigDecimal dailyProfit) { this.dailyProfit = dailyProfit; }
    public Integer getHoldingDays() { return holdingDays; }
    public void setHoldingDays(Integer holdingDays) { this.holdingDays = holdingDays; }
    public String getSourcePlatform() { return sourcePlatform; }
    public void setSourcePlatform(String sourcePlatform) { this.sourcePlatform = sourcePlatform; }
    public Integer getRegularInvestment() { return regularInvestment; }
    public void setRegularInvestment(Integer regularInvestment) { this.regularInvestment = regularInvestment; }
    public Integer getCoreHolding() { return coreHolding; }
    public void setCoreHolding(Integer coreHolding) { this.coreHolding = coreHolding; }
    public Integer getWatchFocus() { return watchFocus; }
    public void setWatchFocus(Integer watchFocus) { this.watchFocus = watchFocus; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

