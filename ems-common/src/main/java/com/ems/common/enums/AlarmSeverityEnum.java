package com.ems.common.enums;

import lombok.Getter;

@Getter
public enum AlarmSeverityEnum {

    INFO("info", "信息"),
    WARNING("warning", "警告"),
    ERROR("error", "错误"),
    CRITICAL("critical", "严重");

    private final String code;
    private final String name;

    AlarmSeverityEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
