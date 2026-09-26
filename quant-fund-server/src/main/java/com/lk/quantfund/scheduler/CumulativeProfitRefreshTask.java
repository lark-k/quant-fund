package com.lk.quantfund.scheduler;

import com.lk.quantfund.service.CumulativeProfitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reads only persisted official NAVs; never requests or consumes intraday estimates. */
@Component
public class CumulativeProfitRefreshTask {
    private static final Logger log = LoggerFactory.getLogger(CumulativeProfitRefreshTask.class);
    private final JdbcTemplate jdbc;
    private final CumulativeProfitService service;

    public CumulativeProfitRefreshTask(JdbcTemplate jdbc, CumulativeProfitService service) {
        this.jdbc = jdbc; this.service = service;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void refresh() {
        for (Long userId : jdbc.queryForList("SELECT user_id FROM cumulative_profit_user", Long.class)) {
            try { service.overview(userId); }
            catch (RuntimeException error) { log.warn("Cumulative profit refresh failed for user {}", userId, error); }
        }
    }
}
