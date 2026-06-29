package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("quant_backtest_result")
public class QuantBacktestResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String strategyName;
    private String modelVersion;
    private String fundCode;
    private String fundName;
    private String fundType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String benchmarkCode;
    private BigDecimal totalReturnRate;
    private BigDecimal annualReturnRate;
    private BigDecimal maxDrawdownRate;
    private BigDecimal winRate;
    private BigDecimal sharpeRatio;
    private BigDecimal calmarRatio;
    private Integer tradeCount;
    private String resultJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getBenchmarkCode() { return benchmarkCode; }
    public void setBenchmarkCode(String benchmarkCode) { this.benchmarkCode = benchmarkCode; }
    public BigDecimal getTotalReturnRate() { return totalReturnRate; }
    public void setTotalReturnRate(BigDecimal totalReturnRate) { this.totalReturnRate = totalReturnRate; }
    public BigDecimal getAnnualReturnRate() { return annualReturnRate; }
    public void setAnnualReturnRate(BigDecimal annualReturnRate) { this.annualReturnRate = annualReturnRate; }
    public BigDecimal getMaxDrawdownRate() { return maxDrawdownRate; }
    public void setMaxDrawdownRate(BigDecimal maxDrawdownRate) { this.maxDrawdownRate = maxDrawdownRate; }
    public BigDecimal getWinRate() { return winRate; }
    public void setWinRate(BigDecimal winRate) { this.winRate = winRate; }
    public BigDecimal getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(BigDecimal sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    public BigDecimal getCalmarRatio() { return calmarRatio; }
    public void setCalmarRatio(BigDecimal calmarRatio) { this.calmarRatio = calmarRatio; }
    public Integer getTradeCount() { return tradeCount; }
    public void setTradeCount(Integer tradeCount) { this.tradeCount = tradeCount; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
