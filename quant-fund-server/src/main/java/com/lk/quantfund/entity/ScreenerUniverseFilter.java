package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("screener_universe_filter")
public class ScreenerUniverseFilter {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private Integer included;
    private String universeType;
    private String excludeReason;
    private Integer minNavDaysPassed;
    private Integer sizeFilterPassed;
    private Integer duplicateFilterPassed;
    private Integer statusFilterPassed;
    private LocalDateTime lastRebuildTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public Integer getIncluded() { return included; }
    public void setIncluded(Integer included) { this.included = included; }
    public String getUniverseType() { return universeType; }
    public void setUniverseType(String universeType) { this.universeType = universeType; }
    public String getExcludeReason() { return excludeReason; }
    public void setExcludeReason(String excludeReason) { this.excludeReason = excludeReason; }
    public Integer getMinNavDaysPassed() { return minNavDaysPassed; }
    public void setMinNavDaysPassed(Integer minNavDaysPassed) { this.minNavDaysPassed = minNavDaysPassed; }
    public Integer getSizeFilterPassed() { return sizeFilterPassed; }
    public void setSizeFilterPassed(Integer sizeFilterPassed) { this.sizeFilterPassed = sizeFilterPassed; }
    public Integer getDuplicateFilterPassed() { return duplicateFilterPassed; }
    public void setDuplicateFilterPassed(Integer duplicateFilterPassed) { this.duplicateFilterPassed = duplicateFilterPassed; }
    public Integer getStatusFilterPassed() { return statusFilterPassed; }
    public void setStatusFilterPassed(Integer statusFilterPassed) { this.statusFilterPassed = statusFilterPassed; }
    public LocalDateTime getLastRebuildTime() { return lastRebuildTime; }
    public void setLastRebuildTime(LocalDateTime lastRebuildTime) { this.lastRebuildTime = lastRebuildTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
