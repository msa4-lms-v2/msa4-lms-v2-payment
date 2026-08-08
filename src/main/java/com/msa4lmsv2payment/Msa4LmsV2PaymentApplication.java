package com.msa4lmsv2payment;

import com.msa4lmsv2payment.global.config.TossPaymentsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class Msa4LmsV2PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(Msa4LmsV2PaymentApplication.class, args);
    }

}
