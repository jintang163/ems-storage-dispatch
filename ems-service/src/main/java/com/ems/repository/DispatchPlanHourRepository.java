package com.ems.repository;

import com.ems.domain.entity.DispatchPlanHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface DispatchPlanHourRepository extends JpaRepository<DispatchPlanHour, Long>, JpaSpecificationExecutor<DispatchPlanHour> {

    List<DispatchPlanHour> findByPlanIdOrderByHourIndex(Long planId);

    List<DispatchPlanHour> findByPlanIdAndStartTimeBetweenOrderByHourIndex(Long planId, LocalTime startTime, LocalTime endTime);

    @Query("SELECT h FROM DispatchPlanHour h WHERE h.planId = :planId AND h.hourIndex = :hourIndex")
    DispatchPlanHour findByPlanIdAndHourIndex(@Param("planId") Long planId, @Param("hourIndex") Integer hourIndex);

    @Query("SELECT SUM(CASE WHEN h.power > 0 THEN h.energy ELSE 0 END), " +
           "SUM(CASE WHEN h.power < 0 THEN ABS(h.energy) ELSE 0 END), " +
           "SUM(h.revenue) " +
           "FROM DispatchPlanHour h WHERE h.planId = :planId")
    List<Object[]> calculatePlanTotals(@Param("planId") Long planId);

    void deleteByPlanId(Long planId);
}
