package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "strategy_execution_log", indexes = {
        @Index(name = "idx_exec_strategy", columnList = "strategy_id"),
        @Index(name = "idx_exec_time", columnList = "execution_time"),
        @Index(name = "idx_exec_status", columnList = "status")
})
public class StrategyExecutionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "strategy_code", nullable = false, length = 50)
    private String strategyCode;

    @Column(name = "execution_time", nullable = false)
    private LocalDateTime executionTime;

    @Column(name = "execution_type", length = 50)
    private String executionType;

    @Column(name = "current_soc", precision = 5, scale = 2)
    private BigDecimal currentSoc;

    @Column(name = "current_load", precision = 10, scale = 2)
    private BigDecimal currentLoad;

    @Column(name = "current_pv", precision = 10, scale = 2)
    private BigDecimal currentPv;

    @Column(name = "current_demand", precision = 10, scale = 2)
    private BigDecimal currentDemand;

    @Column(name = "predicted_demand", precision = 10, scale = 2)
    private BigDecimal predictedDemand;

    @Column(name = "demand_threshold", precision = 10, scale = 2)
    private BigDecimal demandThreshold;

    @Column(name = "current_price", precision = 10, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "period_type", length = 20)
    private String periodType;

    @Column(name = "action_taken", length = 50)
    private String actionTaken;

    @Column(name = "target_power", precision = 10, scale = 2)
    private BigDecimal targetPower;

    @Column(name = "actual_power", precision = 10, scale = 2)
    private BigDecimal actualPower;

    @Column(name = "revenue", precision = 12, scale = 4)
    private BigDecimal revenue;

    @Column(name = "degradation_cost", precision = 12, scale = 4)
    private BigDecimal degradationCost;

    @Column(name = "demand_saving", precision = 12, scale = 4)
    private BigDecimal demandSaving;

    @Column(name = "battery_temperature", precision = 5, scale = 2)
    private BigDecimal batteryTemperature;

    @Column(name = "battery_health", precision = 5, scale = 4)
    private BigDecimal batteryHealth;

    @Column(nullable = false, length = 20)
    private String status = "success";

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(length = 1000)
    private String remark;
}
