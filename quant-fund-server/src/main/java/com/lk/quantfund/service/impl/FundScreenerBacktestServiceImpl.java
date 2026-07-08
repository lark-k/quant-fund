package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import com.lk.quantfund.entity.ScreenerQualityScore;
import com.lk.quantfund.mapper.ScreenerFundNavDailyMapper;
import com.lk.quantfund.mapper.ScreenerQualityScoreMapper;
import com.lk.quantfund.service.FundScreenerBacktestService;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FundScreenerBacktestServiceImpl implements FundScreenerBacktestService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final ScreenerQualityScoreMapper screenerQualityScoreMapper;
    private final ScreenerFundNavDailyMapper screenerFundNavDailyMapper;

    public FundScreenerBacktestServiceImpl(ScreenerQualityScoreMapper screenerQualityScoreMapper,
                                           ScreenerFundNavDailyMapper screenerFundNavDailyMapper) {
        this.screenerQualityScoreMapper = screenerQualityScoreMapper;
        this.screenerFundNavDailyMapper = screenerFundNavDailyMapper;
    }

    @Override
    public FundScreenerTaskResultVO backtest() {
        long started = System.currentTimeMillis();
        int success = 0;
        int failure = 0;
        List<String> errors = new ArrayList<>();
        List<BigDecimal> forwardReturns = new ArrayList<>();
        List<ScreenerQualityScore> scores = screenerQualityScoreMapper.selectList(new LambdaQueryWrapper<ScreenerQualityScore>()
                .orderByDesc(ScreenerQualityScore::getScoreDate)
                .orderByDesc(ScreenerQualityScore::getQualityScore)
                .last("LIMIT 200"));
        for (ScreenerQualityScore score : scores) {
            try {
                forwardReturns.add(forwardReturn(score));
                success++;
            } catch (RuntimeException exception) {
                failure++;
                errors.add(score.getFundCode() + ": " + exception.getMessage());
            }
        }
        BigDecimal averageForwardReturn = average(forwardReturns);
        return new FundScreenerTaskResultVO(
                "SCREENER_BACKTEST",
                failure == 0 ? "SUCCESS" : success > 0 ? "PARTIAL_SUCCESS" : "FAILED",
                success,
                failure,
                0,
                System.currentTimeMillis() - started,
                errors,
                "\u5df2\u8bc4\u4f30" + success + "\u53ea\u57fa\u91d1\uff0c\u5e73\u5747\u672a\u6765\u6536\u76ca "
                        + averageForwardReturn.setScale(4, RoundingMode.HALF_UP) + "%",
                LocalDateTime.now()
        );
    }

    private BigDecimal forwardReturn(ScreenerQualityScore score) {
        if (score.getScoreDate() == null) {
            throw new IllegalStateException("score date not found");
        }
        List<ScreenerFundNavDaily> navPoints = screenerFundNavDailyMapper.selectList(new LambdaQueryWrapper<ScreenerFundNavDaily>()
                .eq(ScreenerFundNavDaily::getFundCode, score.getFundCode())
                .orderByAsc(ScreenerFundNavDaily::getNavDate));
        ScreenerFundNavDaily base = navPoints.stream()
                .filter(point -> point.getNavDate() != null && !point.getNavDate().isAfter(score.getScoreDate()))
                .filter(point -> point.getUnitNav() != null && point.getUnitNav().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(ScreenerFundNavDaily::getNavDate))
                .orElseThrow(() -> new IllegalStateException("base nav not found"));
        ScreenerFundNavDaily future = navPoints.stream()
                .filter(point -> point.getNavDate() != null && point.getNavDate().isAfter(score.getScoreDate()))
                .filter(point -> point.getUnitNav() != null && point.getUnitNav().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(ScreenerFundNavDaily::getNavDate))
                .orElseThrow(() -> new IllegalStateException("future nav not found"));
        return future.getUnitNav().subtract(base.getUnitNav())
                .multiply(HUNDRED)
                .divide(base.getUnitNav(), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }
}
