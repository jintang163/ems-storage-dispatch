package com.ems.repository;

import com.ems.domain.entity.TimeOfUsePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeOfUsePriceRepository extends JpaRepository<TimeOfUsePrice, Long>, JpaSpecificationExecutor<TimeOfUsePrice> {

    List<TimeOfUsePrice> findByPeriodTypeAndEnabledTrue(String periodType);

    List<TimeOfUsePrice> findByEnabledTrueOrderByStartTime();

    @Query("SELECT t FROM TimeOfUsePrice t WHERE t.enabled = true " +
           "AND t.effectiveDate <= :date AND (t.expiryDate IS NULL OR t.expiryDate >= :date) " +
           "AND t.startTime <= :time AND t.endTime > :time " +
           "ORDER BY t.createdAt DESC")
    Optional<TimeOfUsePrice> findCurrentPrice(@Param("date") LocalDate date, @Param("time") LocalTime time);

    @Query("SELECT t FROM TimeOfUsePrice t WHERE t.enabled = true " +
           "AND t.effectiveDate <= :date AND (t.expiryDate IS NULL OR t.expiryDate >= :date) " +
           "ORDER BY t.startTime")
    List<TimeOfUsePrice> findValidPrices(@Param("date") LocalDate date);
}
