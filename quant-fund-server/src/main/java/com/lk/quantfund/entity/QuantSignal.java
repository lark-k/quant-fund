package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("quant_signal")
public class QuantSignal {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long accountId;
    private Long holdingId;
    private String fundCode;
    private String fundName;
    private String action;
    private String actionText;
    private BigDecimal suggestAmount;
    private BigDecimal suggestRatio;
    private String riskLevel;
    private BigDecimal confidence;
    private BigDecimal totalScore;
    private BigDecimal trendScore;
    private BigDecimal opportunityScore;
    private BigDecimal riskScore;
    private BigDecimal positionScore;
    private BigDecimal momentumScore;
    private String metricsJson;
    private String reasonsJson;
    private String risksJson;
    private String modelName;
    private String modelVersion;
    private LocalDate tradeDate;
    private String decisionPhase;
    private String requestPayload;
    private String responsePayload;
    private Integer fallbackUsed;
    private LocalDateTime deadline;
    private LocalDateTime signalTime;
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
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActionText() { return actionText; }
    public void setActionText(String actionText) { this.actionText = actionText; }
    public BigDecimal getSuggestAmount() { return suggestAmount; }
    public void setSuggestAmount(BigDecimal suggestAmount) { this.suggestAmount = suggestAmount; }
    public BigDecimal getSuggestRatio() { return suggestRatio; }
    public void setSuggestRatio(BigDecimal suggestRatio) { this.suggestRatio = suggestRatio; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public BigDecimal getTrendScore() { return trendScore; }
    public void setTrendScore(BigDecimal trendScore) { this.trendScore = trendScore; }
    public BigDecimal getOpportunityScore() { return opportunityScore; }
    public void setOpportunityScore(BigDecimal opportunityScore) { this.opportunityScore = opportunityScore; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public BigDecimal getPositionScore() { return positionScore; }
    public void setPositionScore(BigDecimal positionScore) { this.positionScore = positionScore; }
    public BigDecimal getMomentumScore() { return momentumScore; }
    public void setMomentumScore(BigDecimal momentumScore) { this.momentumScore = momentumScore; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public String getReasonsJson() { return reasonsJson; }
    public void setReasonsJson(String reasonsJson) { this.reasonsJson = reasonsJson; }
    public String getRisksJson() { return risksJson; }
    public void setRisksJson(String risksJson) { this.risksJson = risksJson; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getDecisionPhase() { return decisionPhase; }
    public void setDecisionPhase(String decisionPhase) { this.decisionPhase = decisionPhase; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    public Integer getFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(Integer fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public LocalDateTime getSignalTime() { return signalTime; }
    public void setSignalTime(LocalDateTime signalTime) { this.signalTime = signalTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
