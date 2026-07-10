package com.lk.quantfund.scheduler;

import com.lk.quantfund.entity.SchedulerTaskLog;
import com.lk.quantfund.enums.SchedulerTaskStatus;
import com.lk.quantfund.mapper.SchedulerTaskLogMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SchedulerTaskLogService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerTaskLogService.class);

    private final SchedulerTaskLogMapper schedulerTaskLogMapper;

    public SchedulerTaskLogService(SchedulerTaskLogMapper schedulerTaskLogMapper) {
        this.schedulerTaskLogMapper = schedulerTaskLogMapper;
    }

    public void save(String taskName, String triggerType, LocalDateTime startTime, SchedulerTaskResult result, boolean skipped) {
        try {
            LocalDateTime now = LocalDateTime.now();
            SchedulerTaskStatus status = status(result, skipped);
            SchedulerTaskLog entity = new SchedulerTaskLog();
            entity.setTaskName(taskName);
            entity.setTriggerType(triggerType);
            entity.setStatus(status.name());
            entity.setStartTime(startTime);
            entity.setEndTime(now);
            entity.setCostTimeMs(Duration.between(startTime, now).toMillis());
            entity.setSuccessCount(result.getSuccessCount());
            entity.setFailureCount(result.getFailureCount());
            entity.setErrorMessage(result.logSummary());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDeleted(0);
            schedulerTaskLogMapper.insert(entity);
        } catch (RuntimeException exception) {
            log.warn("Scheduler task log save failed for {}: {}", taskName, exception.getMessage());
        }
    }

    private SchedulerTaskStatus status(SchedulerTaskResult result, boolean skipped) {
        if (skipped) {
            return SchedulerTaskStatus.SKIPPED;
        }
        if (result.getFailureCount() == 0) {
            return SchedulerTaskStatus.SUCCESS;
        }
        if (result.getSuccessCount() > 0) {
            return SchedulerTaskStatus.PARTIAL_SUCCESS;
        }
        return SchedulerTaskStatus.FAILED;
    }
}

