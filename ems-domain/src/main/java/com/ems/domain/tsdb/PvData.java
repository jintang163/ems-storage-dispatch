package com.ems.domain.tsdb;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;

import java.time.Instant;

@Data
@Measurement(name = "pv_data")
public class PvData {

    @Column(tag = true, name = "device_sn")
    private String deviceSn;

    @Column(tag = true, name = "device_type")
    private String deviceType = "pv";

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

    @Column(name = "ac_power")
    private Double acPower;

    @Column(name = "ac_reactive_power")
    private Double acReactivePower;

    @Column(name = "power_factor")
    private Double powerFactor;

    @Column(name = "frequency")
    private Double frequency;

    @Column(name = "efficiency")
    private Double efficiency;

    @Column(name = "total_energy")
    private Double totalEnergy;

    @Column(name = "daily_energy")
    private Double dailyEnergy;

    @Column(name = "module_temperature")
    private Double moduleTemperature;

    @Column(name = "ambient_temperature")
    private Double ambientTemperature;

    @Column(name = "irradiance")
    private Double irradiance;

    @Column(name = "operating_status")
    private Integer operatingStatus;

    @Column(name = "fault_code")
    private Integer faultCode;

    @Column(timestamp = true)
    private Instant timestamp;
}
