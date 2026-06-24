package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("fund_nav_daily")
public class FundNavDaily {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private LocalDate navDate;
    private BigDecimal unitNav;
    private BigDecimal accumulatedNav;
    private BigDecimal dailyGrowthRate;
    private String sourceName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public LocalDate getNavDate() { return navDate; }
    public void setNavDate(LocalDate navDate) { this.navDate = navDate; }
    public BigDecimal getUnitNav() { return unitNav; }
    public void setUnitNav(BigDecimal unitNav) { this.unitNav = unitNav; }
    public BigDecimal getAccumulatedNav() { return accumulatedNav; }
    public void setAccumulatedNav(BigDecimal accumulatedNav) { this.accumulatedNav = accumulatedNav; }
    public BigDecimal getDailyGrowthRate() { return dailyGrowthRate; }
    public void setDailyGrowthRate(BigDecimal dailyGrowthRate) { this.dailyGrowthRate = dailyGrowthRate; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

