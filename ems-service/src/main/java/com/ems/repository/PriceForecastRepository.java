package com.ems.repository;

import com.ems.domain.entity.PriceForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceForecastRepository extends JpaRepository<PriceForecast, Long>, JpaSpecificationExecutor<PriceForecast> {

    List<PriceForecast> findByForecastDateOrderByHourIndex(LocalDate forecastDate);

    List<PriceForecast> findByForecastDateAndForecastSourceOrderByHourIndex(LocalDate forecastDate, String forecastSource);

    Optional<PriceForecast> findByForecastDateAndHourIndex(LocalDate forecastDate, Integer hourIndex);

    @Query("SELECT p FROM PriceForecast p WHERE p.forecastDate = :forecastDate AND p.isPeak = true ORDER BY p.hourIndex")
    List<PriceForecast> findPeakHoursByDate(@Param("forecastDate") LocalDate forecastDate;

    @Query("SELECT p FROM PriceForecast p WHERE p.forecastDate = :forecastDate AND p.isValley = true ORDER BY p.hourIndex")
    List<PriceForecast> findValleyHoursByDate(@Param("forecastDate") LocalDate forecastDate;

    @Query("SELECT MAX(p.forecastPrice) FROM PriceForecast p WHERE p.forecastDate = :forecastDate")
    java.math.BigDecimal findMaxPriceByDate(@Param("forecastDate") LocalDate forecastDate);

    @Query("SELECT MIN(p.forecastPrice) FROM PriceForecast p WHERE p.forecastDate = :forecastDate")
    java.math.BigDecimal findMinPriceByDate(@Param("forecastDate") LocalDate forecastDate;

    @Query("SELECT AVG(p.forecastPrice) FROM PriceForecast p WHERE p.forecastDate = :forecastDate")
    java.math.BigDecimal findAvgPriceByDate(@Param("forecastDate") LocalDate forecastDate;

    @Query("SELECT p.forecastDate, MAX(p.forecastPrice), MIN(p.forecastPrice), AVG(p.forecastPrice) " +
           "FROM PriceForecast p WHERE p.forecastDate BETWEEN :startDate AND :endDate " +
           "GROUP BY p.forecastDate ORDER BY p.forecastDate")
    List<Object[]> findPriceStatisticsByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Modifying
    @Query("DELETE FROM PriceForecast p WHERE p.forecastDate = :forecastDate")
    void deleteByForecastDate(@Param("forecastDate") LocalDate forecastDate);

    boolean existsByForecastDate(LocalDate forecastDate);
}
