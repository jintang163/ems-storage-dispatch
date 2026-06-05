package com.ems.domain.vo;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class RealtimeDataVO {

    private String deviceSn;
    private String deviceType;
    private String location;
    private Map<String, Object> data;
    private Instant timestamp;
}
