package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_analysis_report")
public class AiAnalysisReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long accountId;
    private Long holdingId;
    private String fundCode;
    private String modelName;
    private String action;
    private String actionText;
    private BigDecimal suggestAmount;
    private BigDecimal suggestRatio;
    private BigDecimal confidence;
    private String riskLevel;
    private String deadline;
    private String strategy;
    private String reasonsJson;
    private String risksJson;
    private String dataSummary;
    private String finalConclusion;
    private String requestPayload;
    private String responsePayload;
    private Integer fallbackUsed;
    private LocalDateTime analysisTime;
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
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActionText() { return actionText; }
    public void setActionText(String actionText) { this.actionText = actionText; }
    public BigDecimal getSuggestAmount() { return suggestAmount; }
    public void setSuggestAmount(BigDecimal suggestAmount) { this.suggestAmount = suggestAmount; }
    public BigDecimal getSuggestRatio() { return suggestRatio; }
    public void setSuggestRatio(BigDecimal suggestRatio) { this.suggestRatio = suggestRatio; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getReasonsJson() { return reasonsJson; }
    public void setReasonsJson(String reasonsJson) { this.reasonsJson = reasonsJson; }
    public String getRisksJson() { return risksJson; }
    public void setRisksJson(String risksJson) { this.risksJson = risksJson; }
    public String getDataSummary() { return dataSummary; }
    public void setDataSummary(String dataSummary) { this.dataSummary = dataSummary; }
    public String getFinalConclusion() { return finalConclusion; }
    public void setFinalConclusion(String finalConclusion) { this.finalConclusion = finalConclusion; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    public Integer getFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(Integer fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public LocalDateTime getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(LocalDateTime analysisTime) { this.analysisTime = analysisTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

