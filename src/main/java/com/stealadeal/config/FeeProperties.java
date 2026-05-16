package com.stealadeal.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fees")
public record FeeProperties(BigDecimal transactionRate) {

    public FeeProperties {
        if (transactionRate == null) {
            transactionRate = new BigDecimal("0.0075");
        }
    }
}
