package com.ems.common.enums;

import lombok.Getter;

@Getter
public enum DataTypeEnum {

    INT16("int16", Short.class),
    UINT16("uint16", Integer.class),
    INT32("int32", Integer.class),
    UINT32("uint32", Long.class),
    FLOAT32("float32", Float.class),
    FLOAT64("float64", Double.class),
    BOOLEAN("boolean", Boolean.class),
    STRING("string", String.class);

    private final String code;
    private final Class<?> javaType;

    DataTypeEnum(String code, Class<?> javaType) {
        this.code = code;
        this.javaType = javaType;
    }
}
