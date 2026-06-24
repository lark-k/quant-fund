package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.common.PageResponse;
import com.lk.quantfund.dto.system.DataSourceConfigRequest;
import com.lk.quantfund.entity.ApiCallLogEntity;
import com.lk.quantfund.entity.DataSourceConfig;
import com.lk.quantfund.entity.OperationLogEntity;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.ApiCallLogMapper;
import com.lk.quantfund.mapper.DataSourceConfigMapper;
import com.lk.quantfund.mapper.OperationLogMapper;
import com.lk.quantfund.service.SystemManagementService;
import com.lk.quantfund.vo.system.ApiCallLogVO;
import com.lk.quantfund.vo.system.DataSourceConfigVO;
import com.lk.quantfund.vo.system.OperationLogVO;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemManagementServiceImpl implements SystemManagementService {

    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final OperationLogMapper operationLogMapper;
    private final ApiCallLogMapper apiCallLogMapper;

    public SystemManagementServiceImpl(DataSourceConfigMapper dataSourceConfigMapper,
                                       OperationLogMapper operationLogMapper,
                                       ApiCallLogMapper apiCallLogMapper) {
        this.dataSourceConfigMapper = dataSourceConfigMapper;
        this.operationLogMapper = operationLogMapper;
        this.apiCallLogMapper = apiCallLogMapper;
    }

    @Override
    public List<DataSourceConfigVO> listDataSources() {
        Long userId = UserContext.getUserId();
        List<DataSourceConfig> configs = dataSourceConfigMapper.selectList(new LambdaQueryWrapper<DataSourceConfig>()
                .isNull(DataSourceConfig::getUserId)
                .or(wrapper -> wrapper.eq(DataSourceConfig::getUserId, userId))
                .orderByAsc(DataSourceConfig::getPriority)
                .orderByAsc(DataSourceConfig::getId));
        Map<String, DataSourceConfig> merged = new LinkedHashMap<>();
        for (DataSourceConfig config : configs) {
            String key = config.getSourceName();
            if (!StringUtils.hasText(key)) {
                key = String.valueOf(config.getId());
            }
            DataSourceConfig existing = merged.get(key);
            if (existing == null || config.getUserId() != null) {
                merged.put(key, config);
            }
        }
        return merged.values().stream().map(this::toDataSourceConfigVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSourceConfigVO saveDataSourceConfig(DataSourceConfigRequest request) {
        Long userId = UserContext.getUserId();
        DataSourceConfig existing = dataSourceConfigMapper.selectOne(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getUserId, userId)
                .eq(DataSourceConfig::getSourceName, request.sourceName().trim())
                .last("LIMIT 1"));
        DataSourceConfig config = existing == null ? new DataSourceConfig() : existing;
        applyRequest(config, request, userId);
        if (config.getId() == null) {
            dataSourceConfigMapper.insert(config);
        } else {
            dataSourceConfigMapper.updateById(config);
        }
        return toDataSourceConfigVO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSourceConfigVO updateDataSourceConfig(Long configId, DataSourceConfigRequest request) {
        Long userId = UserContext.getUserId();
        DataSourceConfig target = dataSourceConfigMapper.selectById(configId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "data source config not found");
        }
        if (target.getUserId() != null && !target.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (target.getUserId() == null) {
            DataSourceConfig override = dataSourceConfigMapper.selectOne(new LambdaQueryWrapper<DataSourceConfig>()
                    .eq(DataSourceConfig::getUserId, userId)
                    .eq(DataSourceConfig::getSourceName, target.getSourceName())
                    .last("LIMIT 1"));
            target = override == null ? new DataSourceConfig() : override;
        }
        applyRequest(target, request, userId);
        if (target.getId() == null) {
            dataSourceConfigMapper.insert(target);
        } else {
            dataSourceConfigMapper.updateById(target);
        }
        return toDataSourceConfigVO(target);
    }

    @Override
    public PageResponse<OperationLogVO> operationLogs(Long pageNo, Long pageSize, String module, Boolean success) {
        Long userId = UserContext.getUserId();
        Page<OperationLogEntity> page = operationLogMapper.selectPage(new Page<>(normalizePageNo(pageNo), normalizePageSize(pageSize)),
                new LambdaQueryWrapper<OperationLogEntity>()
                        .eq(OperationLogEntity::getUserId, userId)
                        .eq(StringUtils.hasText(module), OperationLogEntity::getModule, trim(module))
                        .eq(success != null, OperationLogEntity::getSuccess, success != null && success ? 1 : 0)
                        .orderByDesc(OperationLogEntity::getCreateTime));
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(this::toOperationLogVO).toList());
    }

    @Override
    public PageResponse<ApiCallLogVO> apiCallLogs(Long pageNo, Long pageSize, String provider, Boolean success) {
        Long userId = UserContext.getUserId();
        Page<ApiCallLogEntity> page = apiCallLogMapper.selectPage(new Page<>(normalizePageNo(pageNo), normalizePageSize(pageSize)),
                new LambdaQueryWrapper<ApiCallLogEntity>()
                        .and(wrapper -> wrapper.eq(ApiCallLogEntity::getUserId, userId).or().isNull(ApiCallLogEntity::getUserId))
                        .eq(StringUtils.hasText(provider), ApiCallLogEntity::getProvider, trim(provider))
                        .eq(success != null, ApiCallLogEntity::getSuccess, success != null && success ? 1 : 0)
                        .orderByDesc(ApiCallLogEntity::getCallTime));
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(this::toApiCallLogVO).toList());
    }

    private void applyRequest(DataSourceConfig config, DataSourceConfigRequest request, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        config.setUserId(userId);
        config.setSourceName(request.sourceName().trim());
        config.setBaseUrl(request.baseUrl().trim());
        config.setTimeoutMs(request.timeoutMs() == null ? 5000 : request.timeoutMs());
        config.setRefreshIntervalSeconds(request.refreshIntervalSeconds() == null ? 120 : request.refreshIntervalSeconds());
        config.setRateLimitPerMinute(request.rateLimitPerMinute() == null ? 60 : request.rateLimitPerMinute());
        config.setEnabled(request.enabled() == null || request.enabled() ? 1 : 0);
        config.setPriority(request.priority() == null ? 100 : request.priority());
        config.setConfigJson(StringUtils.hasText(request.configJson()) ? request.configJson().trim() : null);
        config.setUpdateTime(now);
        config.setDeleted(0);
        if (config.getCreateTime() == null) {
            config.setCreateTime(now);
        }
    }

    private DataSourceConfigVO toDataSourceConfigVO(DataSourceConfig config) {
        return new DataSourceConfigVO(
                config.getId(),
                config.getUserId(),
                config.getSourceName(),
                config.getBaseUrl(),
                config.getTimeoutMs(),
                config.getRefreshIntervalSeconds(),
                config.getRateLimitPerMinute(),
                config.getEnabled() != null && config.getEnabled() == 1,
                config.getPriority(),
                config.getConfigJson(),
                config.getUserId() != null,
                config.getUpdateTime()
        );
    }

    private OperationLogVO toOperationLogVO(OperationLogEntity log) {
        return new OperationLogVO(
                log.getId(),
                log.getUserId(),
                log.getModule(),
                log.getAction(),
                log.getBizType(),
                log.getRequestMethod(),
                log.getRequestUri(),
                log.getRequestParams(),
                log.getResponseResult(),
                log.getIp(),
                log.getUserAgent(),
                log.getSuccess() != null && log.getSuccess() == 1,
                log.getErrorMessage(),
                log.getCostTimeMs(),
                log.getCreateTime()
        );
    }

    private ApiCallLogVO toApiCallLogVO(ApiCallLogEntity log) {
        return new ApiCallLogVO(
                log.getId(),
                log.getUserId(),
                log.getProvider(),
                log.getApiName(),
                log.getRequestUrl(),
                log.getRequestMethod(),
                log.getSuccess() != null && log.getSuccess() == 1,
                log.getStatusCode(),
                log.getErrorMessage(),
                log.getCostTimeMs(),
                log.getFallbackUsed() != null && log.getFallbackUsed() == 1,
                log.getCallTime()
        );
    }

    private long normalizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
