package com.ems.repository;

import com.ems.domain.entity.AlarmRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlarmRecordRepository extends JpaRepository<AlarmRecord, Long>, JpaSpecificationExecutor<AlarmRecord> {

    Page<AlarmRecord> findByDeviceId(Long deviceId, Pageable pageable);

    Page<AlarmRecord> findByStatus(String status, Pageable pageable);

    Page<AlarmRecord> findBySeverity(String severity, Pageable pageable);

    List<AlarmRecord> findByStatusAndDeviceIdAndPointId(String status, Long deviceId, Long pointId);

    @Modifying
    @Query("UPDATE AlarmRecord a SET a.status = 'acknowledged', a.acknowledgeTime = :acknowledgeTime, " +
           "a.acknowledgedBy = :acknowledgedBy WHERE a.id = :id")
    int acknowledge(@Param("id") Long id, @Param("acknowledgeTime") LocalDateTime acknowledgeTime,
                    @Param("acknowledgedBy") String acknowledgedBy);

    @Modifying
    @Query("UPDATE AlarmRecord a SET a.status = 'cleared', a.clearTime = :clearTime, " +
           "a.clearedBy = :clearedBy WHERE a.id = :id")
    int clear(@Param("id") Long id, @Param("clearTime") LocalDateTime clearTime,
              @Param("clearedBy") String clearedBy);

    @Query("SELECT COUNT(a) FROM AlarmRecord a WHERE a.status = 'active'")
    long countActiveAlarms();

    @Query("SELECT COUNT(a) FROM AlarmRecord a WHERE a.status = 'active' AND a.severity = :severity")
    long countActiveAlarmsBySeverity(@Param("severity") String severity);
}
