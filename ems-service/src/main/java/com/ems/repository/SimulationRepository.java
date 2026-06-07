package com.ems.repository;

import com.ems.domain.entity.Simulation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SimulationRepository extends JpaRepository<Simulation, Long> {

    Page<Simulation> findByStrategyCode(String strategyCode, Pageable pageable);

    Page<Simulation> findByStatus(String status, Pageable pageable);

    Page<Simulation> findBySimulationDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("SELECT s FROM Simulation s WHERE s.strategyCode = :strategyCode " +
           "AND s.simulationDate BETWEEN :startDate AND :endDate " +
           "AND s.status = 'COMPLETED' " +
           "ORDER BY s.simulationDate DESC")
    List<Simulation> findCompletedSimulations(
            @Param("strategyCode") String strategyCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM Simulation s WHERE s.simulationName LIKE %:keyword% " +
           "OR s.strategyName LIKE %:keyword%")
    Page<Simulation> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT DISTINCT s.strategyCode FROM Simulation s WHERE s.status = 'COMPLETED'")
    List<String> findDistinctStrategyCodes();
}
