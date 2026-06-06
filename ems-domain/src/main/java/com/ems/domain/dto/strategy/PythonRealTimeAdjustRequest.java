package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PythonRealTimeAdjustRequest {

    private String strategyCode;
    private String batterySn;
    private BigDecimal currentSoc;
    private BigDecimal expectedSoc;
    private BigDecimal currentLoad;
    private BigDecimal forecastLoad;
    private BigDecimal currentPv;
    private BigDecimal currentPrice;
    private BigDecimal plannedPower;
    private BigDecimal socDeviationThreshold;
    private BigDecimal loadSuddenChangeThreshold;
    private StrategyConfigDTO strategyConfig;
}
