package com.ems.domain.tsdb;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;

import java.time.Instant;

@Data
@Measurement(name = "pcs_data")
public class PcsData {

    @Column(tag = true, name = "device_sn")
    private String deviceSn;

    @Column(tag = true, name = "device_type")
    private String deviceType = "pcs";

    @Column(tag = true)
    private String location;

    @Column(name = "dc_voltage")
    private Double dcVoltage;

    @Column(name = "dc_current")
    private Double dcCurrent;

    @Column(name = "dc_power")
    private Double dcPower;

    @Column(name = "ac_voltage_a")
    private Double acVoltageA;

    @Column(name = "ac_voltage_b")
    private Double acVoltageB;

    @Column(name = "ac_voltage_c")
    private Double acVoltageC;

    @Column(name = "ac_current_a")
    private Double acCurrentA;

    @Column(name = "ac_current_b")
    private Double acCurrentB;

    @Column(name = "ac_current_c")
    private Double acCurrentC;

    @Column(name = "active_power")
    private Double activePower;

    @Column(name = "reactive_power")
    private Double reactivePower;

    @Column(name = "apparent_power")
    private Double apparentPower;

    @Column(name = "power_factor")
    private Double powerFactor;

    @Column(name = "frequency")
    private Double frequency;

    @Column(name = "efficiency")
    private Double efficiency;

    @Column(name = "total_charge_energy")
    private Double totalChargeEnergy;

    @Column(name = "total_discharge_energy")
    private Double totalDischargeEnergy;

    @Column(name = "daily_charge_energy")
    private Double dailyChargeEnergy;

    @Column(name = "daily_discharge_energy")
    private Double dailyDischargeEnergy;

    @Column(name = "grid_voltage")
    private Double gridVoltage;

    @Column(name = "grid_frequency")
    private Double gridFrequency;

    @Column(name = "inverter_temperature")
    private Double inverterTemperature;

    @Column(name = "heat_sink_temperature")
    private Double heatSinkTemperature;

    @Column(name = "running_status")
    private Integer runningStatus;

    @Column(name = "work_mode")
    private Integer workMode;

    @Column(name = "control_mode")
    private Integer controlMode;

    @Column(name = "power_setpoint")
    private Double powerSetpoint;

    @Column(name = "reactive_power_setpoint")
    private Double reactivePowerSetpoint;

    @Column(name = "grid_connect_status")
    private Boolean gridConnectStatus;

    @Column(name = "fault_code")
    private Integer faultCode;

    @Column(name = "warning_code")
    private Integer warningCode;

    @Column(name = "dc_max_voltage")
    private Double dcMaxVoltage;

    @Column(name = "dc_min_voltage")
    private Double dcMinVoltage;

    @Column(name = "ac_max_current")
    private Double acMaxCurrent;

    @Column(timestamp = true)
    private Instant timestamp;
}
