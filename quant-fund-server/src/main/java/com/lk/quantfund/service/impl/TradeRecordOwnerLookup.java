package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TradeRecordOwnerLookup implements ResourceOwnerLookup {

    private final TradeRecordMapper tradeRecordMapper;

    public TradeRecordOwnerLookup(TradeRecordMapper tradeRecordMapper) {
        this.tradeRecordMapper = tradeRecordMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.TRADE_RECORD;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        TradeRecord record = tradeRecordMapper.selectById(resourceId);
        return record == null ? Optional.empty() : Optional.ofNullable(record.getUserId());
    }
}

