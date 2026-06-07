package com.ems.task;

import com.ems.domain.entity.StrategyConfig;
import com.ems.repository.StrategyConfigRepository;
import com.ems.service.RealTimeStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 手动控制超时自动恢复定时任务
 * 主动扫描所有处于手动模式的策略，检查是否超时，到期自动发送停止命令并恢复自动模式
 *
 * 定时策略：
 * - 每30秒执行一次：cron = "0/30 * * * * ?"
 * - 不依赖用户点击执行策略控制，主动检测并恢复
 *
 * 业务逻辑：
 * 1. 获取所有处于 MANUAL 模式且设置了 manualStartTime 和 manualDuration 的策略
 * 2. 检查当前时间是否超过 manualStartTime + manualDuration
 * 3. 对超时的策略发送 STOP 命令到设备
 * 4. 写入 strategy_execution_log 记录表
 * 5. 切换回 AUTO 模式，清空手动控制参数
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualControlTimeoutTask {

    private final StrategyConfigRepository strategyConfigRepository;
    private final RealTimeStrategyService realTimeStrategyService;

    /**
     * 每30秒扫描一次手动控制超时情况
     * 使用cron表达式：秒 分 时 日 月 周
     * "0/30 * * * * ?" 表示每分钟的00秒和30秒执行
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void checkAndExpireManualControls() {
        log.debug("========== 开始扫描手动控制超时情况 ==========");
        long startTime = System.currentTimeMillis();

        try {
            List<StrategyConfig> manualConfigs = strategyConfigRepository.findAllActiveManualControls();
            if (manualConfigs == null || manualConfigs.isEmpty()) {
                log.debug("没有处于手动模式的策略，跳过本次扫描");
                return;
            }

            int expiredCount = 0;
            int activeCount = 0;

            for (StrategyConfig config : manualConfigs) {
                try {
                    if (config.getManualStartTime() == null || config.getManualDuration() == null) {
                        continue;
                    }

                    LocalDateTime endTime = config.getManualStartTime().plusSeconds(config.getManualDuration());
                    LocalDateTime now = LocalDateTime.now();

                    if (now.isAfter(endTime)) {
                        log.info("检测到手动控制超时 - 策略: {}, 开始时间: {}, 时长: {}秒, 结束时间: {}",
                                config.getStrategyCode(), config.getManualStartTime(),
                                config.getManualDuration(), endTime);

                        realTimeStrategyService.expireManualControl(config.getStrategyCode());
                        expiredCount++;
                    } else {
                        long remainingSeconds = java.time.Duration.between(now, endTime).getSeconds();
                        log.debug("手动控制仍在有效期 - 策略: {}, 剩余时间: {}秒",
                                config.getStrategyCode(), remainingSeconds);
                        activeCount++;
                    }
                } catch (Exception e) {
                    log.error("处理手动控制超时失败 - 策略: {}", config.getStrategyCode(), e);
                }
            }

            long totalCostTime = System.currentTimeMillis() - startTime;
            log.info("========== 手动控制超时扫描完成，超时: {}个, 有效: {}个, 总耗时: {}ms ==========",
                    expiredCount, activeCount, totalCostTime);

        } catch (Exception e) {
            long totalCostTime = System.currentTimeMillis() - startTime;
            log.error("========== 手动控制超时扫描异常，耗时: {}ms ==========", totalCostTime, e);
        }
    }
}
