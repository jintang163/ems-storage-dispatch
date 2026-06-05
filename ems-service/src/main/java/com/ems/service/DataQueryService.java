package com.ems.service;

import com.ems.domain.dto.data.DataQueryDTO;
import com.ems.domain.tsdb.*;
import com.ems.domain.vo.RealtimeDataVO;

import java.util.List;

public interface DataQueryService {

    List<MeterData> queryMeterData(DataQueryDTO queryDTO);

    List<PvData> queryPvData(DataQueryDTO queryDTO);

    List<BmsData> queryBmsData(DataQueryDTO queryDTO);

    List<PcsData> queryPcsData(DataQueryDTO queryDTO);

    MeterData getLatestMeterData(String deviceSn);

    PvData getLatestPvData(String deviceSn);

    BmsData getLatestBmsData(String deviceSn);

    PcsData getLatestPcsData(String deviceSn);

    RealtimeDataVO getRealtimeData(String deviceSn, String deviceType);

    List<RealtimeDataVO> getAllRealtimeData();
}
