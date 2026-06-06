package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.strategy.DispatchPlanDTO;
import com.ems.domain.dto.strategy.StrategyGenerateRequest;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.domain.vo.strategy.StrategyStatisticsVO;
import com.ems.service.DispatchPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 调度计划控制器
 * 提供24小时充放电计划的REST API接口
 *
 * 核心功能：
 * 1. 日前调度计划生成：基于电价预测和负荷预测生成24小时充放电计划
 * 2. 滚动优化：根据实时数据动态调整后续时段的计划
 * 3. 计划查询和管理：查询、审批、执行、取消调度计划
 * 4. 收益统计：计算预期收益、衰减成本、需量节省等
 *
 * 调度计划生成流程：
 * 1. 数据收集：获取电价预测、负荷预测、光伏预测
 * 2. 约束建模：SOC约束、功率约束、寿命约束、需量约束
 * 3. 多目标优化：综合考虑套利、寿命、需量三个目标
 * 4. 结果输出：生成24小时逐时段充放电计划
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/strategy/plan")
@RequiredArgsConstructor
@CrossOrigin
public class DispatchPlanController {

    private final DispatchPlanService dispatchPlanService;

    @PostMapping("/generate")
    public Result<DispatchPlanDTO> generatePlan(@Valid @RequestBody StrategyGenerateRequest request) {
        return Result.success(dispatchPlanService.generatePlan(request));
    }

    @PostMapping("/regenerate/{id}")
    public Result<DispatchPlanDTO> regeneratePlan(@PathVariable Long id) {
        return Result.success(dispatchPlanService.regeneratePlan(id));
    }

    @GetMapping("/{id}")
    public Result<DispatchPlanDTO> getById(@PathVariable Long id) {
        return Result.success(dispatchPlanService.getById(id));
    }

    @GetMapping("/latest/{strategyCode}")
    public Result<DispatchPlanDTO> getLatestPendingPlan(
            @PathVariable String strategyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return Result.success(dispatchPlanService.getLatestPendingPlan(strategyCode, date));
    }

    @GetMapping("/strategy/{strategyId}")
    public Result<List<DispatchPlanDTO>> listByStrategyId(@PathVariable Long strategyId) {
        return Result.success(dispatchPlanService.listByStrategyId(strategyId));
    }

    @GetMapping("/date/{date}")
    public Result<List<DispatchPlanDTO>> listByPlanDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(dispatchPlanService.listByPlanDate(date));
    }

    @GetMapping("/date-range")
    public Result<List<DispatchPlanDTO>> listByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(dispatchPlanService.listByDateRange(startDate, endDate));
    }

    @GetMapping("/status/{status}")
    public Result<List<DispatchPlanDTO>> listByStatus(@PathVariable String status) {
        return Result.success(dispatchPlanService.listByStatus(status));
    }

    @GetMapping("/pending")
    public Result<List<DispatchPlanDTO>> listPendingPlans() {
        return Result.success(dispatchPlanService.listPendingPlans());
    }

    @PostMapping("/execute/{id}")
    public Result<Void> executePlan(@PathVariable Long id) {
        dispatchPlanService.executePlan(id);
        return Result.success();
    }

    @PostMapping("/cancel/{id}")
    public Result<Void> cancelPlan(@PathVariable Long id) {
        dispatchPlanService.cancelPlan(id);
        return Result.success();
    }

    @PostMapping("/approve/{id}")
    public Result<Void> approvePlan(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        dispatchPlanService.approvePlan(id, body.get("approvedBy"));
        return Result.success();
    }

    @PostMapping("/execute-current/{strategyCode}")
    public Result<StrategyResultVO> executeCurrentHour(
            @PathVariable String strategyCode,
            @RequestParam(required = false) String batterySn) {
        return Result.success(dispatchPlanService.executeCurrentHour(strategyCode, batterySn));
    }

    @PostMapping("/rolling/{strategyCode}")
    public Result<DispatchPlanDTO> generateRollingPlan(
            @PathVariable String strategyCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam int startHour) {
        return Result.success(dispatchPlanService.generateRollingPlan(strategyCode, date, startHour));
    }

    @GetMapping("/benefits/{id}")
    public Result<Map<String, BigDecimal>> calculateExpectedBenefits(@PathVariable Long id) {
        return Result.success(dispatchPlanService.calculateExpectedBenefits(id));
    }

    @GetMapping("/statistics/date/{date}")
    public Result<StrategyStatisticsVO> getStatisticsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String strategyCode) {
        return Result.success(dispatchPlanService.getStatisticsByDate(date, strategyCode));
    }

    @GetMapping("/statistics/date-range")
    public Result<List<StrategyStatisticsVO>> getStatisticsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String strategyCode) {
        return Result.success(dispatchPlanService.getStatisticsByDateRange(startDate, endDate, strategyCode));
    }

    @GetMapping("/status-summary")
    public Result<Map<String, Object>> getPlanStatusSummary() {
        return Result.success(dispatchPlanService.getPlanStatusSummary());
    }

    @GetMapping("/total-benefits")
    public Result<Map<String, BigDecimal>> getTotalExpectedBenefits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(dispatchPlanService.getTotalExpectedBenefits(startDate, endDate));
    }
}
