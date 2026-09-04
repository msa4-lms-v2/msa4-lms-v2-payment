package com.msa4lmsv2payment.domain.academicsnapshot.repository;

import com.msa4lmsv2payment.domain.academicsnapshot.entity.WithdrawalSnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawalSnapshotRepository extends JpaRepository<WithdrawalSnapshot, Long> {

    @Modifying
    @Query(value = "INSERT INTO withdrawal_snapshots "
            + "(withdrawal_id, student_id, effective_date, source_version, synced_at) "
            + "VALUES (:withdrawalId, :studentId, :effectiveDate, :sourceVersion, :syncedAt) "
            + "ON DUPLICATE KEY UPDATE "
            + "student_id = IF(VALUES(source_version) > source_version, VALUES(student_id), student_id), "
            + "effective_date = IF(VALUES(source_version) > source_version, VALUES(effective_date), effective_date), "
            + "synced_at = IF(VALUES(source_version) > source_version, VALUES(synced_at), synced_at), "
            + "source_version = IF(VALUES(source_version) > source_version, VALUES(source_version), source_version)",
            nativeQuery = true)
    void upsertIfNewer(@Param("withdrawalId") Long withdrawalId, @Param("studentId") Long studentId,
                       @Param("effectiveDate") LocalDate effectiveDate,
                       @Param("sourceVersion") Long sourceVersion, @Param("syncedAt") LocalDateTime syncedAt);
}
