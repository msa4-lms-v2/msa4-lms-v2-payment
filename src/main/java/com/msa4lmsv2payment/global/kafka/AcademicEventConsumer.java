package com.msa4lmsv2payment.global.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.SemesterSnapshotRepository;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.StudentSnapshotRepository;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.WithdrawalSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AcademicEventConsumer {

    private static final String GROUP_ID = "msa4-team3-payment-academic-sync";

    private final StudentSnapshotRepository studentSnapshotRepository;
    private final SemesterSnapshotRepository semesterSnapshotRepository;
    private final WithdrawalSnapshotRepository withdrawalSnapshotRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "msa4-team3.academic.student-changed", groupId = GROUP_ID)
    public void onStudentSnapshotChanged(String message) throws Exception {
        JsonNode payload = objectMapper.readTree(message);
        studentSnapshotRepository.upsertIfNewer(
                payload.get("studentId").asLong(),
                payload.get("userId").asLong(),
                payload.get("displayName").asText(),
                payload.hasNonNull("departmentName") ? payload.get("departmentName").asText() : null,
                payload.get("sourceVersion").asLong(),
                LocalDateTime.now()
        );
        log.info("StudentSnapshotChanged 반영 완료 studentId={}", payload.get("studentId").asLong());
    }

    @KafkaListener(topics = "msa4-team3.academic.semester-created", groupId = GROUP_ID)
    public void onSemesterCreated(String message) throws Exception {
        JsonNode payload = objectMapper.readTree(message);
        semesterSnapshotRepository.upsertIfNewer(
                payload.get("semesterId").asLong(),
                payload.get("displayName").asText(),
                LocalDate.parse(payload.get("startDate").asText()),
                LocalDate.parse(payload.get("endDate").asText()),
                payload.get("sourceVersion").asLong(),
                LocalDateTime.now()
        );
        log.info("SemesterCreated 반영 완료 semesterId={}", payload.get("semesterId").asLong());
    }

    @KafkaListener(topics = "msa4-team3.academic.withdrawal-approved", groupId = GROUP_ID)
    public void onWithdrawalApproved(String message) throws Exception {
        JsonNode payload = objectMapper.readTree(message);
        withdrawalSnapshotRepository.upsertIfNewer(
                payload.get("withdrawalId").asLong(),
                payload.get("studentId").asLong(),
                LocalDate.parse(payload.get("effectiveDate").asText()),
                payload.get("sourceVersion").asLong(),
                LocalDateTime.now()
        );
        log.info("WithdrawalApproved 반영 완료 withdrawalId={}", payload.get("withdrawalId").asLong());
    }
}
