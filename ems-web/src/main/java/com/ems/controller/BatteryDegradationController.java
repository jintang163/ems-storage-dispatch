package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.battery.BatteryDegradationModelDTO;
import com.ems.domain.dto.battery.BatteryDegradationPointDTO;
import com.ems.service.BatteryDegradationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 电池衰减模型控制器
 * 提供电池衰减模型管理和SOH预估的REST API接口
 *
 * 核心功能：
 * 1. 衰减模型管理：支持4种衰减模型（线性、指数、分段、经验）
 * 2. SOH预估：根据循环次数预估当前SOH
 * 3. 寿命预估：预估剩余循环次数和使用寿命
 * 4. 衰减曲线：生成衰减曲线数据点用于图表展示
 * 5. 日历老化：计算静置存储导致的容量衰减
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/battery/degradation")
@RequiredArgsConstructor
@CrossOrigin
public class BatteryDegradationController {

    private final BatteryDegradationService degradationService;

    /**
     * 创建电池衰减模型
     * @param dto 衰减模型配置
     * @return 创建后的模型
     */
    @PostMapping
    public Result<BatteryDegradationModelDTO> create(@Valid @RequestBody BatteryDegradationModelDTO dto) {
        return Result.success(degradationService.create(dto));
    }

    /**
     * 更新电池衰减模型
     * @param id 模型ID
     * @param dto 衰减模型配置
     * @return 更新后的模型
     */
    @PutMapping("/{id}")
    public Result<BatteryDegradationModelDTO> update(@PathVariable Long id, @Valid @RequestBody BatteryDegradationModelDTO dto) {
        return Result.success(degradationService.update(id, dto));
    }

    /**
     * 删除电池衰减模型
     * @param id 模型ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        degradationService.delete(id);
        return Result.success();
    }

    /**
     * 根据ID获取电池衰减模型（包含数据点）
     * @param id 模型ID
     * @return 衰减模型详情
     */
    @GetMapping("/{id}")
    public Result<BatteryDegradationModelDTO> getById(@PathVariable Long id) {
        return Result.success(degradationService.getById(id));
    }

    /**
     * 获取所有衰减模型列表
     * @return 衰减模型列表
     */
    @GetMapping("/list")
    public Result<List<BatteryDegradationModelDTO>> listAll() {
        return Result.success(degradationService.listAll());
    }

    /**
     * 获取启用的衰减模型列表
     * @return 启用的衰减模型列表
     */
    @GetMapping("/enabled")
    public Result<List<BatteryDegradationModelDTO>> listEnabled() {
        return Result.success(degradationService.listEnabled());
    }

    /**
     * 根据模型类型获取衰减模型列表
     * @param modelType 模型类型（LINEAR、EXPONENTIAL、PIECEWISE、EMPIRICAL）
     * @return 衰减模型列表
     */
    @GetMapping("/type/{modelType}")
    public Result<List<BatteryDegradationModelDTO>> listByModelType(@PathVariable String modelType) {
        return Result.success(degradationService.listByModelType(modelType));
    }

    /**
     * 根据电池类型获取衰减模型列表
     * @param batteryType 电池类型（LFP、NMC、LTO等）
     * @return 衰减模型列表
     */
    @GetMapping("/battery-type/{batteryType}")
    public Result<List<BatteryDegradationModelDTO>> listByBatteryType(@PathVariable String batteryType) {
        return Result.success(degradationService.listByBatteryType(batteryType));
    }

    /**
     * 获取默认衰减模型
     * @return 默认衰减模型
     */
    @GetMapping("/default")
    public Result<BatteryDegradationModelDTO> getDefaultModel() {
        return Result.success(degradationService.getDefaultModel());
    }

    /**
     * 获取指定电池类型的默认衰减模型
     * @param batteryType 电池类型
     * @return 默认衰减模型
     */
    @GetMapping("/default/{batteryType}")
    public Result<BatteryDegradationModelDTO> getDefaultModelByBatteryType(@PathVariable String batteryType) {
        return Result.success(degradationService.getDefaultModelByBatteryType(batteryType));
    }

    /**
     * 预估指定循环次数下的SOH
     * @param modelId 衰减模型ID
     * @param cycleCount 循环次数
     * @return 预估SOH（0-1）
     */
    @GetMapping("/estimate/soh")
    public Result<BigDecimal> estimateSoh(
            @RequestParam Long modelId,
            @RequestParam Integer cycleCount) {
        return Result.success(degradationService.estimateSoh(modelId, cycleCount));
    }

    /**
     * 预估SOH（考虑环境影响因素）
     * @param modelId 衰减模型ID
     * @param cycleCount 循环次数
     * @param avgTemperature 平均温度（℃）
     * @param avgSoc 平均SOC（%）
     * @param avgChargeRate 平均充电倍率（C）
     * @param avgDischargeRate 平均放电倍率（C）
     * @param avgDepthOfDischarge 平均放电深度（%）
     * @return 预估SOH（0-1）
     */
    @GetMapping("/estimate/soh-with-factors")
    public Result<BigDecimal> estimateSohWithFactors(
            @RequestParam Long modelId,
            @RequestParam Integer cycleCount,
            @RequestParam(required = false) BigDecimal avgTemperature,
            @RequestParam(required = false) BigDecimal avgSoc,
            @RequestParam(required = false) BigDecimal avgChargeRate,
            @RequestParam(required = false) BigDecimal avgDischargeRate,
            @RequestParam(required = false) BigDecimal avgDepthOfDischarge) {
        return Result.success(degradationService.estimateSohWithFactors(
                modelId, cycleCount, avgTemperature, avgSoc,
                avgChargeRate, avgDischargeRate, avgDepthOfDischarge));
    }

    /**
     * 预估剩余循环次数
     * @param modelId 衰减模型ID
     * @param currentSoh 当前SOH
     * @param currentCycleCount 当前循环次数
     * @return 预估剩余循环次数
     */
    @GetMapping("/estimate/remaining-cycles")
    public Result<Integer> estimateRemainingCycles(
            @RequestParam Long modelId,
            @RequestParam BigDecimal currentSoh,
            @RequestParam Integer currentCycleCount) {
        return Result.success(degradationService.estimateRemainingCycles(
                modelId, currentSoh, currentCycleCount));
    }

    /**
     * 预估剩余使用寿命（年）
     * @param modelId 衰减模型ID
     * @param currentSoh 当前SOH
     * @param currentCycleCount 当前循环次数
     * @param dailyCycles 日均循环次数
     * @return 预估剩余使用寿命（年）
     */
    @GetMapping("/estimate/remaining-lifespan")
    public Result<BigDecimal> estimateRemainingLifespan(
            @RequestParam Long modelId,
            @RequestParam BigDecimal currentSoh,
            @RequestParam Integer currentCycleCount,
            @RequestParam(required = false) BigDecimal dailyCycles) {
        return Result.success(degradationService.estimateRemainingLifespan(
                modelId, currentSoh, currentCycleCount, dailyCycles));
    }

    /**
     * 生成衰减曲线数据点
     * @param modelId 衰减模型ID
     * @param startCycle 起始循环次数（默认0）
     * @param endCycle 结束循环次数（默认10000）
     * @param step 步长（默认500）
     * @return 衰减曲线数据点列表
     */
    @GetMapping("/curve")
    public Result<List<BatteryDegradationPointDTO>> generateDegradationCurve(
            @RequestParam Long modelId,
            @RequestParam(defaultValue = "0") Integer startCycle,
            @RequestParam(defaultValue = "10000") Integer endCycle,
            @RequestParam(defaultValue = "500") Integer step) {
        return Result.success(degradationService.generateDegradationCurve(
                modelId, startCycle, endCycle, step));
    }

    /**
     * 计算日历老化（静置老化）
     * @param modelId 衰减模型ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param storageSoc 存储SOC（%）
     * @param storageTemperature 存储温度（℃）
     * @return 日历老化导致的SOH衰减量
     */
    @GetMapping("/calendar-aging")
    public Result<BigDecimal> calculateCalendarAging(
            @RequestParam Long modelId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) BigDecimal storageSoc,
            @RequestParam(required = false) BigDecimal storageTemperature) {
        return Result.success(degradationService.calculateCalendarAging(
                modelId, startDate, endDate, storageSoc, storageTemperature));
    }

    /**
     * 更新模型启用状态
     * @param id 模型ID
     * @param body 包含enabled字段
     * @return 操作结果
     */
    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        degradationService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }

    /**
     * 设置默认模型
     * @param id 模型ID
     * @return 操作结果
     */
    @PostMapping("/{id}/default")
    public Result<Void> setDefaultModel(@PathVariable Long id) {
        degradationService.setDefaultModel(id);
        return Result.success();
    }

    /**
     * 添加衰减数据点
     * @param modelId 模型ID
     * @param pointDTO 数据点
     * @return 更新后的模型
     */
    @PostMapping("/{modelId}/points")
    public Result<BatteryDegradationModelDTO> addDegradationPoint(
            @PathVariable Long modelId,
            @Valid @RequestBody BatteryDegradationPointDTO pointDTO) {
        return Result.success(degradationService.addDegradationPoint(modelId, pointDTO));
    }

    /**
     * 批量添加衰减数据点
     * @param modelId 模型ID
     * @param pointDTOs 数据点列表
     * @return 更新后的模型
     */
    @PostMapping("/{modelId}/points/batch")
    public Result<BatteryDegradationModelDTO> addDegradationPoints(
            @PathVariable Long modelId,
            @Valid @RequestBody List<BatteryDegradationPointDTO> pointDTOs) {
        return Result.success(degradationService.addDegradationPoints(modelId, pointDTOs));
    }

    /**
     * 删除衰减数据点
     * @param modelId 模型ID
     * @param pointId 数据点ID
     * @return 操作结果
     */
    @DeleteMapping("/{modelId}/points/{pointId}")
    public Result<Void> removeDegradationPoint(
            @PathVariable Long modelId,
            @PathVariable Long pointId) {
        degradationService.removeDegradationPoint(modelId, pointId);
        return Result.success();
    }

    /**
     * 生成标准磷酸铁锂电池衰减曲线
     * LFP电池典型衰减曲线数据点
     * @return 标准衰减数据点列表
     */
    @GetMapping("/standard-curve/lfp")
    public Result<List<BatteryDegradationPointDTO>> generateStandardLFPCurve() {
        return Result.success(degradationService.generateStandardLFPCurve());
    }

    /**
     * 生成标准三元锂电池衰减曲线
     * NMC/NCM电池典型衰减曲线数据点
     * @return 标准衰减数据点列表
     */
    @GetMapping("/standard-curve/nmc")
    public Result<List<BatteryDegradationPointDTO>> generateStandardNMCCurve() {
        return Result.success(degradationService.generateStandardNMCCurve());
    }

    /**
     * 验证衰减模型参数
     * @param dto 衰减模型配置
     * @return 验证结果（key=字段名, value=错误信息）
     */
    @PostMapping("/validate")
    public Result<Map<String, String>> validateDegradationModel(
            @Valid @RequestBody BatteryDegradationModelDTO dto) {
        return Result.success(degradationService.validateDegradationModel(dto));
    }
}
