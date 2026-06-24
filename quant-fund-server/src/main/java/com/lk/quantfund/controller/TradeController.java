package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.trade.TradeRecordRequest;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.enums.TradeStatus;
import com.lk.quantfund.enums.TradeType;
import com.lk.quantfund.service.TradeRecordService;
import com.lk.quantfund.vo.trade.TradeRecordVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/trades")
public class TradeController {

    private final TradeRecordService tradeRecordService;

    public TradeController(TradeRecordService tradeRecordService) {
        this.tradeRecordService = tradeRecordService;
    }

    @PostMapping
    @RateLimit(key = "trade:create", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "create_trade", bizType = "TRADE_RECORD")
    public ApiResponse<TradeRecordVO> create(@Valid @RequestBody TradeRecordRequest request) {
        return ApiResponse.success(tradeRecordService.create(request));
    }

    @PostMapping("/buy")
    @RateLimit(key = "trade:buy", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "simulated_buy", bizType = "TRADE_RECORD")
    public ApiResponse<TradeRecordVO> buy(@Valid @RequestBody TradeRecordRequest request) {
        return ApiResponse.success(tradeRecordService.createAs(request, TradeType.BUY));
    }

    @PostMapping("/sell")
    @RateLimit(key = "trade:sell", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "simulated_sell", bizType = "TRADE_RECORD")
    public ApiResponse<TradeRecordVO> sell(@Valid @RequestBody TradeRecordRequest request) {
        return ApiResponse.success(tradeRecordService.createAs(request, TradeType.SELL));
    }

    @PostMapping("/regular-invest")
    @RateLimit(key = "trade:regular-invest", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "simulated_regular_invest", bizType = "TRADE_RECORD")
    public ApiResponse<TradeRecordVO> regularInvest(@Valid @RequestBody TradeRecordRequest request) {
        return ApiResponse.success(tradeRecordService.createAs(request, TradeType.REGULAR_INVEST));
    }

    @PostMapping("/convert-in")
    @RateLimit(key = "trade:convert-in", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "simulated_convert_in", bizType = "TRADE_RECORD")
    public ApiResponse<TradeRecordVO> convertIn(@Valid @RequestBody TradeRecordRequest request) {
        return ApiResponse.success(tradeRecordService.createAs(request, TradeType.CONVERT_IN));
    }

    @PostMapping("/convert-out")
    @RateLimit(key = "trade:convert-out", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "simulated_convert_out", bizType = "TRADE_RECORD")
    public ApiResponse<TradeRecordVO> convertOut(@Valid @RequestBody TradeRecordRequest request) {
        return ApiResponse.success(tradeRecordService.createAs(request, TradeType.CONVERT_OUT));
    }

    @GetMapping
    @RateLimit(key = "trade:list", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<TradeRecordVO>> list(@RequestParam(required = false) Long accountId,
                                                 @RequestParam(required = false) Long holdingId,
                                                 @RequestParam(required = false) TradeType tradeType,
                                                 @RequestParam(required = false) TradeStatus tradeStatus) {
        return ApiResponse.success(tradeRecordService.list(accountId, holdingId, tradeType, tradeStatus));
    }

    @GetMapping("/processing")
    @RateLimit(key = "trade:processing", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<TradeRecordVO>> processing() {
        return ApiResponse.success(tradeRecordService.processing());
    }

    @GetMapping("/{id}")
    @DataScope(resourceType = ResourceType.TRADE_RECORD, idParam = "id", operation = DataOperation.READ)
    @RateLimit(key = "trade:detail", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<TradeRecordVO> detail(@PathVariable Long id) {
        return ApiResponse.success(tradeRecordService.detail(id));
    }
}

