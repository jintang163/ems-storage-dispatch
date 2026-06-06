package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.strategy.DispatchPlanDTO;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.service.DispatchPlanService;
import com.ems.service.PythonOptimizerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/optimizer")
@RequiredArgsConstructor
public class PythonOptimizerController {

    private final PythonOptimizerService pythonOptimizerService;
    private final DispatchPlanService dispatchPlanService;

    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        boolean healthy = pythonOptimizerService.isHealthy();
        Map<String, Object> result = Map.of(
                "healthy", healthy,
                "service", "python-optimizer",
                "timestamp", System.currentTimeMillis()
        );
        return Result.success(result);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = pythonOptimizerService.getStatus();
        return Result.success(status);
    }

    @PostMapping("/rolling-optimize")
    public Result<DispatchPlanDTO> rollingOptimize(
            @RequestParam String strategyCode,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate planDate,
            @RequestParam(defaultValue = "0") Integer startHour) {
        log.info("调用Python滚动优化 - 策略: {}, 日期: {}, 开始时段: {}", strategyCode, planDate, startHour);
        DispatchPlanDTO plan = dispatchPlanService.generateRollingPlanWithPython(strategyCode, planDate, startHour);
        return Result.success(plan);
    }

    @PostMapping("/real-time-adjust")
    public Result<StrategyResultVO> realTimeAdjust(
            @RequestParam String strategyCode,
            @RequestParam(required = false) String batterySn,
            @RequestParam BigDecimal currentSoc,
            @RequestParam BigDecimal expectedSoc,
            @RequestParam BigDecimal currentLoad,
            @RequestParam BigDecimal forecastLoad,
            @RequestParam BigDecimal plannedPower) {
        log.info("调用Python实时调整 - 策略: {}, SOC偏差: {}", strategyCode, currentSoc.subtract(expectedSoc));
        StrategyResultVO result = dispatchPlanService.executeRealTimeAdjustWithPython(
                strategyCode, batterySn, currentSoc, expectedSoc, currentLoad, forecastLoad, plannedPower);
        return Result.success(result);
    }

    @PostMapping("/rolling-optimize/15min")
    public Result<Map<String, Object>> rollingOptimize15Min(@RequestBody Map<String, Object> request) {
        log.info("调用Python 15分钟滚动优化");
        Map<String, Object> result = pythonOptimizerService.rollingOptimize15Min(request);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Result.success(result);
        } else {
            return Result.error(result.getOrDefault("message", "优化失败").toString());
        }
    }

    @GetMapping("/history/optimization")
    public Result<List<Map<String, Object>>> getOptimizationHistory(
            @RequestParam(required = false) String strategyCode,
            @RequestParam(defaultValue = "100") Integer limit) {
        List<Map<String, Object>> history = pythonOptimizerService.getOptimizationHistory(strategyCode, limit);
        return Result.success(history);
    }

    @GetMapping("/history/adjustment")
    public Result<List<Map<String, Object>>> getAdjustmentHistory(
            @RequestParam(required = false) String strategyCode,
            @RequestParam(required = false) String adjustmentType,
            @RequestParam(defaultValue = "100") Integer limit) {
        List<Map<String, Object>> history = pythonOptimizerService.getAdjustmentHistory(strategyCode, adjustmentType, limit);
        return Result.success(history);
    }

    @GetMapping("/statistics/adjustment")
    public Result<Map<String, Object>> getAdjustmentStatistics(
            @RequestParam(required = false) String strategyCode) {
        Map<String, Object> statistics = pythonOptimizerService.getAdjustmentStatistics(strategyCode);
        return Result.success(statistics);
    }
}
