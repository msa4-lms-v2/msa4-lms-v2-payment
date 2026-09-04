package com.msa4lmsv2payment.domain.academicsnapshot.repository;

import com.msa4lmsv2payment.domain.academicsnapshot.entity.StudentSnapshot;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentSnapshotRepository extends JpaRepository<StudentSnapshot, Long> {

    Optional<StudentSnapshot> findByUserId(Long userId);

    @Modifying
    @Query(value = "INSERT INTO student_snapshots "
            + "(student_id, user_id, display_name, department_name, source_version, synced_at) "
            + "VALUES (:studentId, :userId, :displayName, :departmentName, :sourceVersion, :syncedAt) "
            + "ON DUPLICATE KEY UPDATE "
            + "user_id = IF(VALUES(source_version) > source_version, VALUES(user_id), user_id), "
            + "display_name = IF(VALUES(source_version) > source_version, VALUES(display_name), display_name), "
            + "department_name = IF(VALUES(source_version) > source_version, VALUES(department_name), department_name), "
            + "synced_at = IF(VALUES(source_version) > source_version, VALUES(synced_at), synced_at), "
            + "source_version = IF(VALUES(source_version) > source_version, VALUES(source_version), source_version)",
            nativeQuery = true)
    void upsertIfNewer(@Param("studentId") Long studentId, @Param("userId") Long userId,
                       @Param("displayName") String displayName, @Param("departmentName") String departmentName,
                       @Param("sourceVersion") Long sourceVersion, @Param("syncedAt") LocalDateTime syncedAt);
}
