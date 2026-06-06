package com.ems.domain.vo.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StrategyStatisticsVO {

    private LocalDate statisticsDate;

    private String strategyCode;

    private String strategyName;

    private BigDecimal totalRevenue;

    private BigDecimal totalDegradationCost;

    private BigDecimal totalDemandSaving;

    private BigDecimal netBenefit;

    private Integer chargeCount;

    private Integer dischargeCount;

    private BigDecimal totalChargeEnergy;

    private BigDecimal totalDischargeEnergy;

    private BigDecimal averageChargeRate;

    private BigDecimal averageDischargeRate;

    private BigDecimal averageDepthOfDischarge;

    private BigDecimal maxDemandReduction;

    private Integer demandControlEvents;

    private Integer peakShavingEvents;

    private Integer valleyFillingEvents;

    private Integer executionCount;

    private Integer successCount;

    private Integer failureCount;

    private BigDecimal successRate;

    private String batterySn;

    private String transformerCode;
}
