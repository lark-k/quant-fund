package com.lk.quantfund.scheduler;

import java.util.ArrayList;
import java.util.List;

public class SchedulerTaskResult {

    private int successCount;
    private int failureCount;
    private int skippedCount;
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

    public void skipped() {
        skippedCount++;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public String errorSummary() {
        return String.join("; ", errors);
    }

    public String logSummary() {
        String errorText = errorSummary();
        String skippedText = skippedCount > 0 ? "skipped=" + skippedCount : "";
        if (errorText.isBlank()) {
            return skippedText;
        }
        if (skippedText.isBlank()) {
            return errorText;
        }
        return errorText + "; " + skippedText;
    }

    public String getErrorSummary() {
        return errorSummary();
    }
}
