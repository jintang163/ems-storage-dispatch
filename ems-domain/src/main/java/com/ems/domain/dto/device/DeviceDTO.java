package com.ems.domain.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DeviceDTO {

    private Long id;

    @NotBlank(message = "设备编号不能为空")
    private String deviceSn;

    @NotNull(message = "设备类型不能为空")
    private Long deviceTypeId;

    private String deviceTypeName;

    @NotBlank(message = "设备名称不能为空")
    private String name;

    private String protocol = "modbus";

    private String host;

    private Integer port;

    private Integer slaveId;

    private String location;

    private String status;

    private Integer samplingInterval = 5000;

    private Boolean enabled = true;

    private Map<String, Object> config;

    private String description;

    private LocalDateTime lastOnlineAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
