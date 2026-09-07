package com.msa4lmsv2payment.domain.academicsnapshot.repository;

import com.msa4lmsv2payment.domain.academicsnapshot.entity.SemesterSnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterSnapshotRepository extends JpaRepository<SemesterSnapshot, Long> {

    @Modifying
    @Query(value = "INSERT INTO semester_snapshots "
            + "(semester_id, display_name, start_date, end_date, source_version, synced_at) "
            + "VALUES (:semesterId, :displayName, :startDate, :endDate, :sourceVersion, :syncedAt) "
            + "ON DUPLICATE KEY UPDATE "
            + "display_name = IF(VALUES(source_version) > source_version, VALUES(display_name), display_name), "
            + "start_date = IF(VALUES(source_version) > source_version, VALUES(start_date), start_date), "
            + "end_date = IF(VALUES(source_version) > source_version, VALUES(end_date), end_date), "
            + "synced_at = IF(VALUES(source_version) > source_version, VALUES(synced_at), synced_at), "
            + "source_version = IF(VALUES(source_version) > source_version, VALUES(source_version), source_version)",
            nativeQuery = true)
    void upsertIfNewer(@Param("semesterId") Long semesterId, @Param("displayName") String displayName,
                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                       @Param("sourceVersion") Long sourceVersion, @Param("syncedAt") LocalDateTime syncedAt);
}
