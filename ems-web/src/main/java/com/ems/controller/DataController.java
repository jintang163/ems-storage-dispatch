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

/**
 * 数据查询控制器
 * 提供时序数据的查询接口，包括历史数据查询和实时数据查询
 *
 * 数据来源：
 * Python采集端 -> MQTT -> MqttMessageHandler -> InfluxDB时序数据库 -> DataController -> 前端展示
 *
 * 查询接口分类：
 * 1. 历史数据查询：按时间范围查询，用于生成趋势图表
 * 2. 最新数据查询：获取设备的最后一条数据，用于实时展示
 * 3. 实时数据查询：从缓存中获取最新数据，性能更高
 *
 * InfluxDB vs PostgreSQL分工：
 * - InfluxDB：存储高频时序数据（电表、光伏、BMS、PCS的实时采集数据）
 * - PostgreSQL：存储关系型数据（设备信息、电价配置、用户信息等）
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@CrossOrigin
public class DataController {

    private final DataQueryService dataQueryService;

    /**
     * 查询电表历史数据
     * @param queryDTO 查询条件（时间范围、聚合方式等）
     * @return 电表数据列表
     */
    @PostMapping("/meter/query")
    public Result<List<MeterData>> queryMeterData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryMeterData(queryDTO));
    }

    /**
     * 查询光伏逆变器历史数据
     * @param queryDTO 查询条件
     * @return 光伏数据列表
     */
    @PostMapping("/pv/query")
    public Result<List<PvData>> queryPvData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryPvData(queryDTO));
    }

    /**
     * 查询BMS电池管理系统历史数据
     * @param queryDTO 查询条件
     * @return BMS数据列表
     */
    @PostMapping("/bms/query")
    public Result<List<BmsData>> queryBmsData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryBmsData(queryDTO));
    }

    /**
     * 查询PCS储能变流器历史数据
     * @param queryDTO 查询条件
     * @return PCS数据列表
     */
    @PostMapping("/pcs/query")
    public Result<List<PcsData>> queryPcsData(@Valid @RequestBody DataQueryDTO queryDTO) {
        return Result.success(dataQueryService.queryPcsData(queryDTO));
    }

    /**
     * 获取电表最新数据
     * 从InfluxDB查询该设备最近一条记录
     * @param deviceSn 设备编号
     * @return 电表最新数据
     */
    @GetMapping("/meter/{deviceSn}/latest")
    public Result<MeterData> getLatestMeterData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestMeterData(deviceSn));
    }

    /**
     * 获取光伏逆变器最新数据
     * @param deviceSn 设备编号
     * @return 光伏最新数据
     */
    @GetMapping("/pv/{deviceSn}/latest")
    public Result<PvData> getLatestPvData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestPvData(deviceSn));
    }

    /**
     * 获取BMS最新数据
     * @param deviceSn 设备编号
     * @return BMS最新数据
     */
    @GetMapping("/bms/{deviceSn}/latest")
    public Result<BmsData> getLatestBmsData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestBmsData(deviceSn));
    }

    /**
     * 获取PCS最新数据
     * @param deviceSn 设备编号
     * @return PCS最新数据
     */
    @GetMapping("/pcs/{deviceSn}/latest")
    public Result<PcsData> getLatestPcsData(@PathVariable String deviceSn) {
        return Result.success(dataQueryService.getLatestPcsData(deviceSn));
    }

    /**
     * 获取单设备实时数据
     * 从内存缓存中获取，性能更高，适合实时监控场景
     * @param deviceType 设备类型（meter/pv/bms/pcs）
     * @param deviceSn 设备编号
     * @return 设备实时数据
     */
    @GetMapping("/realtime/{deviceType}/{deviceSn}")
    public Result<RealtimeDataVO> getRealtimeData(
            @PathVariable String deviceType,
            @PathVariable String deviceSn) {
        return Result.success(dataQueryService.getRealtimeData(deviceSn, deviceType));
    }

    /**
     * 获取所有设备实时数据
     * Dashboard页面调用，获取所有在线设备的实时数据
     * @return 所有设备实时数据列表
     */
    @GetMapping("/realtime/all")
    public Result<List<RealtimeDataVO>> getAllRealtimeData() {
        return Result.success(dataQueryService.getAllRealtimeData());
    }
}
