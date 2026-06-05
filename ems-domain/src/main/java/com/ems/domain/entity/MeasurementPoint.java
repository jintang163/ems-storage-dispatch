package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "measurement_point", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_point", columnNames = {"device_id", "point_code"})
}, indexes = {
        @Index(name = "idx_mp_device_id", columnList = "device_id")
})
public class MeasurementPoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", insertable = false, updatable = false)
    private Device device;

    @Column(name = "point_code", nullable = false, length = 100)
    private String pointCode;

    @Column(name = "point_name", nullable = false, length = 200)
    private String pointName;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(length = 20)
    private String unit;

    @Column(name = "register_address")
    private Integer registerAddress;

    @Column(name = "register_count")
    private Integer registerCount = 1;

    @Column(name = "scale_factor", precision = 10, scale = 4)
    private BigDecimal scaleFactor = BigDecimal.ONE;

    @Column(name = "offset_value", precision = 10, scale = 4)
    private BigDecimal offsetValue = BigDecimal.ZERO;

    @Column(name = "alarm_high", precision = 18, scale = 4)
    private BigDecimal alarmHigh;

    @Column(name = "alarm_low", precision = 18, scale = 4)
    private BigDecimal alarmLow;

    @Column(nullable = false)
    private Boolean enabled = true;
}
