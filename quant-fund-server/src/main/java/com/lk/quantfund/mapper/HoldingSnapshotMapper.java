package com.lk.quantfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lk.quantfund.entity.HoldingSnapshot;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HoldingSnapshotMapper extends BaseMapper<HoldingSnapshot> {

    @Select("""
            SELECT *
            FROM holding_snapshot
            WHERE holding_id = #{holdingId}
              AND snapshot_date = #{snapshotDate}
            ORDER BY deleted ASC, update_time DESC, id DESC
            LIMIT 1
            """)
    HoldingSnapshot selectByHoldingAndDateIncludingDeleted(@Param("holdingId") Long holdingId,
                                                           @Param("snapshotDate") LocalDate snapshotDate);

    @Update("""
            UPDATE holding_snapshot
            SET deleted = 0,
                update_time = NOW(3)
            WHERE id = #{id}
            """)
    int restoreById(@Param("id") Long id);
}
