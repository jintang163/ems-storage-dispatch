package com.ems.repository;

import com.ems.domain.entity.DispatchPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispatchPlanRepository extends JpaRepository<DispatchPlan, Long>, JpaSpecificationExecutor<DispatchPlan> {

    Page<DispatchPlan> findByStrategyId(Long strategyId, Pageable pageable);

    Page<DispatchPlan> findByPlanDate(LocalDate planDate, Pageable pageable);

    Page<DispatchPlan> findByStatus(String status, Pageable pageable);

    List<DispatchPlan> findByStrategyIdAndPlanDate(Long strategyId, LocalDate planDate);

    Optional<DispatchPlan> findByStrategyCodeAndPlanDateAndPlanType(String strategyCode, LocalDate planDate, String planType);

    List<DispatchPlan> findByPlanDateAndStatus(LocalDate planDate, String status);

    List<DispatchPlan> findByPlanDateBetweenAndStatus(LocalDate startDate, LocalDate endDate, String status);

    @Query("SELECT d FROM DispatchPlan d WHERE d.strategyCode = :strategyCode AND d.planDate = :planDate AND d.status = 'pending' ORDER BY d.createdAt DESC")
    List<DispatchPlan> findLatestPendingPlan(@Param("strategyCode") String strategyCode, @Param("planDate") LocalDate planDate);

    @Modifying
    @Query("UPDATE DispatchPlan d SET d.status = :status, d.executedAt = :executedAt WHERE d.id = :id")
    int updateStatusToExecuted(@Param("id") Long id, @Param("status") String status, @Param("executedAt") LocalDateTime executedAt);

    @Modifying
    @Query("UPDATE DispatchPlan d SET d.status = :status WHERE d.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Query("SELECT d.status, COUNT(d) FROM DispatchPlan d GROUP BY d.status")
    List<Object[]> countByStatus();

    @Query("SELECT SUM(d.expectedRevenue), SUM(d.expectedDegradation), SUM(d.expectedDemandSaving) " +
           "FROM DispatchPlan d WHERE d.planDate BETWEEN :startDate AND :endDate")
    List<Object[]> sumExpectedBenefitsByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
