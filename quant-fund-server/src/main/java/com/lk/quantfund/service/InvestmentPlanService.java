package com.lk.quantfund.service;

import com.lk.quantfund.dto.trade.InvestmentPlanRequest;
import com.lk.quantfund.vo.trade.InvestmentPlanVO;
import java.util.List;

public interface InvestmentPlanService {

    InvestmentPlanVO create(InvestmentPlanRequest request);

    InvestmentPlanVO update(Long id, InvestmentPlanRequest request);

    List<InvestmentPlanVO> list(Long accountId);

    InvestmentPlanVO updateStatus(Long id, String status);

    void delete(Long id);
}
