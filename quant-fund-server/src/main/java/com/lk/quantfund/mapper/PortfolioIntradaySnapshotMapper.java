package com.lk.quantfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lk.quantfund.entity.PortfolioIntradaySnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PortfolioIntradaySnapshotMapper extends BaseMapper<PortfolioIntradaySnapshot> {

    @Insert("""
            INSERT INTO portfolio_intraday_snapshot (
              user_id, snapshot_date, snapshot_time, total_asset, daily_profit, daily_profit_rate,
              source_name, create_time, update_time, deleted
            )
            VALUES (
              #{snapshot.userId}, #{snapshot.snapshotDate}, #{snapshot.snapshotTime}, #{snapshot.totalAsset},
              #{snapshot.dailyProfit}, #{snapshot.dailyProfitRate}, #{snapshot.sourceName},
              #{snapshot.createTime}, #{snapshot.updateTime}, #{snapshot.deleted}
            )
            ON DUPLICATE KEY UPDATE
              total_asset = VALUES(total_asset),
              daily_profit = VALUES(daily_profit),
              daily_profit_rate = VALUES(daily_profit_rate),
              source_name = VALUES(source_name),
              update_time = VALUES(update_time),
              deleted = 0
            """)
    int upsertByUserAndSnapshotTime(@Param("snapshot") PortfolioIntradaySnapshot snapshot);
}
