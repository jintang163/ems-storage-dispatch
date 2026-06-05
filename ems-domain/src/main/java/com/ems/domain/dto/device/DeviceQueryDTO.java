package com.ems.domain.dto.device;

import lombok.Data;

@Data
public class DeviceQueryDTO {

    private String deviceSn;

    private String name;

    private Long deviceTypeId;

    private String protocol;

    private String status;

    private String location;

    private Boolean enabled;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
