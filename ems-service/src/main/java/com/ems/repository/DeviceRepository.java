package com.ems.repository;

import com.ems.domain.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

    Optional<Device> findByDeviceSn(String deviceSn);

    boolean existsByDeviceSn(String deviceSn);

    List<Device> findByDeviceTypeId(Long deviceTypeId);

    List<Device> findByStatus(String status);

    List<Device> findByEnabledTrue();

    Page<Device> findByDeviceSnContainingAndNameContaining(String deviceSn, String name, Pageable pageable);

    @Modifying
    @Query("UPDATE Device d SET d.status = :status, d.lastOnlineAt = :lastOnlineAt WHERE d.deviceSn = :deviceSn")
    int updateStatus(@Param("deviceSn") String deviceSn, @Param("status") String status, @Param("lastOnlineAt") LocalDateTime lastOnlineAt);

    @Modifying
    @Query("UPDATE Device d SET d.enabled = :enabled WHERE d.id = :id")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);
}
