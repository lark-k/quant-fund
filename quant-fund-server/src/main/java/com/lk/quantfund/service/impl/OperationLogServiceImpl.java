package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.OperationLogEntity;
import com.lk.quantfund.mapper.OperationLogMapper;
import com.lk.quantfund.service.OperationLogService;
import org.springframework.stereotype.Service;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void save(OperationLogEntity operationLog) {
        operationLogMapper.insert(operationLog);
    }
}

