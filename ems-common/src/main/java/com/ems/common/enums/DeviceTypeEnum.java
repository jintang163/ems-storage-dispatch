package com.ems.common.enums;

import lombok.Getter;

@Getter
public enum DeviceTypeEnum {

    METER("meter", "智能电表"),
    PV("pv", "光伏逆变器"),
    BMS("bms", "电池管理系统"),
    PCS("pcs", "储能变流器"),
    WEATHER("weather", "气象站");

    private final String code;
    private final String name;

    DeviceTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static DeviceTypeEnum fromCode(String code) {
        for (DeviceTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown device type code: " + code);
    }
}
