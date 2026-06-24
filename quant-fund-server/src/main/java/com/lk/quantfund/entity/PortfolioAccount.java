package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("portfolio_account")
public class PortfolioAccount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String accountName;
    private String platformType;
    private BigDecimal totalAsset;
    private BigDecimal totalInvestAmount;
    private BigDecimal currentProfit;
    private BigDecimal currentProfitRate;
    private BigDecimal dailyProfit;
    private BigDecimal cashPositionRate;
    private BigDecimal equityPositionRate;
    private BigDecimal bondPositionRate;
    private BigDecimal maxSingleFundPositionRate;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getPlatformType() { return platformType; }
    public void setPlatformType(String platformType) { this.platformType = platformType; }
    public BigDecimal getTotalAsset() { return totalAsset; }
    public void setTotalAsset(BigDecimal totalAsset) { this.totalAsset = totalAsset; }
    public BigDecimal getTotalInvestAmount() { return totalInvestAmount; }
    public void setTotalInvestAmount(BigDecimal totalInvestAmount) { this.totalInvestAmount = totalInvestAmount; }
    public BigDecimal getCurrentProfit() { return currentProfit; }
    public void setCurrentProfit(BigDecimal currentProfit) { this.currentProfit = currentProfit; }
    public BigDecimal getCurrentProfitRate() { return currentProfitRate; }
    public void setCurrentProfitRate(BigDecimal currentProfitRate) { this.currentProfitRate = currentProfitRate; }
    public BigDecimal getDailyProfit() { return dailyProfit; }
    public void setDailyProfit(BigDecimal dailyProfit) { this.dailyProfit = dailyProfit; }
    public BigDecimal getCashPositionRate() { return cashPositionRate; }
    public void setCashPositionRate(BigDecimal cashPositionRate) { this.cashPositionRate = cashPositionRate; }
    public BigDecimal getEquityPositionRate() { return equityPositionRate; }
    public void setEquityPositionRate(BigDecimal equityPositionRate) { this.equityPositionRate = equityPositionRate; }
    public BigDecimal getBondPositionRate() { return bondPositionRate; }
    public void setBondPositionRate(BigDecimal bondPositionRate) { this.bondPositionRate = bondPositionRate; }
    public BigDecimal getMaxSingleFundPositionRate() { return maxSingleFundPositionRate; }
    public void setMaxSingleFundPositionRate(BigDecimal maxSingleFundPositionRate) { this.maxSingleFundPositionRate = maxSingleFundPositionRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

