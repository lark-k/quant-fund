package com.lk.quantfund.service;

import com.lk.quantfund.vo.market.MarketIndexVO;
import java.util.List;

public interface MarketDataService {

    List<MarketIndexVO> marketReadings();
}

