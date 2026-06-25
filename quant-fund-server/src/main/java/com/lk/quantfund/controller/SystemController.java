package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.common.PageResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.system.DataSourceConfigRequest;
import com.lk.quantfund.service.SystemManagementService;
import com.lk.quantfund.vo.system.ApiCallLogVO;
import com.lk.quantfund.vo.system.AiRuntimeConfigVO;
import com.lk.quantfund.vo.system.DataSourceConfigVO;
import com.lk.quantfund.vo.system.DataSourceHealthVO;
import com.lk.quantfund.vo.system.OperationLogVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequireLogin
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/system")
public class SystemController {

    private final SystemManagementService systemManagementService;

    public SystemController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @GetMapping("/data-sources")
    @RateLimit(key = "system:data-source:list", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<DataSourceConfigVO>> dataSources() {
        return ApiResponse.success(systemManagementService.listDataSources());
    }

    @GetMapping("/data-source-health")
    @RateLimit(key = "system:data-source-health", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<List<DataSourceHealthVO>> dataSourceHealth() {
        return ApiResponse.success(systemManagementService.listDataSourceHealth());
    }

    @GetMapping("/ai-runtime-config")
    @RateLimit(key = "system:ai-runtime-config", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<AiRuntimeConfigVO> aiRuntimeConfig() {
        return ApiResponse.success(systemManagementService.aiRuntimeConfig());
    }

    @PostMapping("/data-sources")
    @RateLimit(key = "system:data-source:save", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "system", action = "save_data_source", bizType = "DATA_SOURCE_CONFIG")
    public ApiResponse<DataSourceConfigVO> saveDataSource(@Valid @RequestBody DataSourceConfigRequest request) {
        return ApiResponse.success(systemManagementService.saveDataSourceConfig(request));
    }

    @PutMapping("/data-sources/{id}")
    @RateLimit(key = "system:data-source:update", windowSeconds = 60, maxRequests = 30)
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "system", action = "update_data_source", bizType = "DATA_SOURCE_CONFIG")
    public ApiResponse<DataSourceConfigVO> updateDataSource(@PathVariable Long id,
                                                           @Valid @RequestBody DataSourceConfigRequest request) {
        return ApiResponse.success(systemManagementService.updateDataSourceConfig(id, request));
    }

    @GetMapping("/operation-logs")
    @RateLimit(key = "system:operation-log:list", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<PageResponse<OperationLogVO>> operationLogs(@RequestParam(required = false) Long pageNo,
                                                                   @RequestParam(required = false) Long pageSize,
                                                                   @RequestParam(required = false) String module,
                                                                   @RequestParam(required = false) Boolean success) {
        return ApiResponse.success(systemManagementService.operationLogs(pageNo, pageSize, module, success));
    }

    @GetMapping("/api-call-logs")
    @RateLimit(key = "system:api-call-log:list", windowSeconds = 60, maxRequests = 120)
    public ApiResponse<PageResponse<ApiCallLogVO>> apiCallLogs(@RequestParam(required = false) Long pageNo,
                                                               @RequestParam(required = false) Long pageSize,
                                                               @RequestParam(required = false) String provider,
                                                               @RequestParam(required = false) Boolean success) {
        return ApiResponse.success(systemManagementService.apiCallLogs(pageNo, pageSize, provider, success));
    }
}
