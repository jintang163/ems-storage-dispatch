package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.strategy.*;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.service.MultiObjectiveOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        List<PriceForecastDTO> priceForecast = generateMockPriceForecast(request.getPlanDate());
        List<LoadForecastDTO> loadForecast = generateMockLoadForecast(request.getPlanDate(), request.getTransformerCode());

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

            BigDecimal priceDiff = currentPrice.subtract(new BigDecimal("0.5"));
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
                    pf.getForecastPrice() : new BigDecimal("0.5");
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

    private List<PriceForecastDTO> generateMockPriceForecast(LocalDate date) {
        List<PriceForecastDTO> forecast = new ArrayList<>();
        BigDecimal[] prices = {
                new BigDecimal("0.35"), new BigDecimal("0.32"), new BigDecimal("0.30"), new BigDecimal("0.28"),
                new BigDecimal("0.28"), new BigDecimal("0.30"), new BigDecimal("0.38"), new BigDecimal("0.55"),
                new BigDecimal("0.75"), new BigDecimal("0.85"), new BigDecimal("0.88"), new BigDecimal("0.82"),
                new BigDecimal("0.78"), new BigDecimal("0.75"), new BigDecimal("0.72"), new BigDecimal("0.68"),
                new BigDecimal("0.65"), new BigDecimal("0.70"), new BigDecimal("0.85"), new BigDecimal("0.95"),
                new BigDecimal("0.90"), new BigDecimal("0.75"), new BigDecimal("0.55"), new BigDecimal("0.42")
        };
        String[] periodTypes = {
                "VALLEY", "VALLEY", "VALLEY", "VALLEY",
                "VALLEY", "VALLEY", "FLAT", "PEAK",
                "PEAK", "CRITICAL_PEAK", "CRITICAL_PEAK", "PEAK",
                "PEAK", "PEAK", "PEAK", "FLAT",
                "FLAT", "FLAT", "PEAK", "CRITICAL_PEAK",
                "CRITICAL_PEAK", "PEAK", "FLAT", "VALLEY"
        };

        for (int i = 0; i < 24; i++) {
            PriceForecastDTO dto = new PriceForecastDTO();
            dto.setForecastDate(date);
            dto.setHourIndex(i);
            dto.setStartTime(LocalTime.of(i, 0));
            dto.setEndTime(LocalTime.of((i + 1) % 24, 0));
            dto.setForecastPrice(prices[i]);
            dto.setPeriodType(periodTypes[i]);
            dto.setForecastSource("MOCK");
            dto.setForecastModel("HISTORICAL_AVERAGE");
            dto.setConfidenceLevel(new BigDecimal("0.85"));
            dto.setIsPeak(periodTypes[i].contains("PEAK"));
            dto.setIsValley("VALLEY".equals(periodTypes[i]));
            forecast.add(dto);
        }

        return forecast;
    }

    private List<LoadForecastDTO> generateMockLoadForecast(LocalDate date, String transformerCode) {
        List<LoadForecastDTO> forecast = new ArrayList<>();
        BigDecimal[] loads = {
                new BigDecimal("200"), new BigDecimal("180"), new BigDecimal("170"), new BigDecimal("160"),
                new BigDecimal("165"), new BigDecimal("180"), new BigDecimal("220"), new BigDecimal("350"),
                new BigDecimal("480"), new BigDecimal("550"), new BigDecimal("580"), new BigDecimal("560"),
                new BigDecimal("540"), new BigDecimal("520"), new BigDecimal("500"), new BigDecimal("480"),
                new BigDecimal("460"), new BigDecimal("500"), new BigDecimal("580"), new BigDecimal("620"),
                new BigDecimal("580"), new BigDecimal("500"), new BigDecimal("380"), new BigDecimal("280")
        };
        BigDecimal[] pvs = {
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("80"), new BigDecimal("200"),
                new BigDecimal("350"), new BigDecimal("450"), new BigDecimal("500"), new BigDecimal("480"),
                new BigDecimal("450"), new BigDecimal("400"), new BigDecimal("350"), new BigDecimal("250"),
                new BigDecimal("150"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        };

        for (int i = 0; i < 24; i++) {
            LoadForecastDTO dto = new LoadForecastDTO();
            dto.setForecastDate(date);
            dto.setHourIndex(i);
            dto.setStartTime(LocalTime.of(i, 0));
            dto.setEndTime(LocalTime.of((i + 1) % 24, 0));
            dto.setForecastLoad(loads[i]);
            dto.setForecastPv(pvs[i]);
            dto.setForecastGrid(loads[i].subtract(pvs[i]).max(BigDecimal.ZERO));
            dto.setForecastType("DAY_AHEAD");
            dto.setForecastSource("MOCK");
            dto.setForecastModel("SIMILAR_DAY");
            dto.setConfidenceLevel(new BigDecimal("0.80"));
            dto.setIsPeakHour(loads[i].compareTo(new BigDecimal("500")) >= 0);
            dto.setTransformerCode(transformerCode);
            forecast.add(dto);
        }

        return forecast;
    }
}
