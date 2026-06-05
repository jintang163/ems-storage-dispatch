package com.ems.repository;

import com.ems.domain.entity.MeasurementPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeasurementPointRepository extends JpaRepository<MeasurementPoint, Long>, JpaSpecificationExecutor<MeasurementPoint> {

    List<MeasurementPoint> findByDeviceId(Long deviceId);

    List<MeasurementPoint> findByDeviceIdAndEnabledTrue(Long deviceId);

    Optional<MeasurementPoint> findByDeviceIdAndPointCode(Long deviceId, String pointCode);

    @Modifying
    @Query("DELETE FROM MeasurementPoint mp WHERE mp.deviceId = :deviceId")
    void deleteByDeviceId(@Param("deviceId") Long deviceId);

    @Modifying
    @Query("UPDATE MeasurementPoint mp SET mp.enabled = :enabled WHERE mp.id = :id")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);
}
