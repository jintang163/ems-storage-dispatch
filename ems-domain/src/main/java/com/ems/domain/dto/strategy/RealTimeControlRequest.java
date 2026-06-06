package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RealTimeControlRequest {

    private String strategyCode;

    private String batterySn;

    private String transformerCode;

    private BigDecimal currentSoc;

    private BigDecimal currentLoad;

    private BigDecimal currentPv;

    private BigDecimal currentDemand;

    private BigDecimal currentPrice;

    private BigDecimal batteryTemperature;

    private BigDecimal batteryHealth;

    private String executionType;
}
