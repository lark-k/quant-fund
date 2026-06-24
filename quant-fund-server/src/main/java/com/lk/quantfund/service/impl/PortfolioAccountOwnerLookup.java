package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PortfolioAccountOwnerLookup implements ResourceOwnerLookup {

    private final PortfolioAccountMapper portfolioAccountMapper;

    public PortfolioAccountOwnerLookup(PortfolioAccountMapper portfolioAccountMapper) {
        this.portfolioAccountMapper = portfolioAccountMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.PORTFOLIO_ACCOUNT;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        PortfolioAccount account = portfolioAccountMapper.selectById(resourceId);
        return account == null ? Optional.empty() : Optional.ofNullable(account.getUserId());
    }
}

