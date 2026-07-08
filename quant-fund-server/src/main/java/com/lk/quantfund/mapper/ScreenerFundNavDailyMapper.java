package com.lk.quantfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lk.quantfund.entity.ScreenerFundNavDaily;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScreenerFundNavDailyMapper extends BaseMapper<ScreenerFundNavDaily> {

    @Insert("""
            <script>
            INSERT INTO screener_fund_nav_daily (
              fund_code, nav_date, unit_nav, accumulated_nav, daily_growth_rate,
              source_name, create_time, update_time, deleted
            )
            VALUES
            <foreach collection="entities" item="entity" separator=",">
              (
                #{entity.fundCode}, #{entity.navDate}, #{entity.unitNav}, #{entity.accumulatedNav},
                #{entity.dailyGrowthRate}, #{entity.sourceName}, #{entity.createTime}, #{entity.updateTime},
                #{entity.deleted}
              )
            </foreach>
            ON DUPLICATE KEY UPDATE
              unit_nav = VALUES(unit_nav),
              accumulated_nav = VALUES(accumulated_nav),
              daily_growth_rate = VALUES(daily_growth_rate),
              source_name = VALUES(source_name),
              update_time = VALUES(update_time),
              deleted = 0
            </script>
            """)
    int upsertBatch(@Param("entities") List<ScreenerFundNavDaily> entities);
}
