package com.lk.quantfund.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient fundDataWebClient(WebClient.Builder builder) {
        return builder
                .defaultHeader("User-Agent", "QuantFund/0.1.0")
                .build();
    }
}

