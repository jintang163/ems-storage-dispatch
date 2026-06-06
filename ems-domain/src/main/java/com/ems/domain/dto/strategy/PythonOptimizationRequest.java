package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PythonOptimizationRequest {

    private String strategyCode;
    private String planDate;
    private Integer startHour;
    private BigDecimal initialSoc;
    private BigDecimal currentLoad;
    private BigDecimal currentPv;
    private BigDecimal currentPrice;
    private List<PriceForecastDTO> priceForecast;
    private List<LoadForecastDTO> loadForecast;
    private StrategyConfigDTO strategyConfig;
}
