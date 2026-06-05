package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.data.DataQueryDTO;
import com.ems.domain.tsdb.*;
import com.ems.domain.vo.RealtimeDataVO;
import com.ems.service.DataQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@CrossOrigin
public class DataController {

    private final DataQueryService dataQueryService;

    @PostMapping("/meter/query")
    public Result<List<MeterData>> queryMeterData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryMeterData(queryDTO));
    }

    @PostMapping("/pv/query")
    public Result<List<PvData>> queryPvData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryPvData(queryDTO));
    }

    @PostMapping("/bms/query")
    public Result<List<BmsData>> queryBmsData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryBmsData(queryDTO));
    }

    @PostMapping("/pcs/query")
    public Result<List<PcsData>> queryPcsData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryPcsData(queryDTO));
    }

    @GetMapping("/meter/{deviceSn}/latest")
    public Result<MeterData> getLatestMeterData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestMeterData(deviceSn));
    }

    @GetMapping("/pv/{deviceSn}/latest")
    public Result<PvData> getLatestPvData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestPvData(deviceSn));
    }

    @GetMapping("/bms/{deviceSn}/latest")
    public Result<BmsData> getLatestBmsData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestBmsData(deviceSn));
    }

    @GetMapping("/pcs/{deviceSn}/latest")
    public Result<PcsData> getLatestPcsData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestPcsData(deviceSn));
    }

    @GetMapping("/realtime/{deviceType}/{deviceSn}")
    public Result<RealtimeDataVO> getRealtimeData(
            @PathVariable String deviceType,
            @PathVariable String deviceSn) {
        return Result.success(dataQueryService.getRealtimeData(deviceSn, deviceType));
    }

    @GetMapping("/realtime/all")
    public Result<List<RealtimeDataVO>> getAllRealtimeData() {
        return Result.success(dataQueryService.getAllRealtimeData());
    }
}
