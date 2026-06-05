package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "alarm_record", indexes = {
        @Index(name = "idx_alarm_device", columnList = "device_id"),
        @Index(name = "idx_alarm_status", columnList = "status"),
        @Index(name = "idx_alarm_time", columnList = "alarm_time")
})
public class AlarmRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "point_id")
    private Long pointId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "alarm_type", nullable = false, length = 50)
    private String alarmType;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "point_value", precision = 18, scale = 4)
    private BigDecimal pointValue;

    @Column(name = "alarm_time", nullable = false)
    private LocalDateTime alarmTime;

    @Column(name = "acknowledge_time")
    private LocalDateTime acknowledgeTime;

    @Column(name = "clear_time")
    private LocalDateTime clearTime;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "cleared_by", length = 100)
    private String clearedBy;
}
