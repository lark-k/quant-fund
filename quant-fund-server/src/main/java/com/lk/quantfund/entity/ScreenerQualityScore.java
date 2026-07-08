package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("screener_quality_score")
public class ScreenerQualityScore {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private LocalDate scoreDate;
    private BigDecimal qualityScore;
    private BigDecimal returnScore;
    private BigDecimal riskScore;
    private BigDecimal stabilityScore;
    private BigDecimal excessScore;
    private BigDecimal peerScore;
    private BigDecimal liquidityScore;
    private BigDecimal dataScore;
    private Integer rankNo;
    private BigDecimal rankPercentile;
    private String recommendLevel;
    private String reasonsJson;
    private String risksJson;
    private String modelVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public LocalDate getScoreDate() { return scoreDate; }
    public void setScoreDate(LocalDate scoreDate) { this.scoreDate = scoreDate; }
    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }
    public BigDecimal getReturnScore() { return returnScore; }
    public void setReturnScore(BigDecimal returnScore) { this.returnScore = returnScore; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public BigDecimal getStabilityScore() { return stabilityScore; }
    public void setStabilityScore(BigDecimal stabilityScore) { this.stabilityScore = stabilityScore; }
    public BigDecimal getExcessScore() { return excessScore; }
    public void setExcessScore(BigDecimal excessScore) { this.excessScore = excessScore; }
    public BigDecimal getPeerScore() { return peerScore; }
    public void setPeerScore(BigDecimal peerScore) { this.peerScore = peerScore; }
    public BigDecimal getLiquidityScore() { return liquidityScore; }
    public void setLiquidityScore(BigDecimal liquidityScore) { this.liquidityScore = liquidityScore; }
    public BigDecimal getDataScore() { return dataScore; }
    public void setDataScore(BigDecimal dataScore) { this.dataScore = dataScore; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public BigDecimal getRankPercentile() { return rankPercentile; }
    public void setRankPercentile(BigDecimal rankPercentile) { this.rankPercentile = rankPercentile; }
    public String getRecommendLevel() { return recommendLevel; }
    public void setRecommendLevel(String recommendLevel) { this.recommendLevel = recommendLevel; }
    public String getReasonsJson() { return reasonsJson; }
    public void setReasonsJson(String reasonsJson) { this.reasonsJson = reasonsJson; }
    public String getRisksJson() { return risksJson; }
    public void setRisksJson(String risksJson) { this.risksJson = risksJson; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
