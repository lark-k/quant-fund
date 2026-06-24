package com.lk.quantfund.datasource;

import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FundDataSourceAdapter {

    String sourceName();

    int priority();

    boolean enabled();

    List<FundSearchResultDTO> searchFunds(String keyword);

    Optional<FundBasicInfoDTO> getBasicInfo(String fundCode);

    List<FundNavPointDTO> getHistoricalNav(String fundCode, LocalDate startDate, LocalDate endDate);

    Optional<FundEstimateDTO> getIntradayEstimate(String fundCode);

    List<FundStockHoldingDTO> getHeavyStocks(String fundCode);

    List<FundThemeDTO> getRelatedThemes(String fundCode);

    Optional<FundPeerRankDTO> getPeerRank(String fundCode);
}

