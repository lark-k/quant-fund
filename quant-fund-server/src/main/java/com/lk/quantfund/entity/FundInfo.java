package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("fund_info")
public class FundInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private String fundName;
    private String fundType;
    private Integer activeFund;
    private String trackingIndex;
    private String managerName;
    private String companyName;
    private String riskLevel;
    private String themeTags;
    private String manualCategory;
    private String sourceName;
    private LocalDateTime sourceUpdateTime;
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
    public Integer getActiveFund() { return activeFund; }
    public void setActiveFund(Integer activeFund) { this.activeFund = activeFund; }
    public String getTrackingIndex() { return trackingIndex; }
    public void setTrackingIndex(String trackingIndex) { this.trackingIndex = trackingIndex; }
    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getThemeTags() { return themeTags; }
    public void setThemeTags(String themeTags) { this.themeTags = themeTags; }
    public String getManualCategory() { return manualCategory; }
    public void setManualCategory(String manualCategory) { this.manualCategory = manualCategory; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public LocalDateTime getSourceUpdateTime() { return sourceUpdateTime; }
    public void setSourceUpdateTime(LocalDateTime sourceUpdateTime) { this.sourceUpdateTime = sourceUpdateTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

