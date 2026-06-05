package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.battery.BatteryConfigDTO;
import com.ems.service.BatteryConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 电池参数配置控制器
 * 提供电池参数配置的REST API接口
 *
 * 核心功能：
 * 1. 电池基本参数配置：容量、功率、充放电效率
 * 2. SOC运行范围配置：避免过充过放，保护电池寿命
 * 3. 温度范围配置：电池安全工作温度区间
 * 4. 安全约束计算：最大充放电功率、有效容量、可用电量
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/battery/config")
@RequiredArgsConstructor
@CrossOrigin
public class BatteryConfigController {

    private final BatteryConfigService batteryConfigService;

    /**
     * 创建电池参数配置
     * @param dto 电池参数配置
     * @return 创建后的配置
     */
    @PostMapping
    public Result<BatteryConfigDTO> create(@Valid @RequestBody BatteryConfigDTO dto) {
        return Result.success(batteryConfigService.create(dto));
    }

    /**
     * 更新电池参数配置
     * @param id 配置ID
     * @param dto 电池参数配置
     * @return 更新后的配置
     */
    @PutMapping("/{id}")
    public Result<BatteryConfigDTO> update(@PathVariable Long id, @Valid @RequestBody BatteryConfigDTO dto) {
        return Result.success(batteryConfigService.update(id, dto));
    }

    /**
     * 删除电池参数配置
     * @param id 配置ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        batteryConfigService.delete(id);
        return Result.success();
    }

    /**
     * 根据ID获取电池参数配置
     * @param id 配置ID
     * @return 电池参数配置
     */
    @GetMapping("/{id}")
    public Result<BatteryConfigDTO> getById(@PathVariable Long id) {
        return Result.success(batteryConfigService.getById(id));
    }

    /**
     * 根据设备编号获取电池参数配置
     * @param deviceSn 设备编号
     * @return 电池参数配置
     */
    @GetMapping("/device/{deviceSn}")
    public Result<BatteryConfigDTO> getByDeviceSn(@PathVariable String deviceSn) {
        return Result.success(batteryConfigService.getByDeviceSn(deviceSn));
    }

    /**
     * 获取所有电池参数配置列表
     * @return 配置列表
     */
    @GetMapping("/list")
    public Result<List<BatteryConfigDTO>> listAll() {
        return Result.success(batteryConfigService.listAll());
    }

    /**
     * 获取启用的电池参数配置列表
     * @return 启用的配置列表
     */
    @GetMapping("/enabled")
    public Result<List<BatteryConfigDTO>> listEnabled() {
        return Result.success(batteryConfigService.listEnabled());
    }

    /**
     * 根据电池类型获取配置列表
     * @param batteryType 电池类型（LFP、NMC、LTO等）
     * @return 配置列表
     */
    @GetMapping("/type/{batteryType}")
    public Result<List<BatteryConfigDTO>> listByBatteryType(@PathVariable String batteryType) {
        return Result.success(batteryConfigService.listByBatteryType(batteryType));
    }

    /**
     * 更新配置启用状态
     * @param id 配置ID
     * @param body 包含enabled字段
     * @return 操作结果
     */
    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        batteryConfigService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }

    /**
     * 校验SOC是否在安全范围内
     * @param deviceSn 设备编号
     * @param soc 当前SOC值（0-100）
     * @return 是否在安全范围内
     */
    @GetMapping("/check/soc")
    public Result<Boolean> checkSocSafe(@RequestParam String deviceSn, @RequestParam BigDecimal soc) {
        return Result.success(batteryConfigService.isSocWithinSafeRange(deviceSn, soc));
    }

    /**
     * 校验温度是否在安全范围内
     * @param deviceSn 设备编号
     * @param temperature 当前温度（℃）
     * @return 是否在安全范围内
     */
    @GetMapping("/check/temperature")
    public Result<Boolean> checkTemperatureSafe(@RequestParam String deviceSn, @RequestParam BigDecimal temperature) {
        return Result.success(batteryConfigService.isTemperatureWithinSafeRange(deviceSn, temperature));
    }

    /**
     * 计算最大可充电功率
     * 考虑SOC上限、最大充电功率、温度等约束
     * @param deviceSn 设备编号
     * @param currentSoc 当前SOC
     * @param currentTemperature 当前温度
     * @return 最大可充电功率（kW）
     */
    @GetMapping("/calculate/charge-power")
    public Result<BigDecimal> calculateMaxChargePower(
            @RequestParam String deviceSn,
            @RequestParam BigDecimal currentSoc,
            @RequestParam(required = false) BigDecimal currentTemperature) {
        if (currentTemperature == null) {
            currentTemperature = new BigDecimal("25");
        }
        return Result.success(batteryConfigService.calculateMaxChargePower(deviceSn, currentSoc, currentTemperature));
    }

    /**
     * 计算最大可放电功率
     * 考虑SOC下限、最大放电功率、温度等约束
     * @param deviceSn 设备编号
     * @param currentSoc 当前SOC
     * @param currentTemperature 当前温度
     * @return 最大可放电功率（kW）
     */
    @GetMapping("/calculate/discharge-power")
    public Result<BigDecimal> calculateMaxDischargePower(
            @RequestParam String deviceSn,
            @RequestParam BigDecimal currentSoc,
            @RequestParam(required = false) BigDecimal currentTemperature) {
        if (currentTemperature == null) {
            currentTemperature = new BigDecimal("25");
        }
        return Result.success(batteryConfigService.calculateMaxDischargePower(deviceSn, currentSoc, currentTemperature));
    }

    /**
     * 计算有效容量（考虑SOH衰减）
     * 有效容量 = 额定容量 × 当前SOH
     * @param deviceSn 设备编号
     * @return 有效容量（kWh）
     */
    @GetMapping("/calculate/effective-capacity")
    public Result<BigDecimal> calculateEffectiveCapacity(@RequestParam String deviceSn) {
        return Result.success(batteryConfigService.calculateEffectiveCapacity(deviceSn));
    }

    /**
     * 计算可用电量
     * 可用电量 = 有效容量 × (当前SOC - 最小SOC) / 100
     * @param deviceSn 设备编号
     * @param currentSoc 当前SOC
     * @return 可用电量（kWh）
     */
    @GetMapping("/calculate/available-energy")
    public Result<BigDecimal> calculateAvailableEnergy(
            @RequestParam String deviceSn,
            @RequestParam BigDecimal currentSoc) {
        return Result.success(batteryConfigService.calculateAvailableEnergy(deviceSn, currentSoc));
    }

    /**
     * 获取电池类型统计
     * @param enabled 是否启用
     * @return 电池类型统计Map
     */
    @GetMapping("/statistics/type")
    public Result<Map<String, Long>> getBatteryTypeStatistics(@RequestParam(defaultValue = "true") boolean enabled) {
        return Result.success(batteryConfigService.getBatteryTypeStatistics(enabled));
    }
}
