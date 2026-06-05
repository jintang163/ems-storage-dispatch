package com.ems.task;

import com.ems.service.TimeOfUsePriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 电价数据同步定时任务
 * 每日凌晨自动从电力公司拉取最新的分时电价数据
 *
 * 定时策略：
 * - 每天00:30执行一次
 * - 确保获取最新的电价政策（电力公司通常在凌晨更新电价）
 * - cron表达式：秒 分 时 日 月 周
 * - "0 30 0 * * ?" 表示每天00:30:00执行
 *
 * 业务逻辑：
 * 1. 调用电力公司API获取最新分时电价
 * 2. 禁用本地所有旧电价记录
 * 3. 保存新电价到数据库
 * 4. 记录同步日志，便于后续审计
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceSyncTask {

    private final TimeOfUsePriceService priceService;

    /**
     * 每日凌晨00:30自动同步电力公司电价
     * 使用cron表达式：秒 分 时 日 月 周
     * "0 30 0 * * ?" 表示每天00:30:00执行
     *
     * 同步流程：
     * 1. 调用fetchFromPowerCompany()获取最新电价
     * 2. 禁用本地所有已有的电价记录
     * 3. 将新电价保存到数据库
     * 4. 记录同步结果日志
     */
    @Scheduled(cron = "0 30 0 * * ?")
    public void syncPriceDaily() {
        log.info("========== 开始执行每日电价同步任务 ==========");
        long startTime = System.currentTimeMillis();

        try {
            priceService.syncFromPowerCompany();
            long costTime = System.currentTimeMillis() - startTime;
            log.info("========== 电价同步任务执行成功，耗时：{}ms ==========", costTime);
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("========== 电价同步任务执行失败，耗时：{}ms ==========", costTime, e);
            // 实际项目中可以添加告警通知（邮件、短信、企业微信等）
        }
    }

    /**
     * 每周一00:00执行一次电价数据校验
     * 检查本地电价数据是否完整，避免同步失败导致数据缺失
     */
    @Scheduled(cron = "0 0 0 ? * MON")
    public void validatePriceDataWeekly() {
        log.info("========== 开始执行每周电价数据校验 ==========");
        try {
            int validPriceCount = priceService.listValidPrices().size();
            if (validPriceCount < 8) {
                log.warn("有效电价数据不足，当前数量：{}，建议手动执行同步", validPriceCount);
                // 实际项目中可以添加告警通知
            } else {
                log.info("电价数据校验通过，有效电价数量：{}", validPriceCount);
            }
            log.info("========== 电价数据校验完成 ==========");
        } catch (Exception e) {
            log.error("电价数据校验失败", e);
        }
    }
}
