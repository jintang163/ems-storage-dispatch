package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class RealTimeControlRequest {

    private String strategyCode;

    private String batterySn;

    private String transformerCode;

    private String pcsSn;

    private String meterSn;

    private String pvSn;

    private BigDecimal currentSoc;

    private BigDecimal currentLoad;

    private BigDecimal currentPv;

    private BigDecimal currentDemand;

    private BigDecimal currentPrice;

    private BigDecimal batteryTemperature;

    private BigDecimal maxBatteryTemperature;

    private BigDecimal batteryHealth;

    private String executionType;

    private Integer pcsRunningStatus;

    private Integer pcsWorkMode;

    private BigDecimal pcsActivePower;

    private Boolean pcsOnline;

    private Instant pcsLastDataTime;

    private Instant loadLastDataTime;

    private Instant pvLastDataTime;

    private BigDecimal previousLoad;

    private BigDecimal previousPv;

    private BigDecimal loadSuddenChangeThreshold = new BigDecimal("50");

    private BigDecimal pvSuddenChangeThreshold = new BigDecimal("30");

    private Integer dataTimeoutSeconds = 300;

    private BigDecimal batteryOverTempThreshold = new BigDecimal("55");

    private Boolean safetyInterlockEnabled = true;

    private Boolean alarmEnabled = true;
}
