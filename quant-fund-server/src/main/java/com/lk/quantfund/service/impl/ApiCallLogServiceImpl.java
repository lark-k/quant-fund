package com.lk.quantfund.service.impl;

import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.entity.ApiCallLogEntity;
import com.lk.quantfund.mapper.ApiCallLogMapper;
import com.lk.quantfund.service.ApiCallLogService;
import com.lk.quantfund.util.SensitiveDataMaskUtil;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ApiCallLogServiceImpl implements ApiCallLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiCallLogServiceImpl.class);

    private final ApiCallLogMapper apiCallLogMapper;

    public ApiCallLogServiceImpl(ApiCallLogMapper apiCallLogMapper) {
        this.apiCallLogMapper = apiCallLogMapper;
    }

    @Override
    public void record(String provider,
                       String apiName,
                       String requestUrl,
                       String requestMethod,
                       boolean success,
                       Integer statusCode,
                       String errorMessage,
                       long costTimeMs,
                       boolean fallbackUsed) {
        try {
            LocalDateTime now = LocalDateTime.now();
            ApiCallLogEntity entity = new ApiCallLogEntity();
            entity.setUserId(UserContext.isLogin() ? UserContext.getUserId() : null);
            entity.setProvider(provider);
            entity.setApiName(apiName);
            entity.setRequestUrl(SensitiveDataMaskUtil.maskMessage(requestUrl));
            entity.setRequestMethod(requestMethod);
            entity.setSuccess(success ? 1 : 0);
            entity.setStatusCode(statusCode);
            entity.setErrorMessage(SensitiveDataMaskUtil.maskMessage(errorMessage));
            entity.setCostTimeMs(costTimeMs);
            entity.setFallbackUsed(fallbackUsed ? 1 : 0);
            entity.setCallTime(now);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDeleted(0);
            apiCallLogMapper.insert(entity);
        } catch (Exception exception) {
            log.warn("API call log save failed: {}", exception.getMessage());
        }
    }
}

