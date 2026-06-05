package com.ems.repository;

import com.ems.domain.entity.DispatchCommand;
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
public interface DispatchCommandRepository extends JpaRepository<DispatchCommand, Long>, JpaSpecificationExecutor<DispatchCommand> {

    Page<DispatchCommand> findByDeviceId(Long deviceId, Pageable pageable);

    Page<DispatchCommand> findByStatus(String status, Pageable pageable);

    List<DispatchCommand> findByStatusAndPriorityOrderByCreatedAt(String status, Integer priority);

    @Query("SELECT d FROM DispatchCommand d WHERE d.status = 'pending' ORDER BY d.priority DESC, d.createdAt ASC")
    List<DispatchCommand> findPendingCommands();

    @Modifying
    @Query("UPDATE DispatchCommand d SET d.status = :status, d.sentTime = :sentTime WHERE d.id = :id")
    int updateStatusToSent(@Param("id") Long id, @Param("status") String status, @Param("sentTime") LocalDateTime sentTime);

    @Modifying
    @Query("UPDATE DispatchCommand d SET d.status = :status, d.executeTime = :executeTime, " +
           "d.resultMessage = :resultMessage WHERE d.id = :id")
    int updateStatusToExecuted(@Param("id") Long id, @Param("status") String status,
                               @Param("executeTime") LocalDateTime executeTime,
                               @Param("resultMessage") String resultMessage);
}
