package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.battery.TransformerDemandConfigDTO;
import com.ems.service.TransformerDemandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 变压器需量管理控制器
 * 提供变压器需量配置和需量控制的REST API接口
 *
 * 核心功能：
 * 1. 需量阈值配置：设置需量控制目标值
 * 2. 考核周期配置：通常为15分钟、30分钟或1小时
 * 3. 需量预测：使用滑动窗口算法预测本周期需量
 * 4. 控制策略：根据需量预测生成最优控制建议
 * 5. 电费计算：计算基本电费和需量优化收益
 *
 * 需量控制原理：
 * 需量 = 考核周期内的平均最大有功功率
 * 基本电费 = max(变压器容量 × 容量电价, 最大需量 × 需量电价)
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/transformer/demand")
@RequiredArgsConstructor
@CrossOrigin
public class TransformerDemandController {

    private final TransformerDemandService demandService;

    /**
     * 创建变压器需量配置
     * @param dto 需量配置
     * @return 创建后的配置
     */
    @PostMapping
    public Result<TransformerDemandConfigDTO> create(@Valid @RequestBody TransformerDemandConfigDTO dto) {
        return Result.success(demandService.create(dto));
    }

    /**
     * 更新变压器需量配置
     * @param id 配置ID
     * @param dto 需量配置
     * @return 更新后的配置
     */
    @PutMapping("/{id}")
    public Result<TransformerDemandConfigDTO> update(@PathVariable Long id,
                                                      @Valid @RequestBody TransformerDemandConfigDTO dto) {
        return Result.success(demandService.update(id, dto));
    }

    /**
     * 删除变压器需量配置
     * @param id 配置ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        demandService.delete(id);
        return Result.success();
    }

    /**
     * 根据ID获取变压器需量配置
     * @param id 配置ID
     * @return 需量配置
     */
    @GetMapping("/{id}")
    public Result<TransformerDemandConfigDTO> getById(@PathVariable Long id) {
        return Result.success(demandService.getById(id));
    }

    /**
     * 根据变压器编号获取需量配置
     * @param transformerCode 变压器编号
     * @return 需量配置
     */
    @GetMapping("/code/{transformerCode}")
    public Result<TransformerDemandConfigDTO> getByTransformerCode(@PathVariable String transformerCode) {
        return Result.success(demandService.getByTransformerCode(transformerCode));
    }

    /**
     * 获取所有需量配置列表
     * @return 配置列表
     */
    @GetMapping("/list")
    public Result<List<TransformerDemandConfigDTO>> listAll() {
        return Result.success(demandService.listAll());
    }

    /**
     * 获取启用的需量配置列表
     * @return 启用的配置列表
     */
    @GetMapping("/enabled")
    public Result<List<TransformerDemandConfigDTO>> listEnabled() {
        return Result.success(demandService.listEnabled());
    }

    /**
     * 获取启用需量控制的变压器配置列表
     * @return 启用需量控制的配置列表
     */
    @GetMapping("/control-enabled")
    public Result<List<TransformerDemandConfigDTO>> listDemandControlEnabled() {
        return Result.success(demandService.listDemandControlEnabled());
    }

    /**
     * 更新配置启用状态
     * @param id 配置ID
     * @param body 包含enabled字段
     * @return 操作结果
     */
    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        demandService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }

    /**
     * 更新需量控制启用状态
     * @param id 配置ID
     * @param body 包含demandControlEnabled字段
     * @return 操作结果
     */
    @PatchMapping("/{id}/control-enabled")
    public Result<Void> updateDemandControlEnabled(@PathVariable Long id,
                                                    @RequestBody Map<String, Boolean> body) {
        demandService.updateDemandControlEnabled(id, body.get("demandControlEnabled"));
        return Result.success();
    }

    /**
     * 预测本考核周期的需量
     * 使用滑动窗口算法，基于历史数据和当前功率预测周期结束时的需量
     *
     * @param transformerCode 变压器编号
     * @param currentPower 当前实时功率（kW）
     * @param cycleElapsedMinutes 考核周期已过时间（分钟）
     * @param historyPowerData 历史功率数据（kW数组）
     * @return 预测需量（kW）
     */
    @PostMapping("/predict")
    public Result<BigDecimal> predictDemand(
            @RequestParam String transformerCode,
            @RequestParam BigDecimal currentPower,
            @RequestParam Integer cycleElapsedMinutes,
            @RequestBody(required = false) List<BigDecimal> historyPowerData) {
        return Result.success(demandService.predictDemand(
                transformerCode, currentPower, cycleElapsedMinutes, historyPowerData));
    }

    /**
     * 计算需要削减的功率
     * 当预测需量超过阈值时，计算需要削减的功率值
     *
     * @param transformerCode 变压器编号
     * @param predictedDemand 预测需量（kW）
     * @return 需要削减的功率（kW），正数表示需要削减
     */
    @GetMapping("/reduction")
    public Result<BigDecimal> calculatePowerReduction(
            @RequestParam String transformerCode,
            @RequestParam BigDecimal predictedDemand) {
        return Result.success(demandService.calculatePowerReduction(transformerCode, predictedDemand));
    }

    /**
     * 生成需量控制建议
     * 根据当前工况和需量预测，生成最优的控制策略建议
     *
     * @param transformerCode 变压器编号
     * @param currentLoad 当前总负荷（kW）
     * @param currentPvPower 当前光伏出力（kW）
     * @param currentSoc 当前电池SOC（%）
     * @param predictedDemand 预测需量（kW）
     * @return 控制建议，包含：
     *         - dischargePower: 建议放电功率（kW）
     *         - chargePower: 建议充电功率（kW）
     *         - loadSheddingPower: 建议切负荷功率（kW）
     *         - urgencyLevel: 紧急程度（LOW/MEDIUM/HIGH/CRITICAL）
     *         - recommendedActions: 建议操作列表
     */
    @PostMapping("/recommendation")
    public Result<Map<String, Object>> generateControlRecommendation(
            @RequestParam String transformerCode,
            @RequestParam BigDecimal currentLoad,
            @RequestParam BigDecimal currentPvPower,
            @RequestParam BigDecimal currentSoc,
            @RequestParam BigDecimal predictedDemand) {
        return Result.success(demandService.generateControlRecommendation(
                transformerCode, currentLoad, currentPvPower, currentSoc, predictedDemand));
    }

    /**
     * 检查需量预警状态
     * @param transformerCode 变压器编号
     * @param predictedDemand 预测需量（kW）
     * @return 预警级别：null=无需预警，WARNING=预警，ALARM=告警
     */
    @GetMapping("/warning")
    public Result<String> checkDemandWarning(
            @RequestParam String transformerCode,
            @RequestParam BigDecimal predictedDemand) {
        return Result.success(demandService.checkDemandWarning(transformerCode, predictedDemand));
    }

    /**
     * 更新当前考核周期最大需量
     * @param transformerCode 变压器编号
     * @param maxDemand 当前最大需量（kW）
     * @return 操作结果
     */
    @PostMapping("/max-demand/current")
    public Result<Void> updateCurrentMaxDemand(
            @RequestParam String transformerCode,
            @RequestParam BigDecimal maxDemand) {
        demandService.updateCurrentMaxDemand(transformerCode, maxDemand);
        return Result.success();
    }

    /**
     * 重置考核周期
     * 将当前最大需量移至上一周期，重置当前最大需量
     * @param transformerCode 变压器编号
     * @return 操作结果
     */
    @PostMapping("/cycle/reset")
    public Result<Void> resetAssessmentCycle(@RequestParam String transformerCode) {
        demandService.resetAssessmentCycle(transformerCode);
        return Result.success();
    }

    /**
     * 计算基本电费
     * 根据计费方式（按容量/按需量）计算基本电费
     *
     * @param transformerCode 变压器编号
     * @param maxDemand 当月最大需量（kW）
     * @param days 计费天数（默认30）
     * @return 基本电费（元）
     */
    @GetMapping("/charge")
    public Result<BigDecimal> calculateDemandCharge(
            @RequestParam String transformerCode,
            @RequestParam BigDecimal maxDemand,
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(demandService.calculateDemandCharge(transformerCode, maxDemand, days));
    }

    /**
     * 计算需量优化节约的电费
     * @param transformerCode 变压器编号
     * @param originalMaxDemand 优化前最大需量（kW）
     * @param optimizedMaxDemand 优化后最大需量（kW）
     * @param days 计费天数（默认30）
     * @return 节约电费（元）
     */
    @GetMapping("/saving")
    public Result<BigDecimal> calculateDemandSaving(
            @RequestParam String transformerCode,
            @RequestParam BigDecimal originalMaxDemand,
            @RequestParam BigDecimal optimizedMaxDemand,
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(demandService.calculateDemandSaving(
                transformerCode, originalMaxDemand, optimizedMaxDemand, days));
    }

    /**
     * 获取计费方式统计
     * @return 计费方式统计Map
     */
    @GetMapping("/statistics/billing")
    public Result<Map<String, Long>> getBillingMethodStatistics() {
        return Result.success(demandService.getBillingMethodStatistics());
    }

    /**
     * 获取变压器总额定容量
     * @return 总额定容量（kVA）
     */
    @GetMapping("/statistics/total-capacity")
    public Result<BigDecimal> getTotalRatedCapacity() {
        return Result.success(demandService.getTotalRatedCapacity());
    }

    /**
     * 验证需量配置参数
     * @param dto 需量配置
     * @return 验证结果（key=字段名, value=错误信息）
     */
    @PostMapping("/validate")
    public Result<Map<String, String>> validateDemandConfig(
            @Valid @RequestBody TransformerDemandConfigDTO dto) {
        return Result.success(demandService.validateDemandConfig(dto));
    }
}
