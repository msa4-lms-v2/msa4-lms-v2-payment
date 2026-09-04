package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.domain.academicsnapshot.entity.SemesterSnapshot;
import com.msa4lmsv2payment.domain.academicsnapshot.entity.StudentSnapshot;
import com.msa4lmsv2payment.domain.academicsnapshot.entity.WithdrawalSnapshot;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.SemesterSnapshotRepository;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.StudentSnapshotRepository;
import com.msa4lmsv2payment.domain.academicsnapshot.repository.WithdrawalSnapshotRepository;
import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.error.NotStudentAccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcademicSnapshotClient implements AcademicClient {

    private final StudentSnapshotRepository studentSnapshotRepository;
    private final SemesterSnapshotRepository semesterSnapshotRepository;
    private final WithdrawalSnapshotRepository withdrawalSnapshotRepository;

    @Override
    public AcademicStudentResponse findStudentByUserId(Long userId) {
        StudentSnapshot snapshot = studentSnapshotRepository.findByUserId(userId)
                .orElseThrow(() -> new NotStudentAccountException("학생 계정이 아니거나 존재하지 않는 사용자입니다."));
        return new AcademicStudentResponse(snapshot.getStudentId(), snapshot.getUserId());
    }

    @Override
    public AcademicStudentExistsResponse findStudent(Long studentId) {
        StudentSnapshot snapshot = studentSnapshotRepository.findById(studentId)
                .orElseThrow(() -> new AcademicResourceNotFoundException("존재하지 않는 학번입니다: " + studentId));
        return new AcademicStudentExistsResponse(snapshot.getStudentId());
    }

    @Override
    public AcademicSemesterResponse findSemester(Long semesterId) {
        SemesterSnapshot snapshot = semesterSnapshotRepository.findById(semesterId)
                .orElseThrow(() -> new AcademicResourceNotFoundException("존재하지 않는 학기입니다: " + semesterId));
        return new AcademicSemesterResponse(snapshot.getSemesterId(), snapshot.getStartDate(), snapshot.getEndDate());
    }

    @Override
    public AcademicWithdrawalResponse findWithdrawal(Long withdrawalId) {
        WithdrawalSnapshot snapshot = withdrawalSnapshotRepository.findById(withdrawalId)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "자퇴 신청을 찾을 수 없습니다: withdrawalId=" + withdrawalId));
        return new AcademicWithdrawalResponse(snapshot.getWithdrawalId(), snapshot.getStudentId(), snapshot.getEffectiveDate());
    }
}
