package com.ems.domain.dto.mqtt;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.time.Instant;

/**
 * 电表数据MQTT消息载荷
 * 接收Python采集端发送的下划线命名字段，通过@JSONField映射到Java驼峰命名
 * 字段映射关系：
 * Python(snake_case) -> Java(camelCase)
 * voltage_a -> voltageA, active_power -> activePower 等
 */
@Data
public class MeterDataPayload {

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
     * A相电压 (V)
     */
    @JSONField(name = "voltage_a")
    private Double voltageA;

    /**
     * B相电压 (V)
     */
    @JSONField(name = "voltage_b")
    private Double voltageB;

    /**
     * C相电压 (V)
     */
    @JSONField(name = "voltage_c")
    private Double voltageC;

    /**
     * A相电流 (A)
     */
    @JSONField(name = "current_a")
    private Double currentA;

    /**
     * B相电流 (A)
     */
    @JSONField(name = "current_b")
    private Double currentB;

    /**
     * C相电流 (A)
     */
    @JSONField(name = "current_c")
    private Double currentC;

    /**
     * 有功功率 (kW)
     */
    @JSONField(name = "active_power")
    private Double activePower;

    /**
     * 无功功率 (kVar)
     */
    @JSONField(name = "reactive_power")
    private Double reactivePower;

    /**
     * 视在功率 (kVA)
     */
    @JSONField(name = "apparent_power")
    private Double apparentPower;

    /**
     * 功率因数
     */
    @JSONField(name = "power_factor")
    private Double powerFactor;

    /**
     * 频率 (Hz)
     */
    @JSONField(name = "frequency")
    private Double frequency;

    /**
     * 总有功电能 (kWh)
     */
    @JSONField(name = "total_active_energy")
    private Double totalActiveEnergy;

    /**
     * 总无功电能 (kVarh)
     */
    @JSONField(name = "total_reactive_energy")
    private Double totalReactiveEnergy;

    /**
     * 正向有功电能 (kWh) - 从电网购买的电量
     */
    @JSONField(name = "import_active_energy")
    private Double importActiveEnergy;

    /**
     * 反向有功电能 (kWh) - 向电网输送的电量
     */
    @JSONField(name = "export_active_energy")
    private Double exportActiveEnergy;

    /**
     * 需量 (kW)
     */
    @JSONField(name = "demand")
    private Double demand;

    /**
     * A相电压总谐波畸变率 (%)
     */
    @JSONField(name = "thd_voltage_a")
    private Double thdVoltageA;

    /**
     * A相电流总谐波畸变率 (%)
     */
    @JSONField(name = "thd_current_a")
    private Double thdCurrentA;

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
