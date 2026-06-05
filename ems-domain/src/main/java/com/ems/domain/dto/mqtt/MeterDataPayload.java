package com.ems.domain.dto.mqtt;

import lombok.Data;

import java.time.Instant;

@Data
public class MeterDataPayload {

    private String deviceSn;
    private String location;
    private Double voltageA;
    private Double voltageB;
    private Double voltageC;
    private Double currentA;
    private Double currentB;
    private Double currentC;
    private Double activePower;
    private Double reactivePower;
    private Double apparentPower;
    private Double powerFactor;
    private Double frequency;
    private Double totalActiveEnergy;
    private Double totalReactiveEnergy;
    private Double importActiveEnergy;
    private Double exportActiveEnergy;
    private Double demand;
    private Double thdVoltageA;
    private Double thdCurrentA;
    private Long timestamp;

    public Instant getTimestampInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
