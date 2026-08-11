package com.msa4lmsv2payment;

import com.msa4lmsv2payment.global.config.TossPaymentsProperties;
import com.msa4lmsv2payment.global.config.WithdrawalRefundRateProperties;
import com.msa4lmsv2payment.global.security.GatewayContextProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
        TossPaymentsProperties.class,
        WithdrawalRefundRateProperties.class,
        GatewayContextProperties.class
})
@EnableScheduling
public class Msa4LmsV2PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(Msa4LmsV2PaymentApplication.class, args);
    }

}
