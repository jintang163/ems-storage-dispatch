package com.ems.service;

import com.ems.domain.dto.strategy.DispatchPlanDTO;
import com.ems.domain.dto.strategy.StrategyGenerateRequest;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.domain.vo.strategy.StrategyStatisticsVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 调度计划服务接口
 * 提供24小时充放电计划的生成、查询和管理功能
 *
 * 核心功能：
 * 1. 日内调度计划生成：基于电价预测和负荷预测生成24小时充放电计划
 * 2. 峰谷套利策略：低价充电、高价放电，实现价差收益最大化
 * 3. 削峰填谷策略：高峰放电、低谷充电，平抑负荷波动
 * 4. 需量控制策略：预测需量超阈值时，优先放电抑制需量超标
 * 5. 多目标优化：综合考虑套利收益、寿命损耗、需量费用
 *
 * 调度计划生成算法：
 * 1. 数据收集：获取电价预测、负荷预测、电池参数、策略配置
 * 2. 约束建模：SOC约束、功率约束、寿命约束、需量约束
 * 3. 目标函数：最大化综合收益 = 套利收益 - 衰减成本 - 需量罚款
 * 4. 优化求解：使用线性规划或动态规划求解最优充放电序列
 * 5. 结果校验：校验SOC连续性、功率限制、寿命约束等
 *
 * @author EMS Team
 * @since 1.0.0
 */
public interface DispatchPlanService {

    DispatchPlanDTO generatePlan(StrategyGenerateRequest request);

    DispatchPlanDTO regeneratePlan(Long planId);

    DispatchPlanDTO getById(Long id);

    DispatchPlanDTO getLatestPendingPlan(String strategyCode, LocalDate planDate);

    List<DispatchPlanDTO> listByStrategyId(Long strategyId);

    List<DispatchPlanDTO> listByPlanDate(LocalDate planDate);

    List<DispatchPlanDTO> listByDateRange(LocalDate startDate, LocalDate endDate);

    List<DispatchPlanDTO> listByStatus(String status);

    void executePlan(Long planId);

    void cancelPlan(Long planId);

    void approvePlan(Long planId, String approvedBy);

    StrategyResultVO executeCurrentHour(String strategyCode, String batterySn);

    Map<String, BigDecimal> calculateExpectedBenefits(Long planId);

    StrategyStatisticsVO getStatisticsByDate(LocalDate date, String strategyCode);

    List<StrategyStatisticsVO> getStatisticsByDateRange(LocalDate startDate, LocalDate endDate, String strategyCode);

    Map<String, Object> getPlanStatusSummary();

    Map<String, BigDecimal> getTotalExpectedBenefits(LocalDate startDate, LocalDate endDate);

    List<DispatchPlanDTO> listPendingPlans();

    DispatchPlanDTO generateRollingPlan(String strategyCode, LocalDate planDate, int startHour);

    DispatchPlanDTO generateRollingPlanWithPython(String strategyCode, LocalDate planDate, int startHour);

    StrategyResultVO executeRealTimeAdjustWithPython(String strategyCode, String batterySn,
                                                      BigDecimal currentSoc, BigDecimal expectedSoc,
                                                      BigDecimal currentLoad, BigDecimal forecastLoad,
                                                      BigDecimal plannedPower);
}
