package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "simulation", indexes = {
        @Index(name = "idx_simulation_strategy", columnList = "strategy_code"),
        @Index(name = "idx_simulation_status", columnList = "status"),
        @Index(name = "idx_simulation_date", columnList = "simulation_date")
})
public class Simulation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_name", nullable = false, length = 200)
    private String simulationName;

    @Column(name = "simulation_date", nullable = false)
    private LocalDate simulationDate;

    @Column(name = "strategy_code", nullable = false, length = 50)
    private String strategyCode;

    @Column(name = "strategy_name", length = 100)
    private String strategyName;

    @Column(name = "strategy_type", length = 50)
    private String strategyType;

    @Column(name = "battery_sn", length = 50)
    private String batterySn;

    @Column(name = "transformer_code", length = 50)
    private String transformerCode;

    @Column(name = "initial_soc", precision = 5, scale = 2)
    private BigDecimal initialSoc;

    @Column(name = "battery_capacity", precision = 10, scale = 2)
    private BigDecimal batteryCapacity;

    @Column(name = "battery_power", precision = 10, scale = 2)
    private BigDecimal batteryPower;

    @Column(name = "charge_efficiency", precision = 5, scale = 4)
    private BigDecimal chargeEfficiency;

    @Column(name = "discharge_efficiency", precision = 5, scale = 4)
    private BigDecimal dischargeEfficiency;

    @Column(name = "min_soc", precision = 5, scale = 2)
    private BigDecimal minSoc;

    @Column(name = "max_soc", precision = 5, scale = 2)
    private BigDecimal maxSoc;

    @Column(name = "demand_threshold", precision = 10, scale = 2)
    private BigDecimal demandThreshold;

    @Column(name = "demand_price", precision = 10, scale = 4)
    private BigDecimal demandPrice;

    @Column(name = "degradation_model_id")
    private Long degradationModelId;

    @Column(name = "data_source", length = 50)
    private String dataSource;

    @Column(name = "data_start_date")
    private LocalDate dataStartDate;

    @Column(name = "data_end_date")
    private LocalDate dataEndDate;

    @Column(name = "total_revenue", precision = 15, scale = 4)
    private BigDecimal totalRevenue;

    @Column(name = "total_arbitrage_revenue", precision = 15, scale = 4)
    private BigDecimal totalArbitrageRevenue;

    @Column(name = "total_demand_saving", precision = 15, scale = 4)
    private BigDecimal totalDemandSaving;

    @Column(name = "total_degradation_cost", precision = 15, scale = 4)
    private BigDecimal totalDegradationCost;

    @Column(name = "net_revenue", precision = 15, scale = 4)
    private BigDecimal netRevenue;

    @Column(name = "total_charge_energy", precision = 12, scale = 4)
    private BigDecimal totalChargeEnergy;

    @Column(name = "total_discharge_energy", precision = 12, scale = 4)
    private BigDecimal totalDischargeEnergy;

    @Column(name = "cycle_count", precision = 10, scale = 4)
    private BigDecimal cycleCount;

    @Column(name = "avg_depth_of_discharge", precision = 5, scale = 2)
    private BigDecimal avgDepthOfDischarge;

    @Column(name = "max_demand", precision = 10, scale = 2)
    private BigDecimal maxDemand;

    @Column(name = "min_demand", precision = 10, scale = 2)
    private BigDecimal minDemand;

    @Column(name = "avg_demand", precision = 10, scale = 2)
    private BigDecimal avgDemand;

    @Column(name = "demand_peak_reduction", precision = 10, scale = 2)
    private BigDecimal demandPeakReduction;

    @Column(name = "soh_start", precision = 5, scale = 4)
    private BigDecimal sohStart;

    @Column(name = "soh_end", precision = 5, scale = 4)
    private BigDecimal sohEnd;

    @Column(name = "soh_degradation", precision = 10, scale = 8)
    private BigDecimal sohDegradation;

    @Column(name = "estimated_remaining_cycles")
    private Integer estimatedRemainingCycles;

    @Column(name = "estimated_remaining_lifespan_years", precision = 5, scale = 2)
    private BigDecimal estimatedRemainingLifespanYears;

    @Column(name = "self_consumption_rate", precision = 5, scale = 2)
    private BigDecimal selfConsumptionRate;

    @Column(name = "self_sufficiency_rate", precision = 5, scale = 2)
    private BigDecimal selfSufficiencyRate;

    @Column(name = "round_trip_efficiency", precision = 5, scale = 2)
    private BigDecimal roundTripEfficiency;

    @Column(length = 20)
    private String status;

    @Column(name = "started_at")
    private java.time.LocalDateTime startedAt;

    @Column(name = "completed_at")
    private java.time.LocalDateTime completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(length = 1000)
    private String remark;

    @OneToMany(mappedBy = "simulation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SimulationHourData> hourData = new ArrayList<>();

    @Transient
    private List<com.ems.domain.dto.simulation.SimulationDataPointDTO> loadData;

    @Transient
    private List<com.ems.domain.dto.simulation.SimulationDataPointDTO> pvData;

    @Transient
    private List<com.ems.domain.dto.simulation.SimulationDataPointDTO> priceData;

    public void addHourData(SimulationHourData data) {
        hourData.add(data);
        data.setSimulation(this);
    }

    public void removeHourData(SimulationHourData data) {
        hourData.remove(data);
        data.setSimulation(null);
    }
}
