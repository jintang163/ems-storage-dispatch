package com.ems.common.utils;

import com.ems.common.constants.EmsConstants;

public class TopicUtils {

    private TopicUtils() {
    }

    public static String getMeterDataTopic(String deviceSn) {
        return String.format(EmsConstants.METER_DATA_TOPIC, deviceSn);
    }

    public static String getPvDataTopic(String deviceSn) {
        return String.format(EmsConstants.PV_DATA_TOPIC, deviceSn);
    }

    public static String getBmsDataTopic(String deviceSn) {
        return String.format(EmsConstants.BMS_DATA_TOPIC, deviceSn);
    }

    public static String getPcsDataTopic(String deviceSn) {
        return String.format(EmsConstants.PCS_DATA_TOPIC, deviceSn);
    }

    public static String getStatusTopic(String deviceSn) {
        return String.format(EmsConstants.STATUS_TOPIC, deviceSn);
    }

    public static String extractDeviceSnFromTopic(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return null;
    }

    public static String extractDeviceTypeFromTopic(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
}
