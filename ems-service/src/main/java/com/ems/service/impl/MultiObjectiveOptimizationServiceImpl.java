package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.strategy.*;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.service.ForecastService;
import com.ems.service.MultiObjectiveOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * 多目标优化服务实现类
 * 提供综合考虑套利收益、寿命损耗、需量费用的多目标优化算法
 *
 * 核心算法：
 * 1. 归一化处理：将各目标值映射到[0,1]区间
 * 2. 加权求和：使用权重计算综合目标函数
 * 3. 约束满足：检查并满足所有约束条件
 * 4. 动态调整：根据实时情况动态调整权重和策略
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiObjectiveOptimizationServiceImpl implements MultiObjectiveOptimizationService {

    private static final BigDecimal MAX_C_RATE = new BigDecimal("2.0");
    private static final BigDecimal MIN_SOC = new BigDecimal("0");
    private static final BigDecimal MAX_SOC = new BigDecimal("100");
    private static final BigDecimal DEFAULT_CHARGE_EFFICIENCY = new BigDecimal("0.95");
    private static final BigDecimal DEFAULT_DISCHARGE_EFFICIENCY = new BigDecimal("0.95");
    private static final BigDecimal DEFAULT_BATTERY_CAPACITY = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_DEMAND_PRICE = new BigDecimal("35");
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("24");
    private static final BigDecimal AVG_PRICE_REFERENCE = new BigDecimal("0.55");

    private final ForecastService forecastService;

    @Override
    public StrategyResultVO optimize(RealTimeControlRequest request, StrategyConfigDTO config) {
        log.debug("执行多目标优化 - 策略: {}, 当前SOC: {}, 当前负荷: {}",
                config.getStrategyCode(), request.getCurrentSoc(), request.getCurrentLoad());

        StrategyResultVO result = new StrategyResultVO();
        result.setStrategyCode(config.getStrategyCode());
        result.setStrategyName(config.getStrategyName());

        BigDecimal currentPrice = request.getCurrentPrice();
        BigDecimal currentLoad = request.getCurrentLoad() != null ? request.getCurrentLoad() : BigDecimal.ZERO;
        BigDecimal currentSoc = request.getCurrentSoc() != null ? request.getCurrentSoc() : new BigDecimal("50");
        BigDecimal predictedDemand = request.getCurrentDemand() != null ? request.getCurrentDemand() : currentLoad;
        BigDecimal currentPv = request.getCurrentPv() != null ? request.getCurrentPv() : BigDecimal.ZERO;

        Map<String, BigDecimal> optimalPower = calculateOptimalPower(
                currentPrice, currentLoad, currentSoc, predictedDemand, config);

        BigDecimal targetPower = optimalPower.get("targetPower");
        BigDecimal arbitrageScore = optimalPower.get("arbitrageScore");
        BigDecimal lifespanScore = optimalPower.get("lifespanScore");
        BigDecimal demandScore = optimalPower.get("demandScore");
        BigDecimal totalScore = optimalPower.get("totalScore");

        String actionType;
        if (targetPower.compareTo(BigDecimal.ZERO) > 0) {
            actionType = "CHARGE";
        } else if (targetPower.compareTo(BigDecimal.ZERO) < 0) {
            actionType = "DISCHARGE";
        } else {
            actionType = "HOLD";
        }

        result.setActionType(actionType);
        result.setTargetPower(targetPower.abs());
        result.setExpectedSoc(calculateExpectedSoc(currentSoc, targetPower, targetPower.abs().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP),
                DEFAULT_CHARGE_EFFICIENCY, DEFAULT_DISCHARGE_EFFICIENCY, DEFAULT_BATTERY_CAPACITY));

        BigDecimal expectedRevenue = calculateArbitrageRevenue(currentPrice, targetPower,
                targetPower.compareTo(BigDecimal.ZERO) > 0 ? DEFAULT_CHARGE_EFFICIENCY : DEFAULT_DISCHARGE_EFFICIENCY);
        result.setExpectedRevenue(expectedRevenue.max(BigDecimal.ZERO));

        BigDecimal chargeRate = targetPower.abs().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP);
        BigDecimal degradationCost = calculateDegradationCost(
                targetPower.compareTo(BigDecimal.ZERO) > 0 ? chargeRate : BigDecimal.ZERO,
                targetPower.compareTo(BigDecimal.ZERO) < 0 ? chargeRate : BigDecimal.ZERO,
                BigDecimal.ZERO,
                request.getBatteryTemperature() != null ? request.getBatteryTemperature() : new BigDecimal("25"),
                config);
        result.setExpectedDegradationCost(degradationCost);

        BigDecimal demandThreshold = currentLoad.multiply(config.getDemandThresholdRatio());
        BigDecimal newDemand = currentLoad.add(targetPower).subtract(currentPv).max(BigDecimal.ZERO);
        BigDecimal demandSaving = calculateDemandSaving(predictedDemand, newDemand, demandThreshold);
        result.setExpectedDemandSaving(demandSaving);

        result.setTotalObjectiveScore(totalScore);
        result.setArbitrageScore(arbitrageScore);
        result.setLifespanScore(lifespanScore);
        result.setDemandScore(demandScore);

        String urgencyLevel = determineUrgencyLevel(predictedDemand, demandThreshold, config);
        result.setUrgencyLevel(urgencyLevel);

        List<String> actions = generateRecommendedActions(targetPower, currentSoc, predictedDemand, demandThreshold, config);
        result.setRecommendedActions(actions);

        result.setStatus("success");
        result.setMessage("多目标优化完成");

        log.debug("多目标优化结果 - 动作: {}, 功率: {} kW, 综合得分: {}",
                actionType, targetPower, totalScore);

        return result;
    }

    /**
     * <p>日前多目标优化计划生成
     * <p>核心逻辑：
     * <ol>
     *   <li>从ForecastService获取真实的电价预测和负荷预测数据（优先使用数据库中的分时电价表）</li>
     *   <li>逐小时进行多目标优化，计算最优充放电功率</li>
     *   <li>累计全天收益、损耗和需量节省</li>
     *   <li>计算归一化的三项目标得分（套利/寿命/需量）</li>
     *   <li>根据权重计算综合目标得分</li>
     * </ol>
     *
     * @param request 计划生成请求，包含策略编码、日期、初始SOC等参数
     * @param config  策略配置，包含权重、约束参数等
     * @return 完整的24小时调度计划DTO
     */
    @Override
    public DispatchPlanDTO optimizeDayAheadPlan(StrategyGenerateRequest request, StrategyConfigDTO config) {
        log.info("执行日前多目标优化 - 策略: {}, 日期: {}", config.getStrategyCode(), request.getPlanDate());

        DispatchPlanDTO plan = new DispatchPlanDTO();
        plan.setStrategyId(config.getId());
        plan.setStrategyCode(config.getStrategyCode());
        plan.setPlanDate(request.getPlanDate());
        plan.setPlanType("DAY_AHEAD");
        plan.setBatterySn(request.getBatterySn());
        plan.setTransformerCode(request.getTransformerCode());
        plan.setInitialSoc(request.getInitialSoc() != null ? request.getInitialSoc() : new BigDecimal("50"));
        plan.setStatus("pending");
        plan.setCreatedBy(request.getCreatedBy());

        LocalDate planDate = request.getPlanDate() != null ? request.getPlanDate() : LocalDate.now();

        List<PriceForecastDTO> priceForecast;
        List<LoadForecastDTO> loadForecast;

        if (Boolean.TRUE.equals(request.getUsePriceForecast())) {
            log.debug("使用AI预测电价数据 - 日期: {}", planDate);
            priceForecast = forecastService.generatePriceForecast(planDate, "AI_FORECAST");
        } else {
            log.debug("使用分时电价表生成电价预测 - 日期: {}", planDate);
            priceForecast = forecastService.generatePriceForecastByTou(planDate);
        }

        if (Boolean.TRUE.equals(request.getUseLoadForecast()) && request.getTransformerCode() != null) {
            log.debug("使用负荷预测数据 - 日期: {}, 变压器: {}", planDate, request.getTransformerCode());
            loadForecast = forecastService.generateLoadForecast(planDate, request.getTransformerCode());
        } else {
            log.debug("使用历史相似日负荷数据 - 日期: {}", planDate);
            loadForecast = forecastService.generateLoadForecast(planDate, request.getTransformerCode());
        }

        List<DispatchPlanHourDTO> planHours = optimizeDispatchPlan(
                priceForecast, loadForecast, plan.getInitialSoc(), config);

        plan.setPlanHours(planHours);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalDegradation = BigDecimal.ZERO;
        BigDecimal totalDemandSaving = BigDecimal.ZERO;

        for (DispatchPlanHourDTO hour : planHours) {
            if (hour.getRevenue() != null) {
                totalRevenue = totalRevenue.add(hour.getRevenue());
            }
            if (hour.getDegradationCost() != null) {
                totalDegradation = totalDegradation.add(hour.getDegradationCost());
            }
            if (hour.getDemandSaving() != null) {
                totalDemandSaving = totalDemandSaving.add(hour.getDemandSaving());
            }
        }

        plan.setExpectedRevenue(totalRevenue);
        plan.setExpectedDegradation(totalDegradation);
        plan.setExpectedDemandSaving(totalDemandSaving);

        BigDecimal arbitrageScore = calculateNormalizedArbitrageScore(planHours, config);
        BigDecimal lifespanScore = calculateNormalizedLifespanScore(planHours, config);
        BigDecimal demandScore = calculateNormalizedDemandScore(planHours, config);
        BigDecimal totalScore = calculateTotalObjectiveScore(arbitrageScore, lifespanScore, demandScore, config);

        plan.setArbitrageScore(arbitrageScore);
        plan.setLifespanScore(lifespanScore);
        plan.setDemandScore(demandScore);
        plan.setTotalObjectiveScore(totalScore);

        log.info("日前优化完成 - 预期收益: {}, 衰减成本: {}, 需量节省: {}, 综合得分: {}",
                totalRevenue, totalDegradation, totalDemandSaving, totalScore);

        return plan;
    }

    @Override
    public BigDecimal calculateNormalizedArbitrageScore(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config) {
        if (planHours == null || planHours.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxPrice = BigDecimal.ZERO;
        BigDecimal minPrice = new BigDecimal("999");

        for (DispatchPlanHourDTO hour : planHours) {
            if (hour.getPrice() != null) {
                if (hour.getPrice().compareTo(maxPrice) > 0) {
                    maxPrice = hour.getPrice();
                }
                if (hour.getPrice().compareTo(minPrice) < 0) {
                    minPrice = hour.getPrice();
                }
            }
        }

        BigDecimal priceSpread = maxPrice.subtract(minPrice);
        if (priceSpread.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.5");
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal theoreticalMaxRevenue = BigDecimal.ZERO;

        BigDecimal batteryCapacity = DEFAULT_BATTERY_CAPACITY;
        BigDecimal maxDailyCycles = config.getMaxDailyCycles() != null ? config.getMaxDailyCycles() : BigDecimal.ONE;
        BigDecimal maxEnergy = batteryCapacity.multiply(maxDailyCycles);

        for (DispatchPlanHourDTO hour : planHours) {
            if (hour.getRevenue() != null) {
                totalRevenue = totalRevenue.add(hour.getRevenue());
            }
            if (hour.getEnergy() != null && hour.getPrice() != null) {
                if (hour.getPower().compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal priceDiff = maxPrice.subtract(minPrice);
                    theoreticalMaxRevenue = theoreticalMaxRevenue.add(
                            hour.getEnergy().abs().multiply(priceDiff).multiply(DEFAULT_DISCHARGE_EFFICIENCY));
                }
            }
        }

        if (theoreticalMaxRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            theoreticalMaxRevenue = maxEnergy.multiply(priceSpread).multiply(DEFAULT_DISCHARGE_EFFICIENCY);
        }

        if (theoreticalMaxRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.5");
        }

        BigDecimal score = totalRevenue.divide(theoreticalMaxRevenue, 4, RoundingMode.HALF_UP);
        return score.min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal calculateNormalizedLifespanScore(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config) {
        if (planHours == null || planHours.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalDegradation = BigDecimal.ZERO;
        BigDecimal maxAllowedDegradation = BigDecimal.ZERO;

        BigDecimal maxChargeRate = config.getMaxChargeRate() != null ? config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ? config.getMaxDischargeRate() : new BigDecimal("0.5");
        BigDecimal maxDod = config.getMaxDepthOfDischarge() != null ? config.getMaxDepthOfDischarge() : new BigDecimal("70");

        for (DispatchPlanHourDTO hour : planHours) {
            if (hour.getDegradationCost() != null) {
                totalDegradation = totalDegradation.add(hour.getDegradationCost());
            }

            BigDecimal rate = hour.getChargeRate() != null ? hour.getChargeRate() : BigDecimal.ZERO;
            BigDecimal dod = hour.getDepthOfDischarge() != null ? hour.getDepthOfDischarge() : BigDecimal.ZERO;

            BigDecimal hourPenalty = calculateLifespanPenalty(
                    hour.getPower().compareTo(BigDecimal.ZERO) > 0 ? rate : BigDecimal.ZERO,
                    hour.getPower().compareTo(BigDecimal.ZERO) < 0 ? rate : BigDecimal.ZERO,
                    dod,
                    new BigDecimal("25"),
                    config);

            maxAllowedDegradation = maxAllowedDegradation.add(
                    calculateLifespanPenalty(maxChargeRate, maxDischargeRate, maxDod, new BigDecimal("40"), config));
        }

        if (maxAllowedDegradation.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }

        BigDecimal score = BigDecimal.ONE.subtract(totalDegradation.divide(maxAllowedDegradation, 4, RoundingMode.HALF_UP));
        return score.min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal calculateNormalizedDemandScore(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config) {
        if (planHours == null || planHours.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal demandThresholdRatio = config.getDemandThresholdRatio() != null ?
                config.getDemandThresholdRatio() : new BigDecimal("0.9");

        BigDecimal maxDemand = BigDecimal.ZERO;
        BigDecimal threshold = BigDecimal.ZERO;
        BigDecimal totalDemandSaving = BigDecimal.ZERO;

        for (DispatchPlanHourDTO hour : planHours) {
            if (hour.getForecastLoad() != null && hour.getForecastLoad().compareTo(maxDemand) > 0) {
                maxDemand = hour.getForecastLoad();
                threshold = maxDemand.multiply(demandThresholdRatio);
            }
            if (hour.getDemandSaving() != null) {
                totalDemandSaving = totalDemandSaving.add(hour.getDemandSaving());
            }
        }

        if (threshold.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.5");
        }

        BigDecimal demandAfterControl = maxDemand.subtract(totalDemandSaving).max(BigDecimal.ZERO);

        BigDecimal score;
        if (demandAfterControl.compareTo(threshold) <= 0) {
            score = BigDecimal.ONE;
        } else {
            BigDecimal overage = demandAfterControl.subtract(threshold);
            BigDecimal maxOverage = threshold.multiply(new BigDecimal("0.5"));
            score = BigDecimal.ONE.subtract(overage.divide(maxOverage, 4, RoundingMode.HALF_UP));
        }

        return score.min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal calculateTotalObjectiveScore(BigDecimal arbitrageScore, BigDecimal lifespanScore,
                                                    BigDecimal demandScore, StrategyConfigDTO config) {
        BigDecimal arbitrageWeight = config.getArbitrageWeight() != null ? config.getArbitrageWeight() : new BigDecimal("0.5");
        BigDecimal lifespanWeight = config.getLifespanWeight() != null ? config.getLifespanWeight() : new BigDecimal("0.3");
        BigDecimal demandWeight = config.getDemandWeight() != null ? config.getDemandWeight() : new BigDecimal("0.2");

        BigDecimal totalWeight = arbitrageWeight.add(lifespanWeight).add(demandWeight);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        arbitrageWeight = arbitrageWeight.divide(totalWeight, 4, RoundingMode.HALF_UP);
        lifespanWeight = lifespanWeight.divide(totalWeight, 4, RoundingMode.HALF_UP);
        demandWeight = demandWeight.divide(totalWeight, 4, RoundingMode.HALF_UP);

        BigDecimal totalScore = arbitrageScore.multiply(arbitrageWeight)
                .add(lifespanScore.multiply(lifespanWeight))
                .add(demandScore.multiply(demandWeight));

        return totalScore.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateArbitrageRevenue(BigDecimal price, BigDecimal power, BigDecimal efficiency) {
        if (price == null || power == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal energy = power.abs().divide(HOURS_PER_DAY, 6, RoundingMode.HALF_UP);

        if (power.compareTo(BigDecimal.ZERO) > 0) {
            return energy.multiply(price).negate();
        } else {
            return energy.multiply(price).multiply(efficiency);
        }
    }

    @Override
    public BigDecimal calculateDegradationCost(BigDecimal chargeRate, BigDecimal dischargeRate,
                                                BigDecimal depthOfDischarge, BigDecimal temperature,
                                                StrategyConfigDTO config) {
        BigDecimal penalty = calculateLifespanPenalty(chargeRate, dischargeRate, depthOfDischarge, temperature, config);
        BigDecimal batteryCost = DEFAULT_BATTERY_CAPACITY.multiply(new BigDecimal("1500"));
        return penalty.multiply(batteryCost).divide(new BigDecimal("10000"), 4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateDemandCost(BigDecimal demand, BigDecimal threshold, BigDecimal demandPrice) {
        if (demand == null || threshold == null) {
            return BigDecimal.ZERO;
        }

        if (demand.compareTo(threshold) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal excess = demand.subtract(threshold);
        return excess.multiply(demandPrice != null ? demandPrice : DEFAULT_DEMAND_PRICE);
    }

    /**
     * <p>计算最优充放电功率 - 核心优化算法
     * <p>算法原理：遍历41个可能的功率值（-1.0C 到 +1.0C，步长0.05C），
     * 对每个功率计算三项目标得分并加权求和，选择综合得分最高的功率。
     *
     * <p>计算流程：
     * <ol>
     *   <li>从策略配置中获取约束参数（最大充放电倍率、SOC范围、需量阈值等）</li>
     *   <li>计算功率搜索范围：从最大放电功率到最大充电功率，共41个采样点</li>
     *   <li>对每个测试功率：
     *     <ul>
     *       <li>检查电池约束（SOC范围、充放电倍率限制）</li>
     *       <li>计算新的需量值（原始负荷 + 充放电功率）</li>
     *       <li>计算套利收益：根据当前电价和充放电功率计算</li>
     *       <li>计算寿命损耗成本：考虑充放电倍率、温度等因素</li>
     *       <li>计算三项目标归一化得分：[0,1]区间</li>
     *       <li>根据权重计算综合目标得分</li>
     *     </ul>
     *   </li>
     *   <li>选择综合得分最高的功率作为最优解</li>
     * </ol>
     *
     * @param currentPrice     当前电价（元/kWh），用于计算套利收益
     * @param currentLoad      当前负荷（kW），原始用电负荷
     * @param currentSoc       当前SOC（%），电池当前荷电状态
     * @param predictedDemand  预测需量（kW），滑动窗口预测的最大需量
     * @param config           策略配置，包含权重和约束参数
     * @return 包含最优功率、各项目标得分的Map
     */
    @Override
    public Map<String, BigDecimal> calculateOptimalPower(BigDecimal currentPrice, BigDecimal currentLoad,
                                                          BigDecimal currentSoc, BigDecimal predictedDemand,
                                                          StrategyConfigDTO config) {
        Map<String, BigDecimal> result = new HashMap<>();

        BigDecimal maxChargeRate = config.getMaxChargeRate() != null ? config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ? config.getMaxDischargeRate() : new BigDecimal("0.5");
        BigDecimal minSoc = config.getMinSoc() != null ? config.getMinSoc() : new BigDecimal("20");
        BigDecimal maxSoc = config.getMaxSoc() != null ? config.getMaxSoc() : new BigDecimal("90");
        BigDecimal demandThresholdRatio = config.getDemandThresholdRatio() != null ?
                config.getDemandThresholdRatio() : new BigDecimal("0.9");

        BigDecimal demandThreshold = currentLoad.multiply(demandThresholdRatio);

        BigDecimal maxChargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxChargeRate);
        BigDecimal maxDischargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxDischargeRate);

        BigDecimal bestPower = BigDecimal.ZERO;
        BigDecimal bestTotalScore = BigDecimal.ZERO;
        BigDecimal bestArbitrageScore = BigDecimal.ZERO;
        BigDecimal bestLifespanScore = BigDecimal.ZERO;
        BigDecimal bestDemandScore = BigDecimal.ZERO;

        int steps = 41;
        BigDecimal stepSize = maxChargePower.add(maxDischargePower).divide(new BigDecimal(steps - 1), 4, RoundingMode.HALF_UP);

        for (int i = 0; i < steps; i++) {
            BigDecimal testPower = maxDischargePower.negate().add(stepSize.multiply(new BigDecimal(i)));

            if (!checkBatteryConstraints(testPower, currentSoc, config)) {
                continue;
            }

            BigDecimal newDemand = currentLoad.add(testPower).max(BigDecimal.ZERO);
            BigDecimal demandSaving = calculateDemandSaving(predictedDemand, newDemand, demandThreshold);

            BigDecimal revenue = calculateArbitrageRevenue(currentPrice, testPower,
                    testPower.compareTo(BigDecimal.ZERO) > 0 ? DEFAULT_CHARGE_EFFICIENCY : DEFAULT_DISCHARGE_EFFICIENCY);

            BigDecimal chargeRate = testPower.compareTo(BigDecimal.ZERO) > 0 ?
                    testPower.divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal dischargeRate = testPower.compareTo(BigDecimal.ZERO) < 0 ?
                    testPower.abs().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            BigDecimal degradationCost = calculateDegradationCost(chargeRate, dischargeRate,
                    BigDecimal.ZERO, new BigDecimal("25"), config);

            BigDecimal priceDiff = currentPrice != null ? currentPrice.subtract(AVG_PRICE_REFERENCE) : BigDecimal.ZERO;
            BigDecimal theoreticalMaxRevenue = maxDischargePower.divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP)
                    .multiply(priceDiff.abs()).multiply(DEFAULT_DISCHARGE_EFFICIENCY);

            BigDecimal arbitrageScore = theoreticalMaxRevenue.compareTo(BigDecimal.ZERO) > 0 ?
                    revenue.add(degradationCost).divide(theoreticalMaxRevenue, 4, RoundingMode.HALF_UP)
                            .add(BigDecimal.ONE).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP) :
                    new BigDecimal("0.5");
            arbitrageScore = arbitrageScore.min(BigDecimal.ONE).max(BigDecimal.ZERO);

            BigDecimal lifespanScore = BigDecimal.ONE.subtract(
                    chargeRate.add(dischargeRate).divide(MAX_C_RATE, 4, RoundingMode.HALF_UP))
                    .multiply(new BigDecimal("0.6"))
                    .add(BigDecimal.ONE.subtract(currentSoc.subtract(minSoc).abs()
                            .divide(maxSoc.subtract(minSoc), 4, RoundingMode.HALF_UP))
                            .multiply(new BigDecimal("0.4")));
            lifespanScore = lifespanScore.min(BigDecimal.ONE).max(BigDecimal.ZERO);

            BigDecimal demandScore = predictedDemand.compareTo(demandThreshold) > 0 ?
                    BigDecimal.ONE.subtract(newDemand.max(demandThreshold).subtract(demandThreshold)
                            .divide(demandThreshold.multiply(new BigDecimal("0.5")), 4, RoundingMode.HALF_UP)) :
                    BigDecimal.ONE;
            demandScore = demandScore.min(BigDecimal.ONE).max(BigDecimal.ZERO);

            if (config.getDemandControlEnabled() != null && !config.getDemandControlEnabled()) {
                demandScore = new BigDecimal("0.5");
            }

            Map<String, BigDecimal> weightedScores = calculateWeightedScores(
                    revenue, BigDecimal.ONE.subtract(lifespanScore), demandSaving, config);

            BigDecimal totalScore = weightedScores.get("totalScore");

            if (totalScore.compareTo(bestTotalScore) > 0 || i == 0) {
                bestPower = testPower;
                bestTotalScore = totalScore;
                bestArbitrageScore = arbitrageScore;
                bestLifespanScore = lifespanScore;
                bestDemandScore = demandScore;
            }
        }

        result.put("targetPower", bestPower.setScale(2, RoundingMode.HALF_UP));
        result.put("totalScore", bestTotalScore);
        result.put("arbitrageScore", bestArbitrageScore);
        result.put("lifespanScore", bestLifespanScore);
        result.put("demandScore", bestDemandScore);

        return result;
    }

    @Override
    public boolean checkBatteryConstraints(BigDecimal power, BigDecimal currentSoc, StrategyConfigDTO config) {
        if (power == null || currentSoc == null) {
            return false;
        }

        BigDecimal minSoc = config.getMinSoc() != null ? config.getMinSoc() : new BigDecimal("20");
        BigDecimal maxSoc = config.getMaxSoc() != null ? config.getMaxSoc() : new BigDecimal("90");
        BigDecimal maxChargeRate = config.getMaxChargeRate() != null ? config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ? config.getMaxDischargeRate() : new BigDecimal("0.5");

        BigDecimal chargeRate = power.compareTo(BigDecimal.ZERO) > 0 ?
                power.divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal dischargeRate = power.compareTo(BigDecimal.ZERO) < 0 ?
                power.abs().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        if (chargeRate.compareTo(maxChargeRate) > 0) {
            return false;
        }

        if (dischargeRate.compareTo(maxDischargeRate) > 0) {
            return false;
        }

        BigDecimal expectedSoc = calculateExpectedSoc(currentSoc, power, power.abs().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP),
                DEFAULT_CHARGE_EFFICIENCY, DEFAULT_DISCHARGE_EFFICIENCY, DEFAULT_BATTERY_CAPACITY);

        if (expectedSoc.compareTo(minSoc) < 0 || expectedSoc.compareTo(maxSoc) > 0) {
            return false;
        }

        return true;
    }

    @Override
    public boolean checkDemandConstraints(BigDecimal newDemand, StrategyConfigDTO config) {
        if (newDemand == null) {
            return false;
        }

        BigDecimal demandThresholdRatio = config.getDemandThresholdRatio() != null ?
                config.getDemandThresholdRatio() : new BigDecimal("0.9");

        BigDecimal baseDemand = new BigDecimal("500");
        BigDecimal threshold = baseDemand.multiply(demandThresholdRatio);

        return newDemand.compareTo(threshold.multiply(new BigDecimal("1.2"))) <= 0;
    }

    /**
     * <p>计算电池寿命惩罚值 - 综合考虑多因素的衰减模型
     * <p>惩罚因子构成：
     * <ol>
     *   <li>充电倍率惩罚：超过额定倍率按平方递增（惩罚系数10），基础线性惩罚0.5</li>
     *   <li>放电倍率惩罚：同上，保护电池免受大电流冲击</li>
     *   <li>放电深度(DOD)惩罚：超过阈值线性惩罚（系数0.2），基础惩罚0.01</li>
     *   <li>温度惩罚：偏离25℃超过10℃时，每度额外惩罚0.1</li>
     * </ol>
     *
     * <p>物理意义：
     * 大倍率充放会导致SEI膜增厚、活性物质脱落；
     * 深度放电会导致负极石墨层结构坍塌；
     * 极端温度会加速电解液分解和电极腐蚀。
     *
     * @param chargeRate       充电倍率（C-rate），= 充电功率 / 额定容量
     * @param dischargeRate    放电倍率（C-rate），= 放电功率 / 额定容量
     * @param depthOfDischarge 放电深度（%），本次放电占总容量的比例
     * @param temperature      电池温度（℃），最佳工作温度25℃
     * @param config           策略配置，包含寿命约束参数
     * @return 综合寿命惩罚值（无量纲，越小越好）
     */
    @Override
    public BigDecimal calculateLifespanPenalty(BigDecimal chargeRate, BigDecimal dischargeRate,
                                                BigDecimal depthOfDischarge, BigDecimal temperature,
                                                StrategyConfigDTO config) {
        BigDecimal penalty = BigDecimal.ZERO;

        if (chargeRate != null && chargeRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxChargeRate = config.getMaxChargeRate() != null ?
                    config.getMaxChargeRate() : new BigDecimal("0.5");
            if (chargeRate.compareTo(maxChargeRate) > 0) {
                BigDecimal excess = chargeRate.subtract(maxChargeRate);
                penalty = penalty.add(excess.multiply(excess).multiply(new BigDecimal("10")));
            }
            penalty = penalty.add(chargeRate.multiply(new BigDecimal("0.5")));
        }

        if (dischargeRate != null && dischargeRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ?
                    config.getMaxDischargeRate() : new BigDecimal("0.5");
            if (dischargeRate.compareTo(maxDischargeRate) > 0) {
                BigDecimal excess = dischargeRate.subtract(maxDischargeRate);
                penalty = penalty.add(excess.multiply(excess).multiply(new BigDecimal("10")));
            }
            penalty = penalty.add(dischargeRate.multiply(new BigDecimal("0.5")));
        }

        if (depthOfDischarge != null && depthOfDischarge.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxDod = config.getMaxDepthOfDischarge() != null ?
                    config.getMaxDepthOfDischarge() : new BigDecimal("70");
            if (depthOfDischarge.compareTo(maxDod) > 0) {
                BigDecimal excess = depthOfDischarge.subtract(maxDod);
                penalty = penalty.add(excess.multiply(new BigDecimal("0.2")));
            }
            penalty = penalty.add(depthOfDischarge.multiply(new BigDecimal("0.01")));
        }

        if (temperature != null) {
            BigDecimal tempDiff = temperature.subtract(new BigDecimal("25")).abs();
            if (tempDiff.compareTo(new BigDecimal("10")) > 0) {
                penalty = penalty.add(tempDiff.subtract(new BigDecimal("10")).multiply(new BigDecimal("0.1")));
            }
        }

        return penalty.setScale(6, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, BigDecimal> calculateWeightedScores(BigDecimal arbitrageValue, BigDecimal lifespanValue,
                                                            BigDecimal demandValue, StrategyConfigDTO config) {
        Map<String, BigDecimal> result = new HashMap<>();

        BigDecimal arbitrageWeight = config.getArbitrageWeight() != null ? config.getArbitrageWeight() : new BigDecimal("0.5");
        BigDecimal lifespanWeight = config.getLifespanWeight() != null ? config.getLifespanWeight() : new BigDecimal("0.3");
        BigDecimal demandWeight = config.getDemandWeight() != null ? config.getDemandWeight() : new BigDecimal("0.2");

        BigDecimal totalWeight = arbitrageWeight.add(lifespanWeight).add(demandWeight);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            arbitrageWeight = new BigDecimal("0.5");
            lifespanWeight = new BigDecimal("0.3");
            demandWeight = new BigDecimal("0.2");
            totalWeight = BigDecimal.ONE;
        }

        BigDecimal normalizedArbitrage = normalizeValue(arbitrageValue, new BigDecimal("-100"), new BigDecimal("100"));
        BigDecimal normalizedLifespan = normalizeValue(lifespanValue, BigDecimal.ZERO, BigDecimal.ONE);
        BigDecimal normalizedDemand = normalizeValue(demandValue, BigDecimal.ZERO, new BigDecimal("100"));

        BigDecimal totalScore = normalizedArbitrage.multiply(arbitrageWeight.divide(totalWeight, 4, RoundingMode.HALF_UP))
                .add(normalizedLifespan.multiply(lifespanWeight.divide(totalWeight, 4, RoundingMode.HALF_UP)))
                .add(normalizedDemand.multiply(demandWeight.divide(totalWeight, 4, RoundingMode.HALF_UP)));

        result.put("arbitrageWeight", arbitrageWeight);
        result.put("lifespanWeight", lifespanWeight);
        result.put("demandWeight", demandWeight);
        result.put("normalizedArbitrage", normalizedArbitrage);
        result.put("normalizedLifespan", normalizedLifespan);
        result.put("normalizedDemand", normalizedDemand);
        result.put("totalScore", totalScore);

        return result;
    }

    /**
     * <p>逐小时优化调度计划 - 日前计划生成核心算法
     * <p>算法流程：
     * <ol>
     *   <li>初始化参数：从策略配置获取SOC范围、充放电倍率、需量阈值等约束</li>
     *   <li>构建电价和负荷的小时级索引Map，便于快速查询</li>
     *   <li>计算全天最大负荷，作为需量控制的基准阈值</li>
     *   <li>逐小时（0-23点）进行优化计算：
     *     <ul>
     *       <li>获取当前小时的电价、负荷、光伏出力预测数据</li>
     *       <li>计算净负荷 = 原始负荷 - 光伏出力</li>
     *       <li>调用calculateOptimalPower()计算该小时的最优充放电功率</li>
     *       <li>计算执行后的预计SOC，若超出约束范围则修正功率</li>
     *       <li>计算新的需量值 = 净负荷 + 充放电功率</li>
     *       <li>计算该小时的套利收益、寿命损耗成本、需量节省</li>
     *       <li>记录各项目标得分，更新当前SOC用于下一小时计算</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p>SOC约束修正逻辑：
     * - 若预计SOC < minSoc：强制增加充电功率，确保不低于最低SOC
     * - 若预计SOC > maxSoc：强制减少充电功率（或增加放电），确保不高于最高SOC
     *
     * @param priceForecast 24小时电价预测列表，每小时一条记录
     * @param loadForecast  24小时负荷/光伏预测列表，每小时一条记录
     * @param initialSoc    初始SOC（%），0点时的电池荷电状态
     * @param config        策略配置，包含权重和约束参数
     * @return 24小时调度计划列表，包含每小时的功率、SOC、收益等信息
     */
    @Override
    public List<DispatchPlanHourDTO> optimizeDispatchPlan(List<PriceForecastDTO> priceForecast,
                                                           List<LoadForecastDTO> loadForecast,
                                                           BigDecimal initialSoc,
                                                           StrategyConfigDTO config) {
        List<DispatchPlanHourDTO> planHours = new ArrayList<>();

        BigDecimal currentSoc = initialSoc != null ? initialSoc : new BigDecimal("50");
        BigDecimal minSoc = config.getMinSoc() != null ? config.getMinSoc() : new BigDecimal("20");
        BigDecimal maxSoc = config.getMaxSoc() != null ? config.getMaxSoc() : new BigDecimal("90");
        BigDecimal maxChargeRate = config.getMaxChargeRate() != null ? config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ? config.getMaxDischargeRate() : new BigDecimal("0.5");
        BigDecimal demandThresholdRatio = config.getDemandThresholdRatio() != null ?
                config.getDemandThresholdRatio() : new BigDecimal("0.9");

        Map<Integer, PriceForecastDTO> priceMap = new HashMap<>();
        for (PriceForecastDTO pf : priceForecast) {
            priceMap.put(pf.getHourIndex(), pf);
        }

        Map<Integer, LoadForecastDTO> loadMap = new HashMap<>();
        for (LoadForecastDTO lf : loadForecast) {
            loadMap.put(lf.getHourIndex(), lf);
        }

        BigDecimal maxLoad = BigDecimal.ZERO;
        for (LoadForecastDTO lf : loadForecast) {
            if (lf.getForecastLoad() != null && lf.getForecastLoad().compareTo(maxLoad) > 0) {
                maxLoad = lf.getForecastLoad();
            }
        }
        BigDecimal demandThreshold = maxLoad.multiply(demandThresholdRatio);

        for (int hour = 0; hour < 24; hour++) {
            DispatchPlanHourDTO planHour = new DispatchPlanHourDTO();
            planHour.setHourIndex(hour);
            planHour.setStartTime(LocalTime.of(hour, 0));
            planHour.setEndTime(LocalTime.of((hour + 1) % 24, 0));

            PriceForecastDTO pf = priceMap.get(hour);
            LoadForecastDTO lf = loadMap.get(hour);

            BigDecimal price = pf != null && pf.getForecastPrice() != null ?
                    pf.getForecastPrice() : AVG_PRICE_REFERENCE;
            BigDecimal load = lf != null && lf.getForecastLoad() != null ?
                    lf.getForecastLoad() : new BigDecimal("300");
            BigDecimal pv = lf != null && lf.getForecastPv() != null ?
                    lf.getForecastPv() : BigDecimal.ZERO;

            planHour.setPrice(price);
            planHour.setPeriodType(pf != null ? pf.getPeriodType() : "FLAT");
            planHour.setForecastLoad(load);
            planHour.setForecastPv(pv);

            BigDecimal netLoad = load.subtract(pv).max(BigDecimal.ZERO);

            Map<String, BigDecimal> optimal = calculateOptimalPower(price, netLoad, currentSoc, netLoad, config);
            BigDecimal power = optimal.get("targetPower");

            BigDecimal expectedSoc = calculateExpectedSoc(currentSoc, power, power.abs().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP),
                    DEFAULT_CHARGE_EFFICIENCY, DEFAULT_DISCHARGE_EFFICIENCY, DEFAULT_BATTERY_CAPACITY);

            if (expectedSoc.compareTo(minSoc) < 0) {
                BigDecimal minEnergy = (minSoc.subtract(currentSoc)).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                        .multiply(DEFAULT_BATTERY_CAPACITY);
                BigDecimal minPower = minEnergy.multiply(HOURS_PER_DAY);
                minPower = minPower.min(DEFAULT_BATTERY_CAPACITY.multiply(maxChargeRate));
                power = minPower.max(power);
                expectedSoc = calculateExpectedSoc(currentSoc, power, power.abs().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP),
                        DEFAULT_CHARGE_EFFICIENCY, DEFAULT_DISCHARGE_EFFICIENCY, DEFAULT_BATTERY_CAPACITY);
            }

            if (expectedSoc.compareTo(maxSoc) > 0) {
                BigDecimal maxEnergy = (maxSoc.subtract(currentSoc)).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                        .multiply(DEFAULT_BATTERY_CAPACITY);
                BigDecimal maxPower = maxEnergy.multiply(HOURS_PER_DAY);
                maxPower = maxPower.max(DEFAULT_BATTERY_CAPACITY.multiply(maxDischargeRate).negate());
                power = maxPower.min(power);
                expectedSoc = calculateExpectedSoc(currentSoc, power, power.abs().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP),
                        DEFAULT_CHARGE_EFFICIENCY, DEFAULT_DISCHARGE_EFFICIENCY, DEFAULT_BATTERY_CAPACITY);
            }

            BigDecimal newDemand = netLoad.add(power).max(BigDecimal.ZERO);

            planHour.setPower(power);
            planHour.setEnergy(power.abs().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP));
            planHour.setExpectedSoc(expectedSoc);
            planHour.setChargeRate(power.abs().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP));
            planHour.setExpectedDemand(newDemand);
            planHour.setDemandControlRequired(newDemand.compareTo(demandThreshold) > 0);

            if (power.compareTo(BigDecimal.ZERO) > 0) {
                planHour.setActionType("CHARGE");
            } else if (power.compareTo(BigDecimal.ZERO) < 0) {
                planHour.setActionType("DISCHARGE");
            } else {
                planHour.setActionType("HOLD");
            }

            BigDecimal revenue = calculateArbitrageRevenue(price, power,
                    power.compareTo(BigDecimal.ZERO) > 0 ? DEFAULT_CHARGE_EFFICIENCY : DEFAULT_DISCHARGE_EFFICIENCY);
            planHour.setRevenue(revenue);

            BigDecimal chargeRate = power.compareTo(BigDecimal.ZERO) > 0 ? planHour.getChargeRate() : BigDecimal.ZERO;
            BigDecimal dischargeRate = power.compareTo(BigDecimal.ZERO) < 0 ? planHour.getChargeRate() : BigDecimal.ZERO;
            BigDecimal degradationCost = calculateDegradationCost(chargeRate, dischargeRate,
                    BigDecimal.ZERO, new BigDecimal("25"), config);
            planHour.setDegradationCost(degradationCost);

            BigDecimal demandSaving = calculateDemandSaving(netLoad, newDemand, demandThreshold);
            planHour.setDemandSaving(demandSaving);

            BigDecimal hourScore = optimal.get("totalScore");
            planHour.setObjectiveScore(hourScore);

            planHours.add(planHour);
            currentSoc = expectedSoc;
        }

        return planHours;
    }

    @Override
    public BigDecimal calculateExpectedSoc(BigDecimal currentSoc, BigDecimal power, BigDecimal energy,
                                            BigDecimal chargeEfficiency, BigDecimal dischargeEfficiency,
                                            BigDecimal batteryCapacity) {
        if (currentSoc == null || power == null || energy == null) {
            return currentSoc != null ? currentSoc : BigDecimal.ZERO;
        }

        BigDecimal efficiency = power.compareTo(BigDecimal.ZERO) > 0 ?
                (chargeEfficiency != null ? chargeEfficiency : DEFAULT_CHARGE_EFFICIENCY) :
                (dischargeEfficiency != null ? dischargeEfficiency : DEFAULT_DISCHARGE_EFFICIENCY);

        BigDecimal capacity = batteryCapacity != null ? batteryCapacity : DEFAULT_BATTERY_CAPACITY;

        BigDecimal socChange;
        if (power.compareTo(BigDecimal.ZERO) > 0) {
            socChange = energy.multiply(efficiency).divide(capacity, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        } else {
            socChange = energy.divide(efficiency, 8, RoundingMode.HALF_UP).divide(capacity, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).negate();
        }

        BigDecimal expectedSoc = currentSoc.add(socChange);
        return expectedSoc.min(MAX_SOC).max(MIN_SOC).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, BigDecimal> getParetoFrontier(List<DispatchPlanHourDTO> planHours, StrategyConfigDTO config) {
        Map<String, BigDecimal> frontier = new HashMap<>();

        BigDecimal arbitrageScore = calculateNormalizedArbitrageScore(planHours, config);
        BigDecimal lifespanScore = calculateNormalizedLifespanScore(planHours, config);
        BigDecimal demandScore = calculateNormalizedDemandScore(planHours, config);

        frontier.put("arbitrageScore", arbitrageScore);
        frontier.put("lifespanScore", lifespanScore);
        frontier.put("demandScore", demandScore);

        return frontier;
    }

    @Override
    public StrategyResultVO resolveTradeOffs(RealTimeControlRequest request, StrategyConfigDTO config,
                                               Map<String, BigDecimal> scores) {
        StrategyResultVO result = new StrategyResultVO();
        result.setStrategyCode(config.getStrategyCode());
        result.setStrategyName(config.getStrategyName());

        BigDecimal arbitrageScore = scores.getOrDefault("arbitrageScore", BigDecimal.ZERO);
        BigDecimal lifespanScore = scores.getOrDefault("lifespanScore", BigDecimal.ZERO);
        BigDecimal demandScore = scores.getOrDefault("demandScore", BigDecimal.ZERO);

        BigDecimal minScore = Collections.min(Arrays.asList(arbitrageScore, lifespanScore, demandScore));

        if (minScore.compareTo(new BigDecimal("0.3")) < 0) {
            if (demandScore.compareTo(minScore) == 0 && config.getDemandControlEnabled()) {
                result.setUrgencyLevel("HIGH");
                result.setMessage("需量控制优先级最高，执行放电抑制需量");
            } else if (lifespanScore.compareTo(minScore) == 0) {
                result.setUrgencyLevel("MEDIUM");
                result.setMessage("电池保护优先级高，降低充放电功率");
            } else {
                result.setUrgencyLevel("LOW");
                result.setMessage("套利机会不足，等待更好的价格窗口");
            }
        } else {
            result.setUrgencyLevel("NORMAL");
            result.setMessage("多目标平衡良好，按优化结果执行");
        }

        return result;
    }

    private BigDecimal normalizeValue(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || min == null || max == null) {
            return new BigDecimal("0.5");
        }

        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("0.5");
        }

        BigDecimal normalized = value.subtract(min).divide(range, 4, RoundingMode.HALF_UP);
        return normalized.min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }

    private BigDecimal calculateDemandSaving(BigDecimal originalDemand, BigDecimal newDemand, BigDecimal threshold) {
        if (originalDemand == null || newDemand == null || threshold == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal originalExcess = originalDemand.subtract(threshold).max(BigDecimal.ZERO);
        BigDecimal newExcess = newDemand.subtract(threshold).max(BigDecimal.ZERO);
        BigDecimal saving = originalExcess.subtract(newExcess).max(BigDecimal.ZERO);

        return saving.multiply(DEFAULT_DEMAND_PRICE).setScale(4, RoundingMode.HALF_UP);
    }

    private String determineUrgencyLevel(BigDecimal predictedDemand, BigDecimal threshold, StrategyConfigDTO config) {
        if (predictedDemand == null || threshold == null) {
            return "NORMAL";
        }

        BigDecimal ratio = predictedDemand.divide(threshold, 4, RoundingMode.HALF_UP);

        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            return "CRITICAL";
        } else if (ratio.compareTo(new BigDecimal("0.95")) >= 0) {
            return "HIGH";
        } else if (ratio.compareTo(new BigDecimal("0.90")) >= 0) {
            return "MEDIUM";
        } else if (ratio.compareTo(new BigDecimal("0.80")) >= 0) {
            return "LOW";
        } else {
            return "NORMAL";
        }
    }

    private List<String> generateRecommendedActions(BigDecimal targetPower, BigDecimal currentSoc,
                                                    BigDecimal predictedDemand, BigDecimal threshold,
                                                    StrategyConfigDTO config) {
        List<String> actions = new ArrayList<>();

        if (targetPower.compareTo(BigDecimal.ZERO) > 0) {
            actions.add("充电 " + targetPower.setScale(2, RoundingMode.HALF_UP) + " kW");
        } else if (targetPower.compareTo(BigDecimal.ZERO) < 0) {
            actions.add("放电 " + targetPower.abs().setScale(2, RoundingMode.HALF_UP) + " kW");
        } else {
            actions.add("保持当前状态");
        }

        if (currentSoc != null) {
            if (currentSoc.compareTo(config.getMinSoc() != null ? config.getMinSoc() : new BigDecimal("20")) < 0) {
                actions.add("SOC偏低，建议补充充电");
            } else if (currentSoc.compareTo(config.getMaxSoc() != null ? config.getMaxSoc() : new BigDecimal("90")) > 0) {
                actions.add("SOC偏高，建议避免继续充电");
            }
        }

        if (predictedDemand != null && threshold != null) {
            BigDecimal ratio = predictedDemand.divide(threshold, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.9")) >= 0) {
                actions.add("需量接近阈值，准备启动需量控制");
            }
        }

        return actions;
    }
}
