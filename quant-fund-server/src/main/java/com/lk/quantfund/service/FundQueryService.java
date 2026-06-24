package com.lk.quantfund.service;

import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import java.time.LocalDate;
import java.util.List;

public interface FundQueryService {

    List<FundSearchResultDTO> search(String keyword);

    List<FundSearchResultDTO> search(String keyword, String mode);

    FundBasicInfoDTO getBasicInfo(String fundCode);

    List<FundNavPointDTO> getHistoricalNav(String fundCode, LocalDate startDate, LocalDate endDate);

    FundEstimateDTO getIntradayEstimate(String fundCode, boolean manualRefresh);

    List<FundStockHoldingDTO> getHeavyStocks(String fundCode);

    List<FundThemeDTO> getRelatedThemes(String fundCode);

    FundPeerRankDTO getPeerRank(String fundCode);
}
