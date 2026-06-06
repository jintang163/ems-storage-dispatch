package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "strategy_config", indexes = {
        @Index(name = "idx_strategy_type", columnList = "strategy_type"),
        @Index(name = "idx_strategy_enabled", columnList = "enabled")
})
public class StrategyConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;

    @Column(name = "strategy_type", nullable = false, length = 50)
    private String strategyType;

    @Column(name = "strategy_code", nullable = false, length = 50, unique = true)
    private String strategyCode;

    @Column(name = "arbitrage_weight", precision = 5, scale = 2)
    private BigDecimal arbitrageWeight = new BigDecimal("0.50");

    @Column(name = "lifespan_weight", precision = 5, scale = 2)
    private BigDecimal lifespanWeight = new BigDecimal("0.30");

    @Column(name = "demand_weight", precision = 5, scale = 2)
    private BigDecimal demandWeight = new BigDecimal("0.20");

    @Column(name = "max_charge_rate", precision = 5, scale = 2)
    private BigDecimal maxChargeRate = new BigDecimal("0.50");

    @Column(name = "max_discharge_rate", precision = 5, scale = 2)
    private BigDecimal maxDischargeRate = new BigDecimal("0.50");

    @Column(name = "min_soc", precision = 5, scale = 2)
    private BigDecimal minSoc = new BigDecimal("20.00");

    @Column(name = "max_soc", precision = 5, scale = 2)
    private BigDecimal maxSoc = new BigDecimal("90.00");

    @Column(name = "max_daily_cycles", precision = 5, scale = 2)
    private BigDecimal maxDailyCycles = new BigDecimal("1.00");

    @Column(name = "max_depth_of_discharge", precision = 5, scale = 2)
    private BigDecimal maxDepthOfDischarge = new BigDecimal("70.00");

    @Column(name = "demand_threshold_ratio", precision = 5, scale = 2)
    private BigDecimal demandThresholdRatio = new BigDecimal("0.90");

    @Column(name = "price_forecast_enabled", nullable = false)
    private Boolean priceForecastEnabled = true;

    @Column(name = "peak_valley_arbitrage_enabled", nullable = false)
    private Boolean peakValleyArbitrageEnabled = true;

    @Column(name = "peak_shaving_enabled", nullable = false)
    private Boolean peakShavingEnabled = true;

    @Column(name = "valley_filling_enabled", nullable = false)
    private Boolean valleyFillingEnabled = true;

    @Column(name = "demand_control_enabled", nullable = false)
    private Boolean demandControlEnabled = true;

    @Column(name = "battery_sn", length = 50)
    private String batterySn;

    @Column(name = "transformer_code", length = 50)
    private String transformerCode;

    @Column(name = "schedule_interval_minutes", nullable = false)
    private Integer scheduleIntervalMinutes = 60;

    @Column(name = "rolling_optimization_enabled", nullable = false)
    private Boolean rollingOptimizationEnabled = true;

    @Column(name = "rolling_interval_minutes")
    private Integer rollingIntervalMinutes = 15;

    @Column(name = "look_ahead_hours")
    private Integer lookAheadHours = 24;

    private Integer priority = 5;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "default_strategy", nullable = false)
    private Boolean defaultStrategy = false;

    @Column(length = 1000)
    private String description;
}
