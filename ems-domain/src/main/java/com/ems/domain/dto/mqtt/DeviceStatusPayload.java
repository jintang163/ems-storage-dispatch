package com.ems.domain.dto.mqtt;

import lombok.Data;

import java.time.Instant;

@Data
public class DeviceStatusPayload {

    private String deviceSn;
    private String status;
    private Integer signalStrength;
    private String firmwareVersion;
    private String errorMessage;
    private Long timestamp;

    public Instant getTimestampInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
