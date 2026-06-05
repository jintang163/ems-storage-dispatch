package com.ems.domain.dto.mqtt;

import lombok.Data;

import java.time.Instant;

@Data
public class PvDataPayload {

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
    private Double acPower;
    private Double acReactivePower;
    private Double powerFactor;
    private Double frequency;
    private Double efficiency;
    private Double totalEnergy;
    private Double dailyEnergy;
    private Double moduleTemperature;
    private Double ambientTemperature;
    private Double irradiance;
    private Integer operatingStatus;
    private Integer faultCode;
    private Long timestamp;

    public Instant getTimestampInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
