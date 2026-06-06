package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.service.PythonOptimizerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Python优化服务控制器
 *
 * <p>注意：实时调整功能已集成到主执行路径 /api/dispatch/execute-current/{strategyCode}
 * 本控制器仅提供运维接口和内部调用接口
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/optimizer")
@RequiredArgsConstructor
public class PythonOptimizerController {

    private final PythonOptimizerService pythonOptimizerService;

    /**
     * 健康检查 - 供Spring Cloud Gateway或K8s探针使用
     */
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

    /**
     * 服务状态查询 - 供运维监控使用
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = pythonOptimizerService.getStatus();
        return Result.success(status);
    }

    /**
     * 内部接口：获取优化历史 - 仅供定时任务和调试使用
     */
    @GetMapping("/internal/history/optimization")
    public Result<java.util.List<Map<String, Object>>> getOptimizationHistory(
            @RequestParam(required = false) String strategyCode,
            @RequestParam(defaultValue = "100") Integer limit) {
        java.util.List<Map<String, Object>> history = pythonOptimizerService.getOptimizationHistory(strategyCode, limit);
        return Result.success(history);
    }

    /**
     * 内部接口：获取调整历史 - 仅供定时任务和调试使用
     */
    @GetMapping("/internal/history/adjustment")
    public Result<java.util.List<Map<String, Object>>> getAdjustmentHistory(
            @RequestParam(required = false) String strategyCode,
            @RequestParam(required = false) String adjustmentType,
            @RequestParam(defaultValue = "100") Integer limit) {
        java.util.List<Map<String, Object>> history = pythonOptimizerService.getAdjustmentHistory(strategyCode, adjustmentType, limit);
        return Result.success(history);
    }

    /**
     * 内部接口：获取调整统计 - 仅供定时任务和调试使用
     */
    @GetMapping("/internal/statistics/adjustment")
    public Result<Map<String, Object>> getAdjustmentStatistics(
            @RequestParam(required = false) String strategyCode) {
        Map<String, Object> statistics = pythonOptimizerService.getAdjustmentStatistics(strategyCode);
        return Result.success(statistics);
    }
}
