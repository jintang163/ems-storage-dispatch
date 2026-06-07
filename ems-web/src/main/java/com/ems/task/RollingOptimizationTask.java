package com.ems.task;

import com.ems.domain.dto.strategy.StrategyConfigDTO;
import com.ems.service.DispatchPlanService;
import com.ems.service.PythonOptimizerService;
import com.ems.service.RealTimeStrategyService;
import com.ems.service.StrategyConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 滚动优化定时任务
 * 每15分钟自动调用Python优化服务，基于最新数据重新计算充放电计划
 *
 * 定时策略：
 * - 每15分钟执行一次：cron = "0 */15 * * * ?"
 * - 滚动优化从当前小时开始，只优化未来时段
 *
 * 业务逻辑：
 * 1. 获取所有已启用的调度策略
 * 2. 检查Python优化服务健康状态
 * 3. 对每个策略调用滚动优化
 * 4. 记录优化结果和耗时
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RollingOptimizationTask {

    private final DispatchPlanService dispatchPlanService;
    private final StrategyConfigService strategyConfigService;
    private final PythonOptimizerService pythonOptimizerService;
    private final RealTimeStrategyService realTimeStrategyService;

    /**
     * 每15分钟执行一次滚动优化
     * 使用cron表达式：秒 分 时 日 月 周
     * "0 */15 * * * ?" 表示每15分钟的00秒执行
     *
     * 优化流程：
     * 1. 检查Python优化服务是否可用
     * 2. 获取所有启用的策略
     * 3. 对每个策略从当前小时开始滚动优化
     * 4. 保存优化结果到数据库
     */
    @Scheduled(cron = "0 */15 * * * ?")
    public void executeRollingOptimization() {
        log.info("========== 开始执行滚动优化定时任务 ==========");
        long startTime = System.currentTimeMillis();

        try {
            if (!pythonOptimizerService.isHealthy()) {
                log.warn("Python优化服务不可用，跳过本次滚动优化");
                return;
            }

            List<StrategyConfigDTO> strategies = strategyConfigService.listAll();
            if (strategies == null || strategies.isEmpty()) {
                log.warn("没有可用的调度策略，跳过本次滚动优化");
                return;
            }

            LocalDate today = LocalDate.now();
            int currentHour = LocalDateTime.now().getHour();
            int successCount = 0;
            int failCount = 0;

            for (StrategyConfigDTO strategy : strategies) {
                if (!"ENABLED".equals(strategy.getStatus())) {
                    log.debug("策略[{}]未启用，跳过", strategy.getStrategyCode());
                    continue;
                }

                if (realTimeStrategyService.isManualModeActive(strategy.getStrategyCode())) {
                    log.warn("策略[{}]处于手动模式，跳过滚动优化", strategy.getStrategyCode());
                    continue;
                }

                try {
                    log.info("开始优化策略: {}, 开始时段: {}", strategy.getStrategyCode(), currentHour);
                    long strategyStartTime = System.currentTimeMillis();

                    dispatchPlanService.generateRollingPlanWithPython(
                            strategy.getStrategyCode(),
                            today,
                            currentHour
                    );

                    long strategyCostTime = System.currentTimeMillis() - strategyStartTime;
                    log.info("策略[{}]优化成功，耗时: {}ms", strategy.getStrategyCode(), strategyCostTime);
                    successCount++;
                } catch (Exception e) {
                    log.error("策略[{}]优化失败", strategy.getStrategyCode(), e);
                    failCount++;
                }
            }

            long totalCostTime = System.currentTimeMillis() - startTime;
            log.info("========== 滚动优化任务执行完成，成功: {}个, 失败: {}个, 总耗时: {}ms ==========",
                    successCount, failCount, totalCostTime);

        } catch (Exception e) {
            long totalCostTime = System.currentTimeMillis() - startTime;
            log.error("========== 滚动优化任务执行异常，耗时: {}ms ==========", totalCostTime, e);
        }
    }

    /**
     * 每小时执行一次完整的日计划优化
     * 使用cron表达式："0 0 * * * ?" 表示每小时的00分00秒执行
     *
     * 相比15分钟优化，这里从0点开始重新计算完整的24小时计划
     * 用于修正较大的预测偏差
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void executeFullHourlyOptimization() {
        log.info("========== 开始执行每小时完整优化任务 ==========");
        long startTime = System.currentTimeMillis();

        try {
            if (!pythonOptimizerService.isHealthy()) {
                log.warn("Python优化服务不可用，跳过本次完整优化");
                return;
            }

            List<StrategyConfigDTO> strategies = strategyConfigService.listAll();
            if (strategies == null || strategies.isEmpty()) {
                log.warn("没有可用的调度策略，跳过本次完整优化");
                return;
            }

            LocalDate today = LocalDate.now();
            int successCount = 0;
            int failCount = 0;

            for (StrategyConfigDTO strategy : strategies) {
                if (!"ENABLED".equals(strategy.getStatus())) {
                    continue;
                }

                if (realTimeStrategyService.isManualModeActive(strategy.getStrategyCode())) {
                    log.warn("策略[{}]处于手动模式，跳过完整优化", strategy.getStrategyCode());
                    continue;
                }

                try {
                    dispatchPlanService.generateRollingPlanWithPython(
                            strategy.getStrategyCode(),
                            today,
                            0
                    );
                    successCount++;
                } catch (Exception e) {
                    log.error("策略[{}]完整优化失败", strategy.getStrategyCode(), e);
                    failCount++;
                }
            }

            long totalCostTime = System.currentTimeMillis() - startTime;
            log.info("========== 每小时完整优化任务执行完成，成功: {}个, 失败: {}个, 总耗时: {}ms ==========",
                    successCount, failCount, totalCostTime);

        } catch (Exception e) {
            long totalCostTime = System.currentTimeMillis() - startTime;
            log.error("========== 每小时完整优化任务执行异常，耗时: {}ms ==========", totalCostTime, e);
        }
    }
}
