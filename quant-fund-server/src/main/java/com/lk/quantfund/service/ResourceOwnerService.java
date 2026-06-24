package com.lk.quantfund.service;

import com.lk.quantfund.enums.ResourceType;
import java.util.Optional;

public interface ResourceOwnerService {

    Optional<Long> findOwnerUserId(ResourceType resourceType, Long resourceId);
}

