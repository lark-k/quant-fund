package com.lk.quantfund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("trade_record")
public class TradeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long accountId;
    private Long holdingId;
    private String fundCode;
    private String fundName;
    private String tradeType;
    private String tradeStatus;
    private BigDecimal tradeAmount;
    private BigDecimal tradeShare;
    private BigDecimal tradeNav;
    private BigDecimal tradeFee;
    private LocalDateTime tradeTime;
    private Long relatedTradeId;
    private String remark;
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
    public String getTradeType() { return tradeType; }
    public void setTradeType(String tradeType) { this.tradeType = tradeType; }
    public String getTradeStatus() { return tradeStatus; }
    public void setTradeStatus(String tradeStatus) { this.tradeStatus = tradeStatus; }
    public BigDecimal getTradeAmount() { return tradeAmount; }
    public void setTradeAmount(BigDecimal tradeAmount) { this.tradeAmount = tradeAmount; }
    public BigDecimal getTradeShare() { return tradeShare; }
    public void setTradeShare(BigDecimal tradeShare) { this.tradeShare = tradeShare; }
    public BigDecimal getTradeNav() { return tradeNav; }
    public void setTradeNav(BigDecimal tradeNav) { this.tradeNav = tradeNav; }
    public BigDecimal getTradeFee() { return tradeFee; }
    public void setTradeFee(BigDecimal tradeFee) { this.tradeFee = tradeFee; }
    public LocalDateTime getTradeTime() { return tradeTime; }
    public void setTradeTime(LocalDateTime tradeTime) { this.tradeTime = tradeTime; }
    public Long getRelatedTradeId() { return relatedTradeId; }
    public void setRelatedTradeId(Long relatedTradeId) { this.relatedTradeId = relatedTradeId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}

