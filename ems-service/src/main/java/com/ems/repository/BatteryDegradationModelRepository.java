package com.ems.repository;

import com.ems.domain.entity.BatteryDegradationModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatteryDegradationModelRepository extends JpaRepository<BatteryDegradationModel, Long> {

    List<BatteryDegradationModel> findByEnabledTrue();

    List<BatteryDegradationModel> findByModelTypeAndEnabledTrue(String modelType);

    List<BatteryDegradationModel> findByBatteryTypeAndEnabledTrue(String batteryType);

    Optional<BatteryDegradationModel> findByDefaultModelTrueAndEnabledTrue();

    @Query("SELECT m FROM BatteryDegradationModel m WHERE m.batteryType = :batteryType " +
           "AND m.defaultModel = true AND m.enabled = true")
    Optional<BatteryDegradationModel> findDefaultByBatteryType(@Param("batteryType") String batteryType);
}
