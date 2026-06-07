package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "simulation_hour_data", indexes = {
        @Index(name = "idx_simulation_hour_simulation", columnList = "simulation_id"),
        @Index(name = "idx_simulation_hour_start", columnList = "start_time")
})
public class SimulationHourData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_id", nullable = false)
    private Simulation simulation;

    @Column(name = "hour_index", nullable = false)
    private Integer hourIndex;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "period_type", length = 20)
    private String periodType;

    @Column(precision = 10, scale = 4)
    private BigDecimal price;

    @Column(name = "load_power", precision = 10, scale = 2)
    private BigDecimal loadPower;

    @Column(name = "pv_power", precision = 10, scale = 2)
    private BigDecimal pvPower;

    @Column(name = "grid_power", precision = 10, scale = 2)
    private BigDecimal gridPower;

    @Column(name = "battery_power", precision = 10, scale = 2)
    private BigDecimal batteryPower;

    @Column(name = "battery_energy", precision = 10, scale = 4)
    private BigDecimal batteryEnergy;

    @Column(name = "expected_soc", precision = 5, scale = 2)
    private BigDecimal expectedSoc;

    @Column(name = "charge_rate", precision = 5, scale = 2)
    private BigDecimal chargeRate;

    @Column(name = "depth_of_discharge", precision = 5, scale = 2)
    private BigDecimal depthOfDischarge;

    @Column(name = "action_type", length = 20)
    private String actionType;

    @Column(name = "demand", precision = 10, scale = 2)
    private BigDecimal demand;

    @Column(name = "demand_control_required")
    private Boolean demandControlRequired;

    @Column(precision = 12, scale = 4)
    private BigDecimal revenue;

    @Column(name = "arbitrage_revenue", precision = 12, scale = 4)
    private BigDecimal arbitrageRevenue;

    @Column(name = "demand_saving", precision = 12, scale = 4)
    private BigDecimal demandSaving;

    @Column(name = "degradation_cost", precision = 12, scale = 4)
    private BigDecimal degradationCost;

    @Column(name = "net_profit", precision = 12, scale = 4)
    private BigDecimal netProfit;

    @Column(name = "cumulative_revenue", precision = 15, scale = 4)
    private BigDecimal cumulativeRevenue;

    @Column(name = "soh", precision = 5, scale = 4)
    private BigDecimal soh;

    @Column(name = "battery_temperature", precision = 5, scale = 2)
    private BigDecimal batteryTemperature;

    @Column(length = 500)
    private String remark;
}
