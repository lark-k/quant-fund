package com.lk.quantfund;

import com.lk.quantfund.config.QuantFundProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(QuantFundProperties.class)
@EnableScheduling
public class QuantFundServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantFundServerApplication.class, args);
    }
}
