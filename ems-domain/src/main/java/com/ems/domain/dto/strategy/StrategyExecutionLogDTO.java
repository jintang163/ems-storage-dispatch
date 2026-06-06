package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StrategyExecutionLogDTO {

    private Long id;

    private Long strategyId;

    private String strategyCode;

    private LocalDateTime executionTime;

    private String executionType;

    private BigDecimal currentSoc;

    private BigDecimal currentLoad;

    private BigDecimal currentPv;

    private BigDecimal currentDemand;

    private BigDecimal predictedDemand;

    private BigDecimal demandThreshold;

    private BigDecimal currentPrice;

    private String periodType;

    private String actionTaken;

    private BigDecimal targetPower;

    private BigDecimal actualPower;

    private BigDecimal revenue;

    private BigDecimal degradationCost;

    private BigDecimal demandSaving;

    private BigDecimal batteryTemperature;

    private BigDecimal batteryHealth;

    private String status;

    private String errorMessage;

    private String remark;
}
