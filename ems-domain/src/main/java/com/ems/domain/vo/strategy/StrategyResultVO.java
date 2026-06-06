package com.ems.domain.vo.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class StrategyResultVO {

    private String strategyCode;

    private String strategyName;

    private String actionType;

    private BigDecimal targetPower;

    private BigDecimal expectedSoc;

    private BigDecimal expectedRevenue;

    private BigDecimal expectedDegradationCost;

    private BigDecimal expectedDemandSaving;

    private BigDecimal totalObjectiveScore;

    private BigDecimal arbitrageScore;

    private BigDecimal lifespanScore;

    private BigDecimal demandScore;

    private String urgencyLevel;

    private List<String> recommendedActions;

    private Map<String, Object> additionalInfo;

    private String status;

    private String message;
}
