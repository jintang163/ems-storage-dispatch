package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "battery_degradation_model", indexes = {
        @Index(name = "idx_degradation_model_type", columnList = "model_type"),
        @Index(name = "idx_degradation_enabled", columnList = "enabled")
})
public class BatteryDegradationModel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_type", nullable = false, length = 50)
    private String modelType;

    @Column(name = "battery_type", length = 50)
    private String batteryType;

    @Column(name = "degradation_rate_per_cycle", precision = 10, scale = 8)
    private BigDecimal degradationRatePerCycle;

    @Column(name = "decay_constant", precision = 10, scale = 8)
    private BigDecimal decayConstant;

    @Column(name = "end_of_life_soh", nullable = false, precision = 5, scale = 4)
    private BigDecimal endOfLifeSoh;

    @Column(name = "warranty_cycle_count")
    private Integer warrantyCycleCount;

    @Column(name = "warranty_soh", precision = 5, scale = 4)
    private BigDecimal warrantySoh;

    @Column(name = "calendar_aging_rate_per_year", precision = 10, scale = 8)
    private BigDecimal calendarAgingRatePerYear;

    @Column(name = "temperature_factor", precision = 10, scale = 8)
    private BigDecimal temperatureFactor;

    @Column(name = "soc_factor", precision = 10, scale = 8)
    private BigDecimal socFactor;

    @Column(name = "charge_rate_factor", precision = 10, scale = 8)
    private BigDecimal chargeRateFactor;

    @Column(name = "discharge_rate_factor", precision = 10, scale = 8)
    private BigDecimal dischargeRateFactor;

    @Column(name = "depth_of_discharge_factor", precision = 10, scale = 8)
    private BigDecimal depthOfDischargeFactor;

    @Column(name = "max_cycle_count")
    private Integer maxCycleCount;

    @Column(name = "estimated_lifespan_years", precision = 5, scale = 2)
    private BigDecimal estimatedLifespanYears;

    @Column(name = "default_model", nullable = false)
    private Boolean defaultModel = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 1000)
    private String description;

    @OneToMany(mappedBy = "degradationModel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BatteryDegradationPoint> degradationPoints = new ArrayList<>();

    public void addDegradationPoint(BatteryDegradationPoint point) {
        degradationPoints.add(point);
        point.setDegradationModel(this);
    }

    public void removeDegradationPoint(BatteryDegradationPoint point) {
        degradationPoints.remove(point);
        point.setDegradationModel(null);
    }
}
