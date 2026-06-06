package com.ems.service;

import com.ems.domain.dto.strategy.*;
import com.ems.domain.vo.strategy.StrategyResultVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 多目标优化服务接口
 * 提供综合考虑套利收益、寿命损耗、需量费用的多目标优化算法
 *
 * 多目标优化算法核心思想：
 * 将多个目标函数通过加权求和转化为单目标优化问题：
 *
 * 总目标 = w1 × 归一化套利收益 + w2 × 归一化寿命得分 + w3 × 归一化需量得分
 *
 * 其中：
 * - w1 + w2 + w3 = 1 （权重归一化）
 * - 各目标得分均归一化到 [0, 1] 区间
 *
 * 目标函数详细说明：
 *
 * 1. 套利收益目标（最大化）：
 *    套利收益 = Σ(小时放电能量 × 放电电价 - 小时充电能量 × 充电电价)
 *    归一化：实际收益 / 理论最大收益
 *
 * 2. 寿命损耗目标（最大化寿命得分 = 最小化衰减）：
 *    寿命得分 = 1 - 预计衰减量 / 最大允许衰减量
 *    衰减成本 = f(充放电倍率, 放电深度, 温度, SOC)
 *
 * 3. 需量费用目标（最大化需量得分 = 最小化需量）：
 *    需量得分 = 1 - 预计最大需量 / 需量阈值
 *    需量费用 = max(0, 最大需量 - 需量阈值) × 需量电价
 *
 * 约束条件：
 * - SOC 约束：minSoc ≤ SOC(t) ≤ maxSoc
 * - 功率约束：|P(t)| ≤ maxPower × 充放电倍率
 * - 能量守恒：SOC(t+1) = SOC(t) + P(t) × 效率 / 容量
 * - 循环约束：日循环次数 ≤ maxDailyCycles
 * - 放电深度：单次放电深度 ≤ maxDepthOfDischarge
 *
 * @author EMS Team
 * @since 1.0.0
 */
public interface MultiObjectiveOptimizationService {

    StrategyResultVO optimize(RealTimeControlRequest request, StrategyConfigDTO config);

    DispatchPlanDTO optimizeDayAheadPlan(StrategyGenerateRequest request, StrategyConfigDTO config);

    BigDecimal calculateNormalizedArbitrageScore(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config);

    BigDecimal calculateNormalizedLifespanScore(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config);

    BigDecimal calculateNormalizedDemandScore(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config);

    BigDecimal calculateTotalObjectiveScore(BigDecimal arbitrageScore, BigDecimal lifespanScore,
                                             BigDecimal demandScore, StrategyConfigDTO config);

    BigDecimal calculateArbitrageRevenue(BigDecimal price, BigDecimal power, BigDecimal efficiency);

    BigDecimal calculateDegradationCost(BigDecimal chargeRate, BigDecimal dischargeRate,
                                         BigDecimal depthOfDischarge, BigDecimal temperature,
                                         StrategyConfigDTO config);

    BigDecimal calculateDemandCost(BigDecimal demand, BigDecimal threshold, BigDecimal demandPrice);

    Map<String, BigDecimal> calculateOptimalPower(BigDecimal currentPrice, BigDecimal currentLoad,
                                                   BigDecimal currentSoc, BigDecimal predictedDemand,
                                                   StrategyConfigDTO config);

    boolean checkBatteryConstraints(BigDecimal power, BigDecimal currentSoc, StrategyConfigDTO config);

    boolean checkDemandConstraints(BigDecimal newDemand, StrategyConfigDTO config);

    BigDecimal calculateLifespanPenalty(BigDecimal chargeRate, BigDecimal dischargeRate,
                                         BigDecimal depthOfDischarge, BigDecimal temperature,
                                         StrategyConfigDTO config);

    Map<String, BigDecimal> calculateWeightedScores(BigDecimal arbitrageValue, BigDecimal lifespanValue,
                                                    BigDecimal demandValue, StrategyConfigDTO config);

    List<DispatchPlanHourDTO> optimizeDispatchPlan(List<PriceForecastDTO> priceForecast,
                                                    List<LoadForecastDTO> loadForecast,
                                                    BigDecimal initialSoc,
                                                    StrategyConfigDTO config);

    BigDecimal calculateExpectedSoc(BigDecimal currentSoc, BigDecimal power, BigDecimal energy,
                                     BigDecimal chargeEfficiency, BigDecimal dischargeEfficiency,
                                     BigDecimal batteryCapacity);

    Map<String, BigDecimal> getParetoFrontier(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config);

    StrategyResultVO resolveTradeOffs(RealTimeControlRequest request, StrategyConfigDTO config,
                                       Map<String, BigDecimal> scores);
}
