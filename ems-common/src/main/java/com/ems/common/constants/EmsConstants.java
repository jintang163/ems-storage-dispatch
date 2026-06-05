package com.ems.common.constants;

public class EmsConstants {

    public static final String MQTT_TOPIC_PREFIX = "ems/device/";

    public static final String METER_DATA_TOPIC = "ems/device/meter/%s/data";
    public static final String PV_DATA_TOPIC = "ems/device/pv/%s/data";
    public static final String BMS_DATA_TOPIC = "ems/device/bms/%s/data";
    public static final String PCS_DATA_TOPIC = "ems/device/pcs/%s/data";
    public static final String STATUS_TOPIC = "ems/device/%s/status";
    public static final String COMMAND_TOPIC = "ems/device/command";

    public static final String INFLUX_MEASUREMENT_METER = "meter_data";
    public static final String INFLUX_MEASUREMENT_PV = "pv_data";
    public static final String INFLUX_MEASUREMENT_BMS = "bms_data";
    public static final String INFLUX_MEASUREMENT_PCS = "pcs_data";
    public static final String INFLUX_MEASUREMENT_WEATHER = "weather_data";

    public static final String TAG_DEVICE_SN = "device_sn";
    public static final String TAG_DEVICE_TYPE = "device_type";
    public static final String TAG_LOCATION = "location";

    public static final String DEVICE_STATUS_ONLINE = "online";
    public static final String DEVICE_STATUS_OFFLINE = "offline";
    public static final String DEVICE_STATUS_FAULT = "fault";

    public static final String ALARM_STATUS_ACTIVE = "active";
    public static final String ALARM_STATUS_ACKNOWLEDGED = "acknowledged";
    public static final String ALARM_STATUS_CLEARED = "cleared";

    public static final String COMMAND_STATUS_PENDING = "pending";
    public static final String COMMAND_STATUS_SENT = "sent";
    public static final String COMMAND_STATUS_EXECUTING = "executing";
    public static final String COMMAND_STATUS_SUCCESS = "success";
    public static final String COMMAND_STATUS_FAILED = "failed";

    public static final String PRICE_PERIOD_PEAK = "peak";
    public static final String PRICE_PERIOD_FLAT = "flat";
    public static final String PRICE_PERIOD_VALLEY = "valley";

    public static final String PROTOCOL_MODBUS_TCP = "modbus-tcp";
    public static final String PROTOCOL_MODBUS_RTU = "modbus-rtu";
    public static final String PROTOCOL_MQTT = "mqtt";

    private EmsConstants() {
    }
}
