package com.ems.domain.dto.data;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public class DataQueryDTO {

    @NotBlank(message = "设备编号不能为空")
    private String deviceSn;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    private String measurement;

    private String[] fields;

    private Instant startTime;

    private Instant endTime;

    private String aggregate;

    private String every;

    private Integer limit;
}
