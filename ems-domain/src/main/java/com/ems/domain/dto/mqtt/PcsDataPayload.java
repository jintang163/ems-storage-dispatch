package com.ems.domain.dto.mqtt;

import lombok.Data;

import java.time.Instant;

@Data
public class PcsDataPayload {

    private String deviceSn;
    private String location;
    private Double dcVoltage;
    private Double dcCurrent;
    private Double dcPower;
    private Double acVoltageA;
    private Double acVoltageB;
    private Double acVoltageC;
    private Double acCurrentA;
    private Double acCurrentB;
    private Double acCurrentC;
    private Double activePower;
    private Double reactivePower;
    private Double apparentPower;
    private Double powerFactor;
    private Double frequency;
    private Double efficiency;
    private Double totalChargeEnergy;
    private Double totalDischargeEnergy;
    private Double dailyChargeEnergy;
    private Double dailyDischargeEnergy;
    private Double gridVoltage;
    private Double gridFrequency;
    private Double inverterTemperature;
    private Double heatSinkTemperature;
    private Integer runningStatus;
    private Integer workMode;
    private Integer controlMode;
    private Double powerSetpoint;
    private Double reactivePowerSetpoint;
    private Boolean gridConnectStatus;
    private Integer faultCode;
    private Integer warningCode;
    private Double dcMaxVoltage;
    private Double dcMinVoltage;
    private Double acMaxCurrent;
    private Long timestamp;

    public Instant getTimestampInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
