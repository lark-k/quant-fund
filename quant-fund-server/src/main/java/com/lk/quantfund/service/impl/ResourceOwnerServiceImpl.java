package com.lk.quantfund.service.impl;

import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.service.ResourceOwnerLookup;
import com.lk.quantfund.service.ResourceOwnerService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ResourceOwnerServiceImpl implements ResourceOwnerService {

    private final Map<ResourceType, ResourceOwnerLookup> lookupMap = new EnumMap<>(ResourceType.class);

    public ResourceOwnerServiceImpl(List<ResourceOwnerLookup> lookups) {
        for (ResourceOwnerLookup lookup : lookups) {
            lookupMap.put(lookup.resourceType(), lookup);
        }
    }

    @Override
    public Optional<Long> findOwnerUserId(ResourceType resourceType, Long resourceId) {
        ResourceOwnerLookup lookup = lookupMap.get(resourceType);
        if (lookup == null) {
            return Optional.empty();
        }
        return lookup.findOwnerUserId(resourceId);
    }
}

