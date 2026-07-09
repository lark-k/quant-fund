package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.DataScope;
import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.trade.ConvertPairTradeRequest;
import com.lk.quantfund.dto.trade.TradeRecordRequest;
import com.lk.quantfund.enums.DataOperation;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.enums.TradeStatus;
import com.lk.quantfund.enums.TradeType;
import com.lk.quantfund.scheduler.SchedulerTaskResult;
import com.lk.quantfund.service.TradeRecordService;
import com.lk.quantfund.vo.trade.TradeRecordVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PostMapping("/regular-invest/compensate-due")
    @RateLimit(key = "trade:regular-invest:compensate-due", windowSeconds = 60, maxRequests = 10)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "trade", action = "compensate_due_regular_invest", bizType = "TRADE_RECORD")
    public ApiResponse<SchedulerTaskResult> compensateDueRegularInvest() {
        return ApiResponse.success(tradeRecordService.compensateDueRegularInvestTrades());
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

    @PostMapping("/convert-pair")
    @RateLimit(key = "trade:convert-pair", windowSeconds = 60, maxRequests = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "simulated_convert_pair", bizType = "TRADE_RECORD")
    public ApiResponse<List<TradeRecordVO>> convertPair(@Valid @RequestBody ConvertPairTradeRequest request) {
        return ApiResponse.success(tradeRecordService.createConvertPair(request));
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

    @PostMapping("/settle-due")
    @RateLimit(key = "trade:settle-due", windowSeconds = 60, maxRequests = 20)
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "trade", action = "settle_due_processing_trades", bizType = "TRADE_RECORD")
    public ApiResponse<List<TradeRecordVO>> settleDue() {
        return ApiResponse.success(tradeRecordService.settleDueProcessingTrades());
    }

    @GetMapping("/{id}")
    @DataScope(resourceType = ResourceType.TRADE_RECORD, idParam = "id", operation = DataOperation.READ)
    @RateLimit(key = "trade:detail", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<TradeRecordVO> detail(@PathVariable Long id) {
        return ApiResponse.success(tradeRecordService.detail(id));
    }

    @DeleteMapping("/{id}")
    @DataScope(resourceType = ResourceType.TRADE_RECORD, idParam = "id", operation = DataOperation.DELETE)
    @RateLimit(key = "trade:delete", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "trade", action = "delete_processing_trade", bizType = "TRADE_RECORD")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tradeRecordService.deleteProcessing(id);
        return ApiResponse.success();
    }
}
