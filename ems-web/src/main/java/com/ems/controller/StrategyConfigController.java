package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.strategy.StrategyConfigDTO;
import com.ems.service.StrategyConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 策略配置控制器
 * 提供策略配置的REST API接口
 *
 * 核心功能：
 * 1. 策略配置CRUD管理
 * 2. 多目标权重配置（套利收益 vs 寿命损耗 vs 需量费用）
 * 3. 电池寿命约束配置（充放电倍率、SOC范围、循环次数）
 * 4. 策略功能开关（峰谷套利、削峰填谷、需量控制等）
 *
 * 多目标权重配置说明：
 * - arbitrageWeight: 套利收益权重，默认0.5
 * - lifespanWeight: 寿命损耗权重，默认0.3
 * - demandWeight: 需量费用权重，默认0.2
 * 权重之和建议为1.0，系统会自动归一化处理。
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/strategy/config")
@RequiredArgsConstructor
@CrossOrigin
public class StrategyConfigController {

    private final StrategyConfigService strategyConfigService;

    @PostMapping
    public Result<StrategyConfigDTO> create(@Valid @RequestBody StrategyConfigDTO dto) {
        return Result.success(strategyConfigService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<StrategyConfigDTO> update(@PathVariable Long id, @Valid @RequestBody StrategyConfigDTO dto) {
        return Result.success(strategyConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        strategyConfigService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<StrategyConfigDTO> getById(@PathVariable Long id) {
        return Result.success(strategyConfigService.getById(id));
    }

    @GetMapping("/code/{strategyCode}")
    public Result<StrategyConfigDTO> getByStrategyCode(@PathVariable String strategyCode) {
        return Result.success(strategyConfigService.getByStrategyCode(strategyCode));
    }

    @GetMapping("/list")
    public Result<List<StrategyConfigDTO>> listAll() {
        return Result.success(strategyConfigService.listAll());
    }

    @GetMapping("/enabled")
    public Result<List<StrategyConfigDTO>> listEnabled() {
        return Result.success(strategyConfigService.listEnabled());
    }

    @GetMapping("/type/{strategyType}")
    public Result<List<StrategyConfigDTO>> listByStrategyType(@PathVariable String strategyType) {
        return Result.success(strategyConfigService.listByStrategyType(strategyType));
    }

    @GetMapping("/default")
    public Result<StrategyConfigDTO> getDefaultStrategy() {
        return Result.success(strategyConfigService.getDefaultStrategy());
    }

    @GetMapping("/battery/{batterySn}")
    public Result<List<StrategyConfigDTO>> listByBatterySn(@PathVariable String batterySn) {
        return Result.success(strategyConfigService.listByBatterySn(batterySn));
    }

    @GetMapping("/transformer/{transformerCode}")
    public Result<List<StrategyConfigDTO>> listByTransformerCode(@PathVariable String transformerCode) {
        return Result.success(strategyConfigService.listByTransformerCode(transformerCode));
    }

    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        strategyConfigService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }

    @PatchMapping("/{id}/default")
    public Result<Void> setDefaultStrategy(@PathVariable Long id) {
        strategyConfigService.setDefaultStrategy(id);
        return Result.success();
    }

    @PostMapping("/validate")
    public Result<Map<String, String>> validate(@RequestBody StrategyConfigDTO dto) {
        return Result.success(strategyConfigService.validateStrategyConfig(dto));
    }

    @PostMapping("/normalize-weights")
    public Result<BigDecimal> normalizeWeights(@RequestBody StrategyConfigDTO dto) {
        return Result.success(strategyConfigService.normalizeWeights(dto));
    }

    @GetMapping("/statistics/type")
    public Result<Map<String, Long>> getStrategyTypeStatistics() {
        return Result.success(strategyConfigService.getStrategyTypeStatistics());
    }

    @GetMapping("/statistics/enabled-count")
    public Result<Long> getEnabledStrategyCount() {
        return Result.success(strategyConfigService.getEnabledStrategyCount());
    }
}
