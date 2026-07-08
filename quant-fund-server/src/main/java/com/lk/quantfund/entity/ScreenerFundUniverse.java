package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("screener_fund_universe")
public class ScreenerFundUniverse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private String fundName;
    private String fundType;
    private String shareClass;
    private String mainFundCode;
    private String companyName;
    private String managerName;
    private LocalDate establishDate;
    private BigDecimal fundSize;
    private String trackingIndex;
    private Integer activeFund;
    private String riskLevel;
    private String status;
    private String sourceName;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    public String getShareClass() { return shareClass; }
    public void setShareClass(String shareClass) { this.shareClass = shareClass; }
    public String getMainFundCode() { return mainFundCode; }
    public void setMainFundCode(String mainFundCode) { this.mainFundCode = mainFundCode; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }
    public LocalDate getEstablishDate() { return establishDate; }
    public void setEstablishDate(LocalDate establishDate) { this.establishDate = establishDate; }
    public BigDecimal getFundSize() { return fundSize; }
    public void setFundSize(BigDecimal fundSize) { this.fundSize = fundSize; }
    public String getTrackingIndex() { return trackingIndex; }
    public void setTrackingIndex(String trackingIndex) { this.trackingIndex = trackingIndex; }
    public Integer getActiveFund() { return activeFund; }
    public void setActiveFund(Integer activeFund) { this.activeFund = activeFund; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(LocalDateTime lastSyncTime) { this.lastSyncTime = lastSyncTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
