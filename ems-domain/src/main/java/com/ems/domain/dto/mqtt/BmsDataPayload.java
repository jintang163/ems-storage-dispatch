package com.ems.domain.dto.mqtt;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.time.Instant;

/**
 * BMS电池管理系统数据MQTT消息载荷
 * 接收Python采集端发送的下划线命名字段，通过@JSONField映射到Java驼峰命名
 * 字段映射关系：
 * Python(snake_case) -> Java(camelCase)
 * total_voltage -> totalVoltage, max_temperature -> maxTemperature 等
 */
@Data
public class BmsDataPayload {

    /**
     * 设备编号
     */
    @JSONField(name = "deviceSn")
    private String deviceSn;

    /**
     * 安装位置
     */
    @JSONField(name = "location")
    private String location;

    /**
     * 荷电状态 (%)
     */
    @JSONField(name = "soc")
    private Double soc;

    /**
     * 健康状态 (%)
     */
    @JSONField(name = "soh")
    private Double soh;

    /**
     * 总电压 (V)
     */
    @JSONField(name = "total_voltage")
    private Double totalVoltage;

    /**
     * 总电流 (A)
     */
    @JSONField(name = "total_current")
    private Double totalCurrent;

    /**
     * 最高单体电压 (V)
     */
    @JSONField(name = "max_cell_voltage")
    private Double maxCellVoltage;

    /**
     * 最低单体电压 (V)
     */
    @JSONField(name = "min_cell_voltage")
    private Double minCellVoltage;

    /**
     * 最高电压单体编号
     */
    @JSONField(name = "max_cell_voltage_no")
    private Integer maxCellVoltageNo;

    /**
     * 最低电压单体编号
     */
    @JSONField(name = "min_cell_voltage_no")
    private Integer minCellVoltageNo;

    /**
     * 平均单体电压 (V)
     */
    @JSONField(name = "avg_cell_voltage")
    private Double avgCellVoltage;

    /**
     * 最高温度 (°C)
     */
    @JSONField(name = "max_temperature")
    private Double maxTemperature;

    /**
     * 最低温度 (°C)
     */
    @JSONField(name = "min_temperature")
    private Double minTemperature;

    /**
     * 平均温度 (°C)
     */
    @JSONField(name = "avg_temperature")
    private Double avgTemperature;

    /**
     * 最高温度传感器编号
     */
    @JSONField(name = "max_temp_no")
    private Integer maxTempNo;

    /**
     * 最低温度传感器编号
     */
    @JSONField(name = "min_temp_no")
    private Integer minTempNo;

    /**
     * 充电电流限制 (A)
     */
    @JSONField(name = "charge_current_limit")
    private Double chargeCurrentLimit;

    /**
     * 放电电流限制 (A)
     */
    @JSONField(name = "discharge_current_limit")
    private Double dischargeCurrentLimit;

    /**
     * 最大充电功率 (kW)
     */
    @JSONField(name = "max_charge_power")
    private Double maxChargePower;

    /**
     * 最大放电功率 (kW)
     */
    @JSONField(name = "max_discharge_power")
    private Double maxDischargePower;

    /**
     * 循环次数
     */
    @JSONField(name = "cycle_count")
    private Integer cycleCount;

    /**
     * 标称容量 (Ah)
     */
    @JSONField(name = "capacity")
    private Double capacity;

    /**
     * 剩余容量 (Ah)
     */
    @JSONField(name = "remaining_capacity")
    private Double remainingCapacity;

    /**
     * 设计容量 (Ah)
     */
    @JSONField(name = "design_capacity")
    private Double designCapacity;

    /**
     * BMS运行状态
     */
    @JSONField(name = "bms_status")
    private Integer bmsStatus;

    /**
     * 充电允许标志
     */
    @JSONField(name = "charge_enable")
    private Boolean chargeEnable;

    /**
     * 放电允许标志
     */
    @JSONField(name = "discharge_enable")
    private Boolean dischargeEnable;

    /**
     * 加热允许标志
     */
    @JSONField(name = "heating_enable")
    private Boolean heatingEnable;

    /**
     * 故障代码
     */
    @JSONField(name = "fault_code")
    private Integer faultCode;

    /**
     * 告警代码
     */
    @JSONField(name = "warning_code")
    private Integer warningCode;

    /**
     * 保护代码
     */
    @JSONField(name = "protection_code")
    private Integer protectionCode;

    /**
     * 单体数量 (节)
     */
    @JSONField(name = "cell_count")
    private Integer cellCount;

    /**
     * 温度传感器数量 (个)
     */
    @JSONField(name = "temp_sensor_count")
    private Integer tempSensorCount;

    /**
     * 时间戳 (毫秒)
     */
    @JSONField(name = "timestamp")
    private Long timestamp;

    /**
     * 获取时间戳的Instant对象
     * @return Instant对象，如果时间戳为空则返回当前时间
     */
    public Instant getTimestampInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
