package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.domain.academicsnapshot.entity.SemesterSnapshot;
import com.msa4lmsv2payment.domain.academicsnapshot.entity.StudentSnapshot;
import com.msa4lmsv2payment.domain.academicsnapshot.entity.WithdrawalSnapshot;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.SemesterSnapshotRepository;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.StudentSnapshotRepository;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.WithdrawalSnapshotRepository;
import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.error.NotStudentAccountException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcademicSnapshotClient implements AcademicClient {

    private final StudentSnapshotRepository studentSnapshotRepository;
    private final SemesterSnapshotRepository semesterSnapshotRepository;
    private final WithdrawalSnapshotRepository withdrawalSnapshotRepository;
    private final AcademicResyncClient academicResyncClient;

    @Override
    public AcademicStudentResponse findStudentByUserId(Long userId) {
        Optional<StudentSnapshot> snapshot = studentSnapshotRepository.findByUserId(userId);
        if (snapshot.isPresent()) {
            return new AcademicStudentResponse(snapshot.get().getStudentId(), snapshot.get().getUserId());
        }
        StudentSnapshotSyncResponse backfilled = academicResyncClient.fetchStudentByUserId(userId)
                .orElseThrow(() -> new NotStudentAccountException("학생 계정이 아니거나 존재하지 않는 사용자입니다."));
        upsertStudentSnapshot(backfilled);
        return new AcademicStudentResponse(backfilled.studentId(), backfilled.userId());
    }

    @Override
    public AcademicStudentExistsResponse findStudent(Long studentId) {
        Optional<StudentSnapshot> snapshot = studentSnapshotRepository.findById(studentId);
        if (snapshot.isPresent()) {
            return new AcademicStudentExistsResponse(snapshot.get().getStudentId());
        }
        StudentSnapshotSyncResponse backfilled = academicResyncClient.fetchStudent(studentId)
                .orElseThrow(() -> new AcademicResourceNotFoundException("존재하지 않는 학번입니다: " + studentId));
        upsertStudentSnapshot(backfilled);
        return new AcademicStudentExistsResponse(backfilled.studentId());
    }

    @Override
    public AcademicSemesterResponse findSemester(Long semesterId) {
        Optional<SemesterSnapshot> snapshot = semesterSnapshotRepository.findById(semesterId);
        if (snapshot.isPresent()) {
            return new AcademicSemesterResponse(snapshot.get().getSemesterId(), snapshot.get().getStartDate(),
                    snapshot.get().getEndDate());
        }
        SemesterSnapshotSyncResponse backfilled = academicResyncClient.fetchSemester(semesterId)
                .orElseThrow(() -> new AcademicResourceNotFoundException("존재하지 않는 학기입니다: " + semesterId));
        semesterSnapshotRepository.upsertIfNewer(backfilled.semesterId(), backfilled.displayName(),
                backfilled.startDate(), backfilled.endDate(), backfilled.sourceVersion(), LocalDateTime.now());
        return new AcademicSemesterResponse(backfilled.semesterId(), backfilled.startDate(), backfilled.endDate());
    }

    @Override
    public AcademicWithdrawalResponse findWithdrawal(Long withdrawalId) {
        Optional<WithdrawalSnapshot> snapshot = withdrawalSnapshotRepository.findById(withdrawalId);
        if (snapshot.isPresent()) {
            return new AcademicWithdrawalResponse(snapshot.get().getWithdrawalId(), snapshot.get().getStudentId(),
                    snapshot.get().getEffectiveDate());
        }
        WithdrawalSnapshotSyncResponse backfilled = academicResyncClient.fetchWithdrawal(withdrawalId)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "자퇴 신청을 찾을 수 없습니다: withdrawalId=" + withdrawalId));
        withdrawalSnapshotRepository.upsertIfNewer(backfilled.withdrawalId(), backfilled.studentId(),
                backfilled.effectiveDate(), backfilled.sourceVersion(), LocalDateTime.now());
        return new AcademicWithdrawalResponse(backfilled.withdrawalId(), backfilled.studentId(), backfilled.effectiveDate());
    }

    private void upsertStudentSnapshot(StudentSnapshotSyncResponse backfilled) {
        studentSnapshotRepository.upsertIfNewer(backfilled.studentId(), backfilled.userId(), backfilled.displayName(),
                backfilled.departmentName(), backfilled.sourceVersion(), LocalDateTime.now());
    }
}
