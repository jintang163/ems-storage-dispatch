package com.ems.repository;

import com.ems.domain.entity.LoadForecast;
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
public interface LoadForecastRepository extends JpaRepository<LoadForecast, Long>, JpaSpecificationExecutor<LoadForecast> {

    List<LoadForecast> findByForecastDateOrderByHourIndex(LocalDate forecastDate);

    List<LoadForecast> findByForecastDateAndTransformerCodeOrderByHourIndex(LocalDate forecastDate, String transformerCode);

    Optional<LoadForecast> findByForecastDateAndHourIndexAndTransformerCode(LocalDate forecastDate, Integer hourIndex, String transformerCode);

    @Query("SELECT l FROM LoadForecast l WHERE l.forecastDate = :forecastDate AND l.isPeakHour = true ORDER BY l.hourIndex")
    List<LoadForecast> findPeakHoursByDate(@Param("forecastDate") LocalDate forecastDate);

    @Query("SELECT MAX(l.forecastLoad) FROM LoadForecast l WHERE l.forecastDate = :forecastDate AND l.transformerCode = :transformerCode")
    java.math.BigDecimal findMaxLoadByDateAndTransformer(@Param("forecastDate") LocalDate forecastDate, @Param("transformerCode") String transformerCode);

    @Query("SELECT MIN(l.forecastLoad) FROM LoadForecast l WHERE l.forecastDate = :forecastDate AND l.transformerCode = :transformerCode")
    java.math.BigDecimal findMinLoadByDateAndTransformer(@Param("forecastDate") LocalDate forecastDate, @Param("transformerCode") String transformerCode);

    @Query("SELECT AVG(l.forecastLoad) FROM LoadForecast l WHERE l.forecastDate = :forecastDate AND l.transformerCode = :transformerCode")
    java.math.BigDecimal findAvgLoadByDateAndTransformer(@Param("forecastDate") LocalDate forecastDate, @Param("transformerCode") String transformerCode);

    @Query("SELECT l.forecastDate, MAX(l.forecastLoad), MIN(l.forecastLoad), AVG(l.forecastLoad) " +
           "FROM LoadForecast l WHERE l.forecastDate BETWEEN :startDate AND :endDate " +
           "AND l.transformerCode = :transformerCode " +
           "GROUP BY l.forecastDate ORDER BY l.forecastDate")
    List<Object[]> findLoadStatisticsByDateRangeAndTransformer(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("transformerCode") String transformerCode);

    @Modifying
    @Query("DELETE FROM LoadForecast l WHERE l.forecastDate = :forecastDate AND l.transformerCode = :transformerCode")
    void deleteByForecastDateAndTransformerCode(@Param("forecastDate") LocalDate forecastDate, @Param("transformerCode") String transformerCode);

    boolean existsByForecastDateAndTransformerCode(LocalDate forecastDate, String transformerCode);
}
