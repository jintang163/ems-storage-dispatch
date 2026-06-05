package com.ems.influxdb.service.impl;

import com.ems.common.constants.EmsConstants;
import com.ems.common.exception.EmsException;
import com.ems.domain.dto.data.DataQueryDTO;
import com.ems.domain.tsdb.*;
import com.ems.influxdb.config.InfluxDBConfig;
import com.ems.influxdb.service.TimeSeriesService;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSeriesServiceImpl implements TimeSeriesService {

    private final InfluxDBClient influxDBClient;
    private final WriteApiBlocking writeApiBlocking;
    private final WriteApi writeApi;
    private final InfluxDBConfig influxDBConfig;

    private static final Map<String, String> MEASUREMENT_MAP = new HashMap<>();

    static {
        MEASUREMENT_MAP.put("meter", EmsConstants.INFLUX_MEASUREMENT_METER);
        MEASUREMENT_MAP.put("pv", EmsConstants.INFLUX_MEASUREMENT_PV);
        MEASUREMENT_MAP.put("bms", EmsConstants.INFLUX_MEASUREMENT_BMS);
        MEASUREMENT_MAP.put("pcs", EmsConstants.INFLUX_MEASUREMENT_PCS);
        MEASUREMENT_MAP.put("weather", EmsConstants.INFLUX_MEASUREMENT_WEATHER);
    }

    @Override
    public void writeMeterData(MeterData data) {
        try {
            writeApiBlocking.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to write meter data for device: {}", data.getDeviceSn(), e);
            throw new EmsException("Failed to write meter data", e);
        }
    }

    @Override
    public void writeMeterDataAsync(MeterData data) {
        try {
            writeApi.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to async write meter data for device: {}", data.getDeviceSn(), e);
        }
    }

    @Override
    public void writePvData(PvData data) {
        try {
            writeApiBlocking.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to write pv data for device: {}", data.getDeviceSn(), e);
            throw new EmsException("Failed to write pv data", e);
        }
    }

    @Override
    public void writePvDataAsync(PvData data) {
        try {
            writeApi.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to async write pv data for device: {}", data.getDeviceSn(), e);
        }
    }

    @Override
    public void writeBmsData(BmsData data) {
        try {
            writeApiBlocking.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to write bms data for device: {}", data.getDeviceSn(), e);
            throw new EmsException("Failed to write bms data", e);
        }
    }

    @Override
    public void writeBmsDataAsync(BmsData data) {
        try {
            writeApi.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to async write bms data for device: {}", data.getDeviceSn(), e);
        }
    }

    @Override
    public void writePcsData(PcsData data) {
        try {
            writeApiBlocking.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to write pcs data for device: {}", data.getDeviceSn(), e);
            throw new EmsException("Failed to write pcs data", e);
        }
    }

    @Override
    public void writePcsDataAsync(PcsData data) {
        try {
            writeApi.writeMeasurement(influxDBConfig.getWritePrecision(), data);
        } catch (Exception e) {
            log.error("Failed to async write pcs data for device: {}", data.getDeviceSn(), e);
        }
    }

    @Override
    public <T> List<T> queryData(DataQueryDTO queryDTO, Class<T> clazz) {
        String measurement = queryDTO.getMeasurement();
        if (measurement == null) {
            measurement = MEASUREMENT_MAP.get(queryDTO.getDeviceType());
        }
        if (measurement == null) {
            throw new EmsException("Unknown device type: " + queryDTO.getDeviceType());
        }

        StringBuilder fluxQuery = new StringBuilder();
        fluxQuery.append(String.format("from(bucket: \"%s\")", influxDBConfig.getBucket()));

        Instant startTime = queryDTO.getStartTime() != null ? queryDTO.getStartTime() :
                Instant.now().minus(1, ChronoUnit.HOURS);
        Instant endTime = queryDTO.getEndTime() != null ? queryDTO.getEndTime() : Instant.now();

        fluxQuery.append(String.format("|> range(start: %d, stop: %d)",
                startTime.getEpochSecond(), endTime.getEpochSecond()));
        fluxQuery.append(String.format("|> filter(fn: (r) => r._measurement == \"%s\")", measurement));
        fluxQuery.append(String.format("|> filter(fn: (r) => r.device_sn == \"%s\")", queryDTO.getDeviceSn()));

        if (queryDTO.getFields() != null && queryDTO.getFields().length > 0) {
            StringBuilder fieldFilter = new StringBuilder("|> filter(fn: (r) => ");
            for (int i = 0; i < queryDTO.getFields().length; i++) {
                if (i > 0) {
                    fieldFilter.append(" or ");
                }
                fieldFilter.append(String.format("r._field == \"%s\"", queryDTO.getFields()[i]));
            }
            fieldFilter.append(")");
            fluxQuery.append(fieldFilter);
        }

        if (queryDTO.getAggregate() != null && queryDTO.getEvery() != null) {
            fluxQuery.append(String.format("|> %s(every: %s)", queryDTO.getAggregate(), queryDTO.getEvery()));
        }

        if (queryDTO.getLimit() != null) {
            fluxQuery.append(String.format("|> limit(n: %d)", queryDTO.getLimit()));
        }

        QueryApi queryApi = influxDBClient.getQueryApi();
        return queryApi.query(fluxQuery.toString(), clazz);
    }

    @Override
    public <T> List<T> queryLatestData(String deviceSn, String deviceType, int limit, Class<T> clazz) {
        String measurement = MEASUREMENT_MAP.get(deviceType);
        if (measurement == null) {
            throw new EmsException("Unknown device type: " + deviceType);
        }

        String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -1h) " +
                        "|> filter(fn: (r) => r._measurement == \"%s\") " +
                        "|> filter(fn: (r) => r.device_sn == \"%s\") " +
                        "|> sort(columns: [\"_time\"], desc: true) " +
                        "|> limit(n: %d)",
                influxDBConfig.getBucket(), measurement, deviceSn, limit
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        return queryApi.query(fluxQuery, clazz);
    }

    @Override
    public <T> T queryLatestData(String deviceSn, String deviceType, Class<T> clazz) {
        List<T> data = queryLatestData(deviceSn, deviceType, 1, clazz);
        return data.isEmpty() ? null : data.get(0);
    }

    @Override
    public void deleteData(String deviceSn, String deviceType, long startTime, long endTime) {
        String measurement = MEASUREMENT_MAP.get(deviceType);
        if (measurement == null) {
            throw new EmsException("Unknown device type: " + deviceType);
        }

        try {
            influxDBClient.getDeleteApi().delete(
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime),
                    String.format("_measurement=\"%s\" AND device_sn=\"%s\"", measurement, deviceSn),
                    influxDBConfig.getBucket(),
                    influxDBConfig.getOrg()
            );
            log.info("Deleted data for device: {}, type: {}, from: {}, to: {}",
                    deviceSn, deviceType, startTime, endTime);
        } catch (Exception e) {
            log.error("Failed to delete data for device: {}", deviceSn, e);
            throw new EmsException("Failed to delete data", e);
        }
    }

    @Override
    public void flush() {
        try {
            writeApi.flush();
        } catch (Exception e) {
            log.error("Failed to flush write buffer", e);
        }
    }
}
