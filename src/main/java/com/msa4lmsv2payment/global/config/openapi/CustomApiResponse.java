package com.msa4lmsv2payment.global.config.openapi;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomApiResponse {
    CustomResponseCode[] value();
}
