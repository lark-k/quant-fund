package com.lk.quantfund.service;

import com.lk.quantfund.dto.holding.ClearHoldingRequest;
import com.lk.quantfund.dto.holding.CreateHoldingRequest;
import com.lk.quantfund.dto.holding.UpdateHoldingRequest;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import java.util.List;

public interface FundHoldingService {

    FundHoldingVO create(CreateHoldingRequest request);

    FundHoldingVO update(Long holdingId, UpdateHoldingRequest request);

    void delete(Long holdingId);

    FundHoldingVO clear(Long holdingId, ClearHoldingRequest request);

    FundHoldingVO detail(Long holdingId);

    List<FundHoldingVO> list(Long accountId, String fundCode);

    FundHoldingVO recalculate(Long holdingId);

    List<FundHoldingVO> syncOfficialNav();
}
