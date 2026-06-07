package com.ems.repository;

import com.ems.domain.entity.SimulationHourData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulationHourDataRepository extends JpaRepository<SimulationHourData, Long> {

    List<SimulationHourData> findBySimulationIdOrderByHourIndexAsc(Long simulationId);

    @Modifying
    @Query("DELETE FROM SimulationHourData d WHERE d.simulation.id = :simulationId")
    void deleteBySimulationId(@Param("simulationId") Long simulationId);

    @Query("SELECT d FROM SimulationHourData d WHERE d.simulation.id = :simulationId " +
           "AND d.actionType = :actionType ORDER BY d.hourIndex ASC")
    List<SimulationHourData> findBySimulationIdAndActionType(
            @Param("simulationId") Long simulationId,
            @Param("actionType") String actionType);
}
