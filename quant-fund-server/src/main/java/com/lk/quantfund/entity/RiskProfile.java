package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("risk_profile")
public class RiskProfile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String riskLevel;
    private BigDecimal maxEquityPositionRate;
    private BigDecimal maxSingleFundPositionRate;
    private BigDecimal drawdownAlertRate;
    private BigDecimal dailyRiseAlertRate;
    private BigDecimal dailyFallAlertRate;
    private String configJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public BigDecimal getMaxEquityPositionRate() { return maxEquityPositionRate; }
    public void setMaxEquityPositionRate(BigDecimal maxEquityPositionRate) { this.maxEquityPositionRate = maxEquityPositionRate; }
    public BigDecimal getMaxSingleFundPositionRate() { return maxSingleFundPositionRate; }
    public void setMaxSingleFundPositionRate(BigDecimal maxSingleFundPositionRate) { this.maxSingleFundPositionRate = maxSingleFundPositionRate; }
    public BigDecimal getDrawdownAlertRate() { return drawdownAlertRate; }
    public void setDrawdownAlertRate(BigDecimal drawdownAlertRate) { this.drawdownAlertRate = drawdownAlertRate; }
    public BigDecimal getDailyRiseAlertRate() { return dailyRiseAlertRate; }
    public void setDailyRiseAlertRate(BigDecimal dailyRiseAlertRate) { this.dailyRiseAlertRate = dailyRiseAlertRate; }
    public BigDecimal getDailyFallAlertRate() { return dailyFallAlertRate; }
    public void setDailyFallAlertRate(BigDecimal dailyFallAlertRate) { this.dailyFallAlertRate = dailyFallAlertRate; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

