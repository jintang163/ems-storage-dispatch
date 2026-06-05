package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "alarm_rule")
public class AlarmRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "point_id")
    private Long pointId;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Column(precision = 18, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false, length = 10)
    private String operator;

    @Column(nullable = false, length = 20)
    private String severity = "warning";

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "notification_email", length = 500)
    private String notificationEmail;

    @Column(name = "notification_phone", length = 500)
    private String notificationPhone;

    @Column(length = 500)
    private String description;
}
