package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "device", indexes = {
        @Index(name = "idx_device_type_id", columnList = "device_type_id"),
        @Index(name = "idx_device_status", columnList = "status"),
        @Index(name = "idx_device_sn", columnList = "device_sn", unique = true)
})
public class Device extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_sn", nullable = false, unique = true, length = 100)
    private String deviceSn;

    @Column(name = "device_type_id", nullable = false)
    private Long deviceTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_type_id", insertable = false, updatable = false)
    private DeviceType deviceType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String protocol = "modbus";

    @Column(length = 100)
    private String host;

    private Integer port;

    @Column(name = "slave_id")
    private Integer slaveId;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, length = 20)
    private String status = "offline";

    @Column(name = "sampling_interval", nullable = false)
    private Integer samplingInterval = 5000;

    @Column(nullable = false)
    private Boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(length = 500)
    private String description;

    @Column(name = "last_online_at")
    private LocalDateTime lastOnlineAt;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeasurementPoint> measurementPoints;
}
