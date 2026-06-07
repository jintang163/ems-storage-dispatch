package com.ems.domain.vo.simulation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SimulationResultVO {

    private Long id;

    private String simulationName;

    private LocalDate simulationDate;

    private String strategyCode;

    private String strategyName;

    private String strategyType;

    private String batterySn;

    private String transformerCode;

    private BigDecimal initialSoc;

    private BigDecimal batteryCapacity;

    private BigDecimal batteryPower;

    private BigDecimal chargeEfficiency;

    private BigDecimal dischargeEfficiency;

    private BigDecimal minSoc;

    private BigDecimal maxSoc;

    private BigDecimal demandThreshold;

    private BigDecimal demandPrice;

    private Long degradationModelId;

    private String dataSource;

    private LocalDate dataStartDate;

    private LocalDate dataEndDate;

    private BigDecimal totalRevenue;

    private BigDecimal totalArbitrageRevenue;

    private BigDecimal totalDemandSaving;

    private BigDecimal totalDegradationCost;

    private BigDecimal netRevenue;

    private BigDecimal totalChargeEnergy;

    private BigDecimal totalDischargeEnergy;

    private BigDecimal cycleCount;

    private BigDecimal avgDepthOfDischarge;

    private BigDecimal maxDemand;

    private BigDecimal minDemand;

    private BigDecimal avgDemand;

    private BigDecimal demandPeakReduction;

    private BigDecimal sohStart;

    private BigDecimal sohEnd;

    private BigDecimal sohDegradation;

    private Integer estimatedRemainingCycles;

    private BigDecimal estimatedRemainingLifespanYears;

    private BigDecimal selfConsumptionRate;

    private BigDecimal selfSufficiencyRate;

    private BigDecimal roundTripEfficiency;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String errorMessage;

    private String remark;

    private LocalDateTime createdAt;

    private List<SimulationHourDataVO> hourData;

    private SimulationReportVO report;
}
