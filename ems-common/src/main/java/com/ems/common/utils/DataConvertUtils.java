package com.ems.common.utils;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.NumberUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DataConvertUtils {

    private DataConvertUtils() {
    }

    public static Double scaleValue(Number value, double scaleFactor, double offset) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value.doubleValue())
                .multiply(BigDecimal.valueOf(scaleFactor))
                .add(BigDecimal.valueOf(offset))
                .doubleValue();
    }

    public static Integer hexToInt(String hex) {
        return HexUtil.hexToInt(hex);
    }

    public static Short hexToShort(String hex) {
        return (short) HexUtil.hexToInt(hex);
    }

    public static Float intBitsToFloat(int bits) {
        return Float.intBitsToFloat(bits);
    }

    public static int floatToIntBits(float value) {
        return Float.floatToIntBits(value);
    }

    public static double round(double value, int places) {
        return NumberUtil.round(value, places).doubleValue();
    }

    public static int mergeRegisters(int high, int low) {
        return (high << 16) | (low & 0xFFFF);
    }

    public static long mergeRegistersToLong(int high, int low) {
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }
}
