package com.ems.domain.tsdb;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;

import java.time.Instant;

@Data
@Measurement(name = "bms_data")
public class BmsData {

    @Column(tag = true, name = "device_sn")
    private String deviceSn;

    @Column(tag = true, name = "device_type")
    private String deviceType = "bms";

    @Column(tag = true)
    private String location;

    @Column(name = "soc")
    private Double soc;

    @Column(name = "soh")
    private Double soh;

    @Column(name = "total_voltage")
    private Double totalVoltage;

    @Column(name = "total_current")
    private Double totalCurrent;

    @Column(name = "max_cell_voltage")
    private Double maxCellVoltage;

    @Column(name = "min_cell_voltage")
    private Double minCellVoltage;

    @Column(name = "max_cell_voltage_no")
    private Integer maxCellVoltageNo;

    @Column(name = "min_cell_voltage_no")
    private Integer minCellVoltageNo;

    @Column(name = "avg_cell_voltage")
    private Double avgCellVoltage;

    @Column(name = "max_temperature")
    private Double maxTemperature;

    @Column(name = "min_temperature")
    private Double minTemperature;

    @Column(name = "avg_temperature")
    private Double avgTemperature;

    @Column(name = "max_temp_no")
    private Integer maxTempNo;

    @Column(name = "min_temp_no")
    private Integer minTempNo;

    @Column(name = "charge_current_limit")
    private Double chargeCurrentLimit;

    @Column(name = "discharge_current_limit")
    private Double dischargeCurrentLimit;

    @Column(name = "max_charge_power")
    private Double maxChargePower;

    @Column(name = "max_discharge_power")
    private Double maxDischargePower;

    @Column(name = "cycle_count")
    private Integer cycleCount;

    @Column(name = "capacity")
    private Double capacity;

    @Column(name = "remaining_capacity")
    private Double remainingCapacity;

    @Column(name = "design_capacity")
    private Double designCapacity;

    @Column(name = "bms_status")
    private Integer bmsStatus;

    @Column(name = "charge_enable")
    private Boolean chargeEnable;

    @Column(name = "discharge_enable")
    private Boolean dischargeEnable;

    @Column(name = "heating_enable")
    private Boolean heatingEnable;

    @Column(name = "fault_code")
    private Integer faultCode;

    @Column(name = "warning_code")
    private Integer warningCode;

    @Column(name = "protection_code")
    private Integer protectionCode;

    @Column(name = "cell_count")
    private Integer cellCount;

    @Column(name = "temp_sensor_count")
    private Integer tempSensorCount;

    @Column(timestamp = true)
    private Instant timestamp;
}
