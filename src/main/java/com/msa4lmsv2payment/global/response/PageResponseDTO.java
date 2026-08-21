package com.msa4lmsv2payment.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "페이지네이션 응답 포맷")
public record PageResponseDTO<T>(
        @Schema(description = "현재 페이지의 항목 목록") List<T> items,
        @Schema(description = "전체 항목 수") long totalCount,
        @Schema(description = "현재 페이지(1부터 시작)") int page,
        @Schema(description = "페이지 크기(최대 100)") int size,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext
) {
}
