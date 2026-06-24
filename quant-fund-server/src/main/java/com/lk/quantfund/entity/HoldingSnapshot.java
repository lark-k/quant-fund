package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("holding_snapshot")
public class HoldingSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long accountId;
    private Long holdingId;
    private LocalDate snapshotDate;
    private BigDecimal totalAsset;
    private BigDecimal holdingAmount;
    private BigDecimal holdingProfit;
    private BigDecimal dailyProfit;
    private BigDecimal positionRate;
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
    public Long getHoldingId() { return holdingId; }
    public void setHoldingId(Long holdingId) { this.holdingId = holdingId; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public BigDecimal getTotalAsset() { return totalAsset; }
    public void setTotalAsset(BigDecimal totalAsset) { this.totalAsset = totalAsset; }
    public BigDecimal getHoldingAmount() { return holdingAmount; }
    public void setHoldingAmount(BigDecimal holdingAmount) { this.holdingAmount = holdingAmount; }
    public BigDecimal getHoldingProfit() { return holdingProfit; }
    public void setHoldingProfit(BigDecimal holdingProfit) { this.holdingProfit = holdingProfit; }
    public BigDecimal getDailyProfit() { return dailyProfit; }
    public void setDailyProfit(BigDecimal dailyProfit) { this.dailyProfit = dailyProfit; }
    public BigDecimal getPositionRate() { return positionRate; }
    public void setPositionRate(BigDecimal positionRate) { this.positionRate = positionRate; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

