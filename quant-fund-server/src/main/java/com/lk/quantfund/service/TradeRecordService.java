package com.lk.quantfund.service;

import com.lk.quantfund.dto.trade.TradeRecordRequest;
import com.lk.quantfund.enums.TradeStatus;
import com.lk.quantfund.enums.TradeType;
import com.lk.quantfund.vo.trade.TradeRecordVO;
import java.util.List;

public interface TradeRecordService {

    TradeRecordVO create(TradeRecordRequest request);

    TradeRecordVO createAs(TradeRecordRequest request, TradeType tradeType);

    TradeRecordVO detail(Long tradeId);

    List<TradeRecordVO> list(Long accountId, Long holdingId, TradeType tradeType, TradeStatus tradeStatus);

    List<TradeRecordVO> processing();
}

