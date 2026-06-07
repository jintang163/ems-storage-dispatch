package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.strategy.ManualForceChargeDischargeDTO;
import com.ems.domain.dto.strategy.ManualStandbyDTO;
import com.ems.domain.dto.strategy.ModeSwitchDTO;
import com.ems.domain.dto.strategy.RealTimeControlRequest;
import com.ems.domain.dto.strategy.StrategyExecutionLogDTO;
import com.ems.domain.dto.strategy.StrategyParamAdjustDTO;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.domain.vo.strategy.StrategyStatisticsVO;
import com.ems.service.RealTimeStrategyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 实时策略控制器
 * 提供实时充放电控制和需量管理的REST API接口
 *
 * 核心功能：
 * 1. 实时需量控制：当实时负荷接近需量阈值时，优先放电抑制需量超标
 * 2. 峰谷套利：根据实时电价动态调整充放电策略
 * 3. 削峰填谷：在负荷高峰放电、低谷充电，平抑负荷波动
 * 4. 多目标优化：综合考虑套利收益、寿命损耗、需量费用
 *
 * 实时控制策略优先级：
 * 1. 需量控制（最高优先级）- 避免需量超标罚款
 * 2. 电池保护 - 遵守SOC范围、温度限制、充放电倍率限制
 * 3. 计划执行 - 按照调度计划运行
 * 4. 套利优化 - 捕捉实时电价机会
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/strategy/realtime")
@RequiredArgsConstructor
@CrossOrigin
public class RealTimeStrategyController {

    private final RealTimeStrategyService realTimeStrategyService;

    @PostMapping("/control")
    public Result<StrategyResultVO> executeRealTimeControl(@Valid @RequestBody RealTimeControlRequest request) {
        return Result.success(realTimeStrategyService.executeRealTimeControl(request));
    }

    @PostMapping("/demand-control")
    public Result<StrategyResultVO> executeDemandControl(@Valid @RequestBody RealTimeControlRequest request) {
        return Result.success(realTimeStrategyService.executeDemandControl(request));
    }

    @PostMapping("/arbitrage")
    public Result<StrategyResultVO> executePeakValleyArbitrage(@Valid @RequestBody RealTimeControlRequest request) {
        return Result.success(realTimeStrategyService.executePeakValleyArbitrage(request));
    }

    @PostMapping("/peak-shaving")
    public Result<StrategyResultVO> executePeakShaving(@Valid @RequestBody RealTimeControlRequest request) {
        return Result.success(realTimeStrategyService.executePeakShaving(request));
    }

    @PostMapping("/valley-filling")
    public Result<StrategyResultVO> executeValleyFilling(@Valid @RequestBody RealTimeControlRequest request) {
        return Result.success(realTimeStrategyService.executeValleyFilling(request));
    }

    @PostMapping("/multi-objective")
    public Result<StrategyResultVO> executeMultiObjectiveOptimization(@Valid @RequestBody RealTimeControlRequest request) {
        return Result.success(realTimeStrategyService.executeMultiObjectiveOptimization(request));
    }

    @GetMapping("/demand-warning/{strategyCode}")
    public Result<String> checkDemandWarning(
            @PathVariable String strategyCode,
            @RequestParam BigDecimal currentDemand,
            @RequestParam(required = false) BigDecimal predictedDemand) {
        return Result.success(realTimeStrategyService.checkDemandWarning(
                strategyCode, currentDemand, predictedDemand));
    }

    @GetMapping("/discharge-power/{strategyCode}")
    public Result<BigDecimal> calculateRequiredDischargePower(
            @PathVariable String strategyCode,
            @RequestParam BigDecimal currentDemand,
            @RequestParam(required = false) BigDecimal predictedDemand) {
        return Result.success(realTimeStrategyService.calculateRequiredDischargePower(
                strategyCode, currentDemand, predictedDemand));
    }

    @GetMapping("/charge-power/{strategyCode}")
    public Result<BigDecimal> calculateRequiredChargePower(
            @PathVariable String strategyCode,
            @RequestParam BigDecimal currentDemand,
            @RequestParam BigDecimal currentSoc) {
        return Result.success(realTimeStrategyService.calculateRequiredChargePower(
                strategyCode, currentDemand, currentSoc));
    }

    @GetMapping("/logs/{strategyCode}")
    public Result<List<StrategyExecutionLogDTO>> listExecutionLogs(
            @PathVariable String strategyCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(realTimeStrategyService.listExecutionLogs(
                strategyCode, startTime, endTime));
    }

    @GetMapping("/logs/recent/{strategyCode}")
    public Result<List<StrategyExecutionLogDTO>> listRecentExecutions(
            @PathVariable String strategyCode,
            @RequestParam(defaultValue = "24") int hours) {
        return Result.success(realTimeStrategyService.listRecentExecutions(strategyCode, hours));
    }

    @GetMapping("/statistics/{strategyCode}")
    public Result<StrategyStatisticsVO> getExecutionStatistics(
            @PathVariable String strategyCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(realTimeStrategyService.getExecutionStatistics(
                strategyCode, startDate, endDate));
    }

    @GetMapping("/status/{strategyCode}")
    public Result<Map<String, Object>> getRealTimeStatus(
            @PathVariable String strategyCode,
            @RequestParam(required = false) String batterySn,
            @RequestParam(required = false) String transformerCode) {
        return Result.success(realTimeStrategyService.getRealTimeStatus(
                strategyCode, batterySn, transformerCode));
    }

    @GetMapping("/benefits/{strategyCode}")
    public Result<Map<String, BigDecimal>> getTotalBenefits(
            @PathVariable String strategyCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(realTimeStrategyService.getTotalBenefits(
                strategyCode, startDate, endDate));
    }

    @GetMapping("/action-statistics/{strategyCode}")
    public Result<Map<String, Object>> getControlActionStatistics(
            @PathVariable String strategyCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(realTimeStrategyService.getControlActionStatistics(
                strategyCode, startDate, endDate));
    }

    @PostMapping("/degradation-cost/{strategyCode}")
    public Result<BigDecimal> calculateBatteryDegradationCost(
            @PathVariable String strategyCode,
            @RequestBody Map<String, BigDecimal> params) {
        return Result.success(realTimeStrategyService.calculateBatteryDegradationCost(
                strategyCode,
                params.get("chargeRate"),
                params.get("dischargeRate"),
                params.get("depthOfDischarge"),
                params.get("temperature")));
    }

    @PostMapping("/demand-saving/{strategyCode}")
    public Result<BigDecimal> calculateDemandChargeSaving(
            @PathVariable String strategyCode,
            @RequestParam BigDecimal originalDemand,
            @RequestParam BigDecimal controlledDemand) {
        return Result.success(realTimeStrategyService.calculateDemandChargeSaving(
                strategyCode, originalDemand, controlledDemand));
    }

    @PostMapping("/manual/force-charge-discharge")
    public Result<StrategyResultVO> executeManualForceChargeDischarge(
            @Valid @RequestBody ManualForceChargeDischargeDTO request) {
        return Result.success(realTimeStrategyService.executeManualForceChargeDischarge(request));
    }

    @PostMapping("/manual/standby")
    public Result<StrategyResultVO> executeManualStandby(@Valid @RequestBody ManualStandbyDTO request) {
        return Result.success(realTimeStrategyService.executeManualStandby(request));
    }

    @PostMapping("/manual/cancel/{strategyCode}")
    public Result<Void> cancelManualControl(
            @PathVariable String strategyCode,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String remark) {
        realTimeStrategyService.cancelManualControl(strategyCode, operator, remark);
        return Result.success();
    }

    @PostMapping("/parameters/adjust")
    public Result<StrategyResultVO> adjustStrategyParameters(
            @Valid @RequestBody StrategyParamAdjustDTO request) {
        return Result.success(realTimeStrategyService.adjustStrategyParameters(request));
    }

    @PostMapping("/mode/switch")
    public Result<Map<String, Object>> switchControlMode(@Valid @RequestBody ModeSwitchDTO request) {
        return Result.success(realTimeStrategyService.switchControlMode(request));
    }

    @GetMapping("/mode/status/{strategyCode}")
    public Result<Map<String, Object>> getControlModeStatus(@PathVariable String strategyCode) {
        return Result.success(realTimeStrategyService.getControlModeStatus(strategyCode));
    }

    @PostMapping("/safety/validate/manual")
    public Result<Map<String, String>> validateManualControlSafety(
            @Valid @RequestBody ManualForceChargeDischargeDTO request) {
        return Result.success(realTimeStrategyService.validateManualControlSafety(request));
    }

    @PostMapping("/safety/validate/mode-switch")
    public Result<Map<String, String>> validateModeSwitchSafety(
            @Valid @RequestBody ModeSwitchDTO request) {
        return Result.success(realTimeStrategyService.validateModeSwitchSafety(request));
    }
}
