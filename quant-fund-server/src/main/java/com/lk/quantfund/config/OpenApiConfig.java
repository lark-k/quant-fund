package com.lk.quantfund.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quantFundOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuantFund API")
                        .description("QuantFund - AI Fund Quant Dashboard backend API")
                        .version("0.1.0")
                        .contact(new Contact().name("QuantFund"))
                        .license(new License().name("Private")));
    }
}

