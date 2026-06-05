package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.device.DeviceDTO;
import com.ems.domain.dto.device.DeviceQueryDTO;
import com.ems.domain.vo.PageResult;
import com.ems.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设备管理控制器
 * 提供设备的增删改查接口，管理EMS系统中的所有采集设备
 *
 * 设备类型说明：
 * - meter: 电表，采集园区/工厂用电负荷数据
 * - pv: 光伏逆变器，采集新能源出力数据
 * - bms: 电池管理系统，采集电池SOC、SOH、温度等状态数据
 * - pcs: 储能变流器，控制电池充放电，是储能系统的核心执行设备
 *
 * 设备数据链路：
 * 1. Python采集端通过Modbus TCP协议从设备采集数据
 * 2. 通过MQTT协议上报到EMS后端
 * 3. MqttMessageHandler解析并写入InfluxDB时序数据库
 * 4. 前端通过DataController查询实时和历史数据
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@CrossOrigin
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 创建设备
     * @param dto 设备信息
     * @return 创建后的设备
     */
    @PostMapping
    public Result<DeviceDTO> create(@Valid @RequestBody DeviceDTO dto) {
        return Result.success(deviceService.create(dto));
    }

    /**
     * 更新设备信息
     * @param id 设备ID
     * @param dto 设备信息
     * @return 更新后的设备
     */
    @PutMapping("/{id}")
    public Result<DeviceDTO> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        return Result.success(deviceService.update(id, dto));
    }

    /**
     * 删除设备
     * @param id 设备ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.success();
    }

    /**
     * 根据ID获取设备详情
     * @param id 设备ID
     * @return 设备详情
     */
    @GetMapping("/{id}")
    public Result<DeviceDTO> getById(@PathVariable Long id) {
        return Result.success(deviceService.getById(id));
    }

    /**
     * 根据设备编号获取设备详情
     * @param deviceSn 设备编号（如MTR-001, PV-001等）
     * @return 设备详情
     */
    @GetMapping("/sn/{deviceSn}")
    public Result<DeviceDTO> getByDeviceSn(@PathVariable String deviceSn) {
        return Result.success(deviceService.getByDeviceSn(deviceSn));
    }

    /**
     * 分页查询设备列表
     * @param queryDTO 查询条件（支持按设备类型、状态、关键字等筛选）
     * @return 分页结果
     */
    @PostMapping("/query")
    public Result<PageResult<DeviceDTO>> query(@RequestBody DeviceQueryDTO queryDTO) {
        return Result.success(deviceService.query(queryDTO));
    }

    /**
     * 获取所有设备列表
     * @return 设备列表
     */
    @GetMapping("/list")
    public Result<List<DeviceDTO>> listAll() {
        return Result.success(deviceService.listAll());
    }

    /**
     * 根据设备类型获取设备列表
     * @param deviceTypeId 设备类型ID
     * @return 指定类型的设备列表
     */
    @GetMapping("/type/{deviceTypeId}")
    public Result<List<DeviceDTO>> listByType(@PathVariable Long deviceTypeId) {
        return Result.success(deviceService.listByType(deviceTypeId));
    }

    /**
     * 更新设备启用状态
     * 禁用的设备将停止数据采集
     * @param id 设备ID
     * @param body 包含enabled字段
     * @return 操作结果
     */
    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        deviceService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }
}
