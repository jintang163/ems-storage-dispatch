package com.ems.influxdb.service;

import com.ems.domain.dto.data.DataQueryDTO;
import com.ems.domain.tsdb.*;

import java.util.List;

public interface TimeSeriesService {

    void writeMeterData(MeterData data);

    void writeMeterDataAsync(MeterData data);

    void writePvData(PvData data);

    void writePvDataAsync(PvData data);

    void writeBmsData(BmsData data);

    void writeBmsDataAsync(BmsData data);

    void writePcsData(PcsData data);

    void writePcsDataAsync(PcsData data);

    <T> List<T> queryData(DataQueryDTO queryDTO, Class<T> clazz);

    <T> List<T> queryLatestData(String deviceSn, String deviceType, int limit, Class<T> clazz);

    <T> T queryLatestData(String deviceSn, String deviceType, Class<T> clazz);

    void deleteData(String deviceSn, String deviceType, long startTime, long endTime);

    void flush();
}
