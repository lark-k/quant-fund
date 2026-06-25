package com.lk.quantfund.service;

import com.lk.quantfund.vo.market.MarketIndexVO;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import com.lk.quantfund.vo.market.MarketIndexIntradayPointVO;
import java.time.LocalDate;
import java.util.List;

public interface MarketDataService {

    List<MarketIndexVO> marketReadings();

    List<MarketIndexDailyVO> historicalIndex(String indexCode, LocalDate startDate, LocalDate endDate);

    List<MarketIndexIntradayPointVO> intradayIndex(String indexCode);
}
