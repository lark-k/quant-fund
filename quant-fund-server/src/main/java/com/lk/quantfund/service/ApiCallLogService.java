package com.lk.quantfund.service;

public interface ApiCallLogService {

    void record(String provider,
                String apiName,
                String requestUrl,
                String requestMethod,
                boolean success,
                Integer statusCode,
                String errorMessage,
                long costTimeMs,
                boolean fallbackUsed);
}

