package com.lk.quantfund;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "quantfund.scheduler.enabled=false")
class QuantFundServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
