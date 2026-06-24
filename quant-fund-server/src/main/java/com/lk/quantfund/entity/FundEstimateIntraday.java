package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("fund_estimate_intraday")
public class FundEstimateIntraday {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private LocalDate estimateDate;
    private BigDecimal estimateNav;
    private BigDecimal estimateGrowthRate;
    private LocalDateTime estimateTime;
    private String sourceName;
    @TableField("`delayed`")
    private Integer delayed;
    private String rawPayload;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public LocalDate getEstimateDate() { return estimateDate; }
    public void setEstimateDate(LocalDate estimateDate) { this.estimateDate = estimateDate; }
    public BigDecimal getEstimateNav() { return estimateNav; }
    public void setEstimateNav(BigDecimal estimateNav) { this.estimateNav = estimateNav; }
    public BigDecimal getEstimateGrowthRate() { return estimateGrowthRate; }
    public void setEstimateGrowthRate(BigDecimal estimateGrowthRate) { this.estimateGrowthRate = estimateGrowthRate; }
    public LocalDateTime getEstimateTime() { return estimateTime; }
    public void setEstimateTime(LocalDateTime estimateTime) { this.estimateTime = estimateTime; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public Integer getDelayed() { return delayed; }
    public void setDelayed(Integer delayed) { this.delayed = delayed; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
