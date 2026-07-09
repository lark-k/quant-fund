package com.lk.quantfund.scheduler;

import java.util.ArrayList;
import java.util.List;

public class SchedulerTaskResult {

    private int successCount;
    private int failureCount;
    private final List<String> errors = new ArrayList<>();

    public void success() {
        successCount++;
    }

    public void failure(String message) {
        failureCount++;
        if (message != null && errors.size() < 10) {
            errors.add(message);
        }
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public String errorSummary() {
        return String.join("; ", errors);
    }

    public String getErrorSummary() {
        return errorSummary();
    }
}
