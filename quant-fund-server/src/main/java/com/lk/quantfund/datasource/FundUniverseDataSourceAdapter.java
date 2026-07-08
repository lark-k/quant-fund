package com.lk.quantfund.datasource;

import com.lk.quantfund.datasource.model.MarketFundDTO;
import java.util.List;

public interface FundUniverseDataSourceAdapter {

    String sourceName();

    int priority();

    boolean enabled();

    List<MarketFundDTO> listAllFunds();

    List<MarketFundDTO> listFundsByCategory(String category);
}
