package com.ems.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ems.domain.dto.data.DataQueryDTO;
import com.ems.domain.entity.Device;
import com.ems.domain.tsdb.*;
import com.ems.domain.vo.RealtimeDataVO;
import com.ems.influxdb.service.RealtimeDataCache;
import com.ems.influxdb.service.TimeSeriesService;
import com.ems.repository.DeviceRepository;
import com.ems.service.DataQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryServiceImpl implements DataQueryService {

    private final TimeSeriesService timeSeriesService;
    private final RealtimeDataCache realtimeDataCache;
    private final DeviceRepository deviceRepository;

    @Override
    public List<MeterData> queryMeterData(DataQueryDTO queryDTO) {
        queryDTO.setDeviceType("meter");
        return timeSeriesService.queryData(queryDTO, MeterData.class);
    }

    @Override
    public List<PvData> queryPvData(DataQueryDTO queryDTO) {
        queryDTO.setDeviceType("pv");
        return timeSeriesService.queryData(queryDTO, PvData.class);
    }

    @Override
    public List<BmsData> queryBmsData(DataQueryDTO queryDTO) {
        queryDTO.setDeviceType("bms");
        return timeSeriesService.queryData(queryDTO, BmsData.class);
    }

    @Override
    public List<PcsData> queryPcsData(DataQueryDTO queryDTO) {
        queryDTO.setDeviceType("pcs");
        return timeSeriesService.queryData(queryDTO, PcsData.class);
    }

    @Override
    public MeterData getLatestMeterData(String deviceSn) {
        MeterData cached = realtimeDataCache.getMeterData(deviceSn);
        if (cached != null) {
            return cached;
        }
        return timeSeriesService.queryLatestData(deviceSn, "meter", MeterData.class);
    }

    @Override
    public PvData getLatestPvData(String deviceSn) {
        PvData cached = realtimeDataCache.getPvData(deviceSn);
        if (cached != null) {
            return cached;
        }
        return timeSeriesService.queryLatestData(deviceSn, "pv", PvData.class);
    }

    @Override
    public BmsData getLatestBmsData(String deviceSn) {
        BmsData cached = realtimeDataCache.getBmsData(deviceSn);
        if (cached != null) {
            return cached;
        }
        return timeSeriesService.queryLatestData(deviceSn, "bms", BmsData.class);
    }

    @Override
    public PcsData getLatestPcsData(String deviceSn) {
        PcsData cached = realtimeDataCache.getPcsData(deviceSn);
        if (cached != null) {
            return cached;
        }
        return timeSeriesService.queryLatestData(deviceSn, "pcs", PcsData.class);
    }

    @Override
    public RealtimeDataVO getRealtimeData(String deviceSn, String deviceType) {
        Object data = null;
        switch (deviceType) {
            case "meter":
                data = getLatestMeterData(deviceSn);
                break;
            case "pv":
                data = getLatestPvData(deviceSn);
                break;
            case "bms":
                data = getLatestBmsData(deviceSn);
                break;
            case "pcs":
                data = getLatestPcsData(deviceSn);
                break;
            default:
                return null;
        }

        if (data == null) {
            return null;
        }

        RealtimeDataVO vo = new RealtimeDataVO();
        vo.setDeviceSn(deviceSn);
        vo.setDeviceType(deviceType);

        Map<String, Object> dataMap = JSON.parseObject(JSON.toJSONString(data), Map.class);
        vo.setData(dataMap);

        if (data instanceof MeterData) {
            vo.setLocation(((MeterData) data).getLocation());
            vo.setTimestamp(((MeterData) data).getTimestamp());
        } else if (data instanceof PvData) {
            vo.setLocation(((PvData) data).getLocation());
            vo.setTimestamp(((PvData) data).getTimestamp());
        } else if (data instanceof BmsData) {
            vo.setLocation(((BmsData) data).getLocation());
            vo.setTimestamp(((BmsData) data).getTimestamp());
        } else if (data instanceof PcsData) {
            vo.setLocation(((PcsData) data).getLocation());
            vo.setTimestamp(((PcsData) data).getTimestamp());
        }

        return vo;
    }

    @Override
    public List<RealtimeDataVO> getAllRealtimeData() {
        List<RealtimeDataVO> result = new ArrayList<>();
        List<Device> devices = deviceRepository.findByEnabledTrue();

        for (Device device : devices) {
            String deviceType = device.getDeviceType() != null ? device.getDeviceType().getCode() : null;
            if (deviceType != null) {
                RealtimeDataVO data = getRealtimeData(device.getDeviceSn(), deviceType);
                if (data != null) {
                    result.add(data);
                }
            }
        }

        return result;
    }
}
