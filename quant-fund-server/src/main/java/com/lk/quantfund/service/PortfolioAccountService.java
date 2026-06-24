package com.lk.quantfund.service;

import com.lk.quantfund.dto.portfolio.CreatePortfolioAccountRequest;
import com.lk.quantfund.dto.portfolio.UpdatePortfolioAccountRequest;
import com.lk.quantfund.vo.holding.FundHoldingVO;
import com.lk.quantfund.vo.portfolio.PortfolioAccountVO;
import com.lk.quantfund.vo.portfolio.PortfolioSummaryVO;
import java.util.List;

public interface PortfolioAccountService {

    PortfolioAccountVO create(CreatePortfolioAccountRequest request);

    PortfolioAccountVO update(Long accountId, UpdatePortfolioAccountRequest request);

    PortfolioAccountVO detail(Long accountId);

    List<PortfolioAccountVO> list();

    PortfolioSummaryVO summary();

    List<FundHoldingVO> holdings(Long accountId);

    PortfolioAccountVO recalculate(Long accountId);

    void recalculateOwnedAccount(Long userId, Long accountId);
}

