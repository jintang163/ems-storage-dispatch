package com.ems.domain.dto.mqtt;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.time.Instant;

/**
 * PCS储能变流器数据MQTT消息载荷
 * 接收Python采集端发送的下划线命名字段，通过@JSONField映射到Java驼峰命名
 * 字段映射关系：
 * Python(snake_case) -> Java(camelCase)
 * active_power -> activePower, work_mode -> workMode 等
 */
@Data
public class PcsDataPayload {

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
     * 直流侧电压 (V)
     */
    @JSONField(name = "dc_voltage")
    private Double dcVoltage;

    /**
     * 直流侧电流 (A)
     */
    @JSONField(name = "dc_current")
    private Double dcCurrent;

    /**
     * 直流侧功率 (kW)
     */
    @JSONField(name = "dc_power")
    private Double dcPower;

    /**
     * A相交流侧电压 (V)
     */
    @JSONField(name = "ac_voltage_a")
    private Double acVoltageA;

    /**
     * B相交流侧电压 (V)
     */
    @JSONField(name = "ac_voltage_b")
    private Double acVoltageB;

    /**
     * C相交流侧电压 (V)
     */
    @JSONField(name = "ac_voltage_c")
    private Double acVoltageC;

    /**
     * A相交流侧电流 (A)
     */
    @JSONField(name = "ac_current_a")
    private Double acCurrentA;

    /**
     * B相交流侧电流 (A)
     */
    @JSONField(name = "ac_current_b")
    private Double acCurrentB;

    /**
     * C相交流侧电流 (A)
     */
    @JSONField(name = "ac_current_c")
    private Double acCurrentC;

    /**
     * 有功功率 (kW) - 正值表示放电，负值表示充电
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
     * 电网频率 (Hz)
     */
    @JSONField(name = "frequency")
    private Double frequency;

    /**
     * 变流器效率 (%)
     */
    @JSONField(name = "efficiency")
    private Double efficiency;

    /**
     * 累计充电电量 (kWh)
     */
    @JSONField(name = "total_charge_energy")
    private Double totalChargeEnergy;

    /**
     * 累计放电电量 (kWh)
     */
    @JSONField(name = "total_discharge_energy")
    private Double totalDischargeEnergy;

    /**
     * 当日充电电量 (kWh)
     */
    @JSONField(name = "daily_charge_energy")
    private Double dailyChargeEnergy;

    /**
     * 当日放电电量 (kWh)
     */
    @JSONField(name = "daily_discharge_energy")
    private Double dailyDischargeEnergy;

    /**
     * 电网电压 (V)
     */
    @JSONField(name = "grid_voltage")
    private Double gridVoltage;

    /**
     * 电网频率 (Hz)
     */
    @JSONField(name = "grid_frequency")
    private Double gridFrequency;

    /**
     * 逆变器温度 (°C)
     */
    @JSONField(name = "inverter_temperature")
    private Double inverterTemperature;

    /**
     * 散热器温度 (°C)
     */
    @JSONField(name = "heat_sink_temperature")
    private Double heatSinkTemperature;

    /**
     * 运行状态：0-停机，1-运行，2-故障，3-告警
     */
    @JSONField(name = "running_status")
    private Integer runningStatus;

    /**
     * 工作模式：0-待机，1-充电，2-放电，3-恒压，4-恒流
     */
    @JSONField(name = "work_mode")
    private Integer workMode;

    /**
     * 控制模式：0-本地，1-远程
     */
    @JSONField(name = "control_mode")
    private Integer controlMode;

    /**
     * 有功功率设定值 (kW)
     */
    @JSONField(name = "power_setpoint")
    private Double powerSetpoint;

    /**
     * 无功功率设定值 (kVar)
     */
    @JSONField(name = "reactive_power_setpoint")
    private Double reactivePowerSetpoint;

    /**
     * 并网状态：false-离网，true-并网
     */
    @JSONField(name = "grid_connect_status")
    private Boolean gridConnectStatus;

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
     * 直流侧最大允许电压 (V)
     */
    @JSONField(name = "dc_max_voltage")
    private Double dcMaxVoltage;

    /**
     * 直流侧最小允许电压 (V)
     */
    @JSONField(name = "dc_min_voltage")
    private Double dcMinVoltage;

    /**
     * 交流侧最大允许电流 (A)
     */
    @JSONField(name = "ac_max_current")
    private Double acMaxCurrent;

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
