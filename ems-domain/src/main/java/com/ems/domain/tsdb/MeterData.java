package com.ems.domain.tsdb;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;

import java.time.Instant;

@Data
@Measurement(name = "meter_data")
public class MeterData {

    @Column(tag = true, name = "device_sn")
    private String deviceSn;

    @Column(tag = true, name = "device_type")
    private String deviceType = "meter";

    @Column(tag = true)
    private String location;

    @Column(name = "voltage_a")
    private Double voltageA;

    @Column(name = "voltage_b")
    private Double voltageB;

    @Column(name = "voltage_c")
    private Double voltageC;

    @Column(name = "current_a")
    private Double currentA;

    @Column(name = "current_b")
    private Double currentB;

    @Column(name = "current_c")
    private Double currentC;

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

    @Column(name = "total_active_energy")
    private Double totalActiveEnergy;

    @Column(name = "total_reactive_energy")
    private Double totalReactiveEnergy;

    @Column(name = "import_active_energy")
    private Double importActiveEnergy;

    @Column(name = "export_active_energy")
    private Double exportActiveEnergy;

    @Column(name = "demand")
    private Double demand;

    @Column(name = "thd_voltage_a")
    private Double thdVoltageA;

    @Column(name = "thd_current_a")
    private Double thdCurrentA;

    @Column(timestamp = true)
    private Instant timestamp;
}
