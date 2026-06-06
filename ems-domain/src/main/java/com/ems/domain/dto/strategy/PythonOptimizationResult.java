package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PythonOptimizationResult {

    private boolean success;
    private String message;
    private List<DispatchPlanHourDTO> planHours;
    private BigDecimal expectedRevenue;
    private BigDecimal expectedDegradation;
    private BigDecimal expectedDemandSaving;
    private BigDecimal totalObjectiveScore;
    private BigDecimal arbitrageScore;
    private BigDecimal lifespanScore;
    private BigDecimal demandScore;
}
