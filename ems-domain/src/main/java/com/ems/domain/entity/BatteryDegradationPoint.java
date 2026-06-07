package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "battery_degradation_point", indexes = {
        @Index(name = "idx_degradation_model_id", columnList = "degradation_model_id"),
        @Index(name = "idx_cycle_count", columnList = "cycle_count")
})
public class BatteryDegradationPoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "degradation_model_id", nullable = false)
    private BatteryDegradationModel degradationModel;

    @Column(name = "cycle_count", nullable = false)
    private Integer cycleCount;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal soh;

    @Column(name = "capacity_retention", precision = 5, scale = 4)
    private BigDecimal capacityRetention;

    @Column(name = "internal_resistance_ratio", precision = 5, scale = 4)
    private BigDecimal internalResistanceRatio;

    @Column(precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "depth_of_discharge", precision = 5, scale = 2)
    private BigDecimal depthOfDischarge;

    @Column(name = "charge_rate", precision = 5, scale = 2)
    private BigDecimal chargeRate;

    @Column(name = "discharge_rate", precision = 5, scale = 2)
    private BigDecimal dischargeRate;

    @Column(length = 500)
    private String remarks;
}
