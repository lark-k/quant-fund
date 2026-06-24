package com.lk.quantfund.service;

import com.lk.quantfund.enums.ResourceType;
import java.util.Optional;

public interface ResourceOwnerLookup {

    ResourceType resourceType();

    Optional<Long> findOwnerUserId(Long resourceId);
}

