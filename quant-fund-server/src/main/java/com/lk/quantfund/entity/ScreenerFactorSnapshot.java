package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("screener_factor_snapshot")
public class ScreenerFactorSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private LocalDate factorDate;
    @TableField("return_20d")
    private BigDecimal return20d;
    @TableField("return_60d")
    private BigDecimal return60d;
    @TableField("return_120d")
    private BigDecimal return120d;
    @TableField("return_250d")
    private BigDecimal return250d;
    @TableField("annual_return_250d")
    private BigDecimal annualReturn250d;
    @TableField("volatility_60d")
    private BigDecimal volatility60d;
    @TableField("volatility_120d")
    private BigDecimal volatility120d;
    @TableField("max_drawdown_60d")
    private BigDecimal maxDrawdown60d;
    @TableField("max_drawdown_120d")
    private BigDecimal maxDrawdown120d;
    @TableField("positive_day_ratio_60d")
    private BigDecimal positiveDayRatio60d;
    @TableField("trend_slope_60d")
    private BigDecimal trendSlope60d;
    @TableField("excess_return_60d")
    private BigDecimal excessReturn60d;
    @TableField("excess_return_120d")
    private BigDecimal excessReturn120d;
    private String benchmarkCode;
    @TableField("return_drawdown_ratio_120d")
    private BigDecimal returnDrawdownRatio120d;
    private BigDecimal returnConsistencyScore;
    private BigDecimal fundAgeYears;
    private BigDecimal peerPercentile;
    private BigDecimal fundSize;
    private Integer navSampleSize;
    private String sourceName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public LocalDate getFactorDate() { return factorDate; }
    public void setFactorDate(LocalDate factorDate) { this.factorDate = factorDate; }
    public BigDecimal getReturn20d() { return return20d; }
    public void setReturn20d(BigDecimal return20d) { this.return20d = return20d; }
    public BigDecimal getReturn60d() { return return60d; }
    public void setReturn60d(BigDecimal return60d) { this.return60d = return60d; }
    public BigDecimal getReturn120d() { return return120d; }
    public void setReturn120d(BigDecimal return120d) { this.return120d = return120d; }
    public BigDecimal getReturn250d() { return return250d; }
    public void setReturn250d(BigDecimal return250d) { this.return250d = return250d; }
    public BigDecimal getAnnualReturn250d() { return annualReturn250d; }
    public void setAnnualReturn250d(BigDecimal annualReturn250d) { this.annualReturn250d = annualReturn250d; }
    public BigDecimal getVolatility60d() { return volatility60d; }
    public void setVolatility60d(BigDecimal volatility60d) { this.volatility60d = volatility60d; }
    public BigDecimal getVolatility120d() { return volatility120d; }
    public void setVolatility120d(BigDecimal volatility120d) { this.volatility120d = volatility120d; }
    public BigDecimal getMaxDrawdown60d() { return maxDrawdown60d; }
    public void setMaxDrawdown60d(BigDecimal maxDrawdown60d) { this.maxDrawdown60d = maxDrawdown60d; }
    public BigDecimal getMaxDrawdown120d() { return maxDrawdown120d; }
    public void setMaxDrawdown120d(BigDecimal maxDrawdown120d) { this.maxDrawdown120d = maxDrawdown120d; }
    public BigDecimal getPositiveDayRatio60d() { return positiveDayRatio60d; }
    public void setPositiveDayRatio60d(BigDecimal positiveDayRatio60d) { this.positiveDayRatio60d = positiveDayRatio60d; }
    public BigDecimal getTrendSlope60d() { return trendSlope60d; }
    public void setTrendSlope60d(BigDecimal trendSlope60d) { this.trendSlope60d = trendSlope60d; }
    public BigDecimal getExcessReturn60d() { return excessReturn60d; }
    public void setExcessReturn60d(BigDecimal excessReturn60d) { this.excessReturn60d = excessReturn60d; }
    public BigDecimal getExcessReturn120d() { return excessReturn120d; }
    public void setExcessReturn120d(BigDecimal excessReturn120d) { this.excessReturn120d = excessReturn120d; }
    public String getBenchmarkCode() { return benchmarkCode; }
    public void setBenchmarkCode(String benchmarkCode) { this.benchmarkCode = benchmarkCode; }
    public BigDecimal getReturnDrawdownRatio120d() { return returnDrawdownRatio120d; }
    public void setReturnDrawdownRatio120d(BigDecimal returnDrawdownRatio120d) { this.returnDrawdownRatio120d = returnDrawdownRatio120d; }
    public BigDecimal getReturnConsistencyScore() { return returnConsistencyScore; }
    public void setReturnConsistencyScore(BigDecimal returnConsistencyScore) { this.returnConsistencyScore = returnConsistencyScore; }
    public BigDecimal getFundAgeYears() { return fundAgeYears; }
    public void setFundAgeYears(BigDecimal fundAgeYears) { this.fundAgeYears = fundAgeYears; }
    public BigDecimal getPeerPercentile() { return peerPercentile; }
    public void setPeerPercentile(BigDecimal peerPercentile) { this.peerPercentile = peerPercentile; }
    public BigDecimal getFundSize() { return fundSize; }
    public void setFundSize(BigDecimal fundSize) { this.fundSize = fundSize; }
    public Integer getNavSampleSize() { return navSampleSize; }
    public void setNavSampleSize(Integer navSampleSize) { this.navSampleSize = navSampleSize; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
