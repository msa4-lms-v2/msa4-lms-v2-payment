package com.msa4lmsv2payment.global.client;

public record InternalApiResponse<T>(boolean success, T data) {
}
