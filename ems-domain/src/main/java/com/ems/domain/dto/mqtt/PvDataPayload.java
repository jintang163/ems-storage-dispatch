package com.ems.domain.dto.mqtt;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.time.Instant;

/**
 * 光伏逆变器数据MQTT消息载荷
 * 接收Python采集端发送的下划线命名字段，通过@JSONField映射到Java驼峰命名
 * 字段映射关系：
 * Python(snake_case) -> Java(camelCase)
 * dc_voltage -> dcVoltage, module_temperature -> moduleTemperature 等
 */
@Data
public class PvDataPayload {

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
     * 直流输入电压 (V)
     */
    @JSONField(name = "dc_voltage")
    private Double dcVoltage;

    /**
     * 直流输入电流 (A)
     */
    @JSONField(name = "dc_current")
    private Double dcCurrent;

    /**
     * 直流输入功率 (kW)
     */
    @JSONField(name = "dc_power")
    private Double dcPower;

    /**
     * A相交流输出电压 (V)
     */
    @JSONField(name = "ac_voltage_a")
    private Double acVoltageA;

    /**
     * B相交流输出电压 (V)
     */
    @JSONField(name = "ac_voltage_b")
    private Double acVoltageB;

    /**
     * C相交流输出电压 (V)
     */
    @JSONField(name = "ac_voltage_c")
    private Double acVoltageC;

    /**
     * A相交流输出电流 (A)
     */
    @JSONField(name = "ac_current_a")
    private Double acCurrentA;

    /**
     * B相交流输出电流 (A)
     */
    @JSONField(name = "ac_current_b")
    private Double acCurrentB;

    /**
     * C相交流输出电流 (A)
     */
    @JSONField(name = "ac_current_c")
    private Double acCurrentC;

    /**
     * 交流输出功率 (kW)
     */
    @JSONField(name = "ac_power")
    private Double acPower;

    /**
     * 交流无功功率 (kVar)
     */
    @JSONField(name = "ac_reactive_power")
    private Double acReactivePower;

    /**
     * 功率因数
     */
    @JSONField(name = "power_factor")
    private Double powerFactor;

    /**
     * 电网频率 (Hz)
     */
    @JSONField(name = "frequency")
    private Double frequency;

    /**
     * 逆变器效率 (%)
     */
    @JSONField(name = "efficiency")
    private Double efficiency;

    /**
     * 累计发电量 (kWh)
     */
    @JSONField(name = "total_energy")
    private Double totalEnergy;

    /**
     * 当日发电量 (kWh)
     */
    @JSONField(name = "daily_energy")
    private Double dailyEnergy;

    /**
     * 组件温度 (°C)
     */
    @JSONField(name = "module_temperature")
    private Double moduleTemperature;

    /**
     * 环境温度 (°C)
     */
    @JSONField(name = "ambient_temperature")
    private Double ambientTemperature;

    /**
     * 辐照度 (W/m²)
     */
    @JSONField(name = "irradiance")
    private Double irradiance;

    /**
     * 运行状态：0-停机，1-运行，2-故障
     */
    @JSONField(name = "operating_status")
    private Integer operatingStatus;

    /**
     * 故障代码
     */
    @JSONField(name = "fault_code")
    private Integer faultCode;

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
