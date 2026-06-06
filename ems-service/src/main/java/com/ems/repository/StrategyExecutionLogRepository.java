package com.ems.repository;

import com.ems.domain.entity.StrategyExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StrategyExecutionLogRepository extends JpaRepository<StrategyExecutionLog, Long>, JpaSpecificationExecutor<StrategyExecutionLog> {

    Page<StrategyExecutionLog> findByStrategyId(Long strategyId, Pageable pageable);

    Page<StrategyExecutionLog> findByStrategyCode(String strategyCode, Pageable pageable);

    List<StrategyExecutionLog> findByStrategyIdAndExecutionTimeBetweenOrderByExecutionTimeDesc(
            Long strategyId, LocalDateTime startTime, LocalDateTime endTime);

    List<StrategyExecutionLog> findByStrategyCodeAndExecutionTimeBetweenOrderByExecutionTimeDesc(
            String strategyCode, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT l FROM StrategyExecutionLog l WHERE l.strategyCode = :strategyCode " +
           "AND l.executionTime >= :startTime ORDER BY l.executionTime DESC")
    List<StrategyExecutionLog> findRecentExecutions(@Param("strategyCode") String strategyCode,
                                                    @Param("startTime") LocalDateTime startTime);

    @Query("SELECT l.status, COUNT(l) FROM StrategyExecutionLog l WHERE l.executionTime >= :startTime GROUP BY l.status")
    List<Object[]> countByStatusSince(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT SUM(l.revenue), SUM(l.degradationCost), SUM(l.demandSaving) " +
           "FROM StrategyExecutionLog l WHERE l.strategyCode = :strategyCode " +
           "AND l.executionTime BETWEEN :startTime AND :endTime")
    List<Object[]> sumBenefitsByStrategyCodeAndDateRange(
            @Param("strategyCode") String strategyCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT SUM(l.revenue), SUM(l.degradationCost), SUM(l.demandSaving) " +
           "FROM StrategyExecutionLog l WHERE l.executionTime BETWEEN :startTime AND :endTime")
    List<Object[]> sumTotalBenefitsByDateRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT DATE(l.executionTime), SUM(l.revenue), SUM(l.degradationCost), SUM(l.demandSaving) " +
           "FROM StrategyExecutionLog l WHERE l.executionTime BETWEEN :startTime AND :endTime " +
           "GROUP BY DATE(l.executionTime) ORDER BY DATE(l.executionTime)")
    List<Object[]> sumDailyBenefitsByDateRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(l) FROM StrategyExecutionLog l WHERE l.actionTaken = :actionType " +
           "AND l.executionTime BETWEEN :startTime AND :endTime")
    long countByActionTypeAndDateRange(
            @Param("actionType") String actionType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT MAX(l.predictedDemand), AVG(l.predictedDemand) " +
           "FROM StrategyExecutionLog l WHERE l.strategyCode = :strategyCode " +
           "AND l.executionTime BETWEEN :startTime AND :endTime")
    List<Object[]> getDemandStatsByStrategyCodeAndDateRange(
            @Param("strategyCode") String strategyCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
