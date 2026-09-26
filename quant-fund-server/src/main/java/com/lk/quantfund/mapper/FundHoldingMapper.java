package com.lk.quantfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lk.quantfund.entity.FundHolding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FundHoldingMapper extends BaseMapper<FundHolding> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM fund_holding WHERE user_id=#{userId} AND id=#{holdingId}")
    FundHolding selectOwnedIncludingDeleted(@org.apache.ibatis.annotations.Param("userId") Long userId,
                                           @org.apache.ibatis.annotations.Param("holdingId") Long holdingId);
}
