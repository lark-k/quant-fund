package com.lk.quantfund.service;

import com.lk.quantfund.vo.dashboard.DashboardOverviewVO;
import com.lk.quantfund.vo.dashboard.MarketSessionStatusVO;

public interface DashboardService {

    DashboardOverviewVO overview();

    MarketSessionStatusVO marketStatus();
}
