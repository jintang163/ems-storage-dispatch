package com.ems.domain.dto.mqtt;

import lombok.Data;

import java.time.Instant;

@Data
public class BmsDataPayload {

    private String deviceSn;
    private String location;
    private Double soc;
    private Double soh;
    private Double totalVoltage;
    private Double totalCurrent;
    private Double maxCellVoltage;
    private Double minCellVoltage;
    private Integer maxCellVoltageNo;
    private Integer minCellVoltageNo;
    private Double avgCellVoltage;
    private Double maxTemperature;
    private Double minTemperature;
    private Double avgTemperature;
    private Integer maxTempNo;
    private Integer minTempNo;
    private Double chargeCurrentLimit;
    private Double dischargeCurrentLimit;
    private Double maxChargePower;
    private Double maxDischargePower;
    private Integer cycleCount;
    private Double capacity;
    private Double remainingCapacity;
    private Double designCapacity;
    private Integer bmsStatus;
    private Boolean chargeEnable;
    private Boolean dischargeEnable;
    private Boolean heatingEnable;
    private Integer faultCode;
    private Integer warningCode;
    private Integer protectionCode;
    private Integer cellCount;
    private Integer tempSensorCount;
    private Long timestamp;

    public Instant getTimestampInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
