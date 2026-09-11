package com.lk.quantfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lk.quantfund.entity.QuantSignal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuantSignalMapper extends BaseMapper<QuantSignal> {

    // Rank only IDs before loading display fields. Historical engine payloads can
    // be hundreds of MB and must never be materialized by this read endpoint.
    @Select("""
            <script>
            SELECT s.id, s.account_id, s.holding_id, s.fund_code, s.fund_name,
                   s.action, s.action_text, s.suggest_amount, s.suggest_ratio,
                   s.risk_level, s.confidence, s.total_score, s.trend_score,
                   s.opportunity_score, s.risk_score, s.position_score, s.momentum_score,
                   s.reasons_json, s.risks_json, s.metrics_json, s.model_name,
                   s.model_version, s.deadline, s.signal_time, s.fallback_used
            FROM quant_signal s
            JOIN (
                SELECT id, ROW_NUMBER() OVER (
                    PARTITION BY holding_id ORDER BY signal_time DESC, id DESC
                ) AS row_rank
                FROM quant_signal
                WHERE deleted = 0 AND user_id = #{userId}
                <if test="accountId != null">AND account_id = #{accountId}</if>
                <if test="holdingId != null">AND holding_id = #{holdingId}</if>
                <if test="fundCode != null">AND fund_code = #{fundCode}</if>
                <if test="action != null">AND action = #{action}</if>
            ) latest ON latest.id = s.id AND latest.row_rank = 1
            ORDER BY s.signal_time DESC, s.id DESC
            </script>
            """)
    List<QuantSignal> selectLatestSignals(@Param("userId") Long userId,
                                          @Param("accountId") Long accountId,
                                          @Param("holdingId") Long holdingId,
                                          @Param("fundCode") String fundCode,
                                          @Param("action") String action);
}
