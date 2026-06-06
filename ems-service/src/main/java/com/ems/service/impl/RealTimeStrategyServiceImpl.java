package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.strategy.*;
import com.ems.domain.entity.StrategyExecutionLog;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.domain.vo.strategy.StrategyStatisticsVO;
import com.ems.repository.StrategyExecutionLogRepository;
import com.ems.service.ForecastService;
import com.ems.service.MultiObjectiveOptimizationService;
import com.ems.service.RealTimeStrategyService;
import com.ems.service.StrategyConfigService;
import com.ems.service.TimeOfUsePriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 实时策略控制服务实现类
 * 提供实时充放电控制、需量管理和策略执行功能
 *
 * 核心控制策略：
 * 1. 需量控制（最高优先级）：当预测需量超过阈值时，立即启动放电
 * 2. 电池保护：遵守SOC范围、温度限制、充放电倍率限制
 * 3. 峰谷套利：在低价时段充电，高价时段放电
 * 4. 削峰填谷：在负荷高峰放电，负荷低谷充电
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeStrategyServiceImpl implements RealTimeStrategyService {

    private final StrategyConfigService strategyConfigService;
    private final MultiObjectiveOptimizationService optimizationService;
    private final StrategyExecutionLogRepository executionLogRepository;
    private final ForecastService forecastService;
    private final TimeOfUsePriceService timeOfUsePriceService;

    private static final BigDecimal WARNING_THRESHOLD_RATIO = new BigDecimal("0.80");
    private static final BigDecimal ALARM_THRESHOLD_RATIO = new BigDecimal("0.90");
    private static final BigDecimal DEFAULT_BATTERY_CAPACITY = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_DEMAND_PRICE = new BigDecimal("35");
    private static final BigDecimal DEFAULT_BATTERY_COST = new BigDecimal("1500");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyResultVO executeRealTimeControl(RealTimeControlRequest request) {
        log.info("执行实时策略控制 - 策略: {}, 执行类型: {}", request.getStrategyCode(), request.getExecutionType());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());

        String executionType = request.getExecutionType() != null ?
                request.getExecutionType().toUpperCase() : "MULTI_OBJECTIVE";

        StrategyResultVO result;

        switch (executionType) {
            case "DEMAND_CONTROL":
                result = executeDemandControl(request);
                break;
            case "PEAK_VALLEY_ARBITRAGE":
                result = executePeakValleyArbitrage(request);
                break;
            case "PEAK_SHAVING":
                result = executePeakShaving(request);
                break;
            case "VALLEY_FILLING":
                result = executeValleyFilling(request);
                break;
            case "MULTI_OBJECTIVE":
            default:
                result = executeMultiObjectiveOptimization(request);
                break;
        }

        logExecution(result, request);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyResultVO executeDemandControl(RealTimeControlRequest request) {
        log.info("执行需量控制 - 策略: {}, 当前需量: {}, 预测需量: {}",
                request.getStrategyCode(), request.getCurrentDemand(), request.getCurrentLoad());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());

        if (config.getDemandControlEnabled() == null || !config.getDemandControlEnabled()) {
            StrategyResultVO result = new StrategyResultVO();
            result.setStrategyCode(config.getStrategyCode());
            result.setStrategyName(config.getStrategyName());
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setStatus("success");
            result.setMessage("需量控制未启用");
            result.setUrgencyLevel("NORMAL");
            return result;
        }

        BigDecimal currentDemand = request.getCurrentDemand() != null ?
                request.getCurrentDemand() : request.getCurrentLoad() != null ?
                request.getCurrentLoad() : BigDecimal.ZERO;
        BigDecimal predictedDemand = currentDemand;
        BigDecimal demandThreshold = currentDemand.multiply(
                config.getDemandThresholdRatio() != null ? config.getDemandThresholdRatio() : new BigDecimal("0.9"));

        String warningLevel = checkDemandWarning(request.getStrategyCode(), currentDemand, predictedDemand);

        StrategyResultVO result = new StrategyResultVO();
        result.setStrategyCode(config.getStrategyCode());
        result.setStrategyName(config.getStrategyName());

        if ("ALARM".equals(warningLevel) || "CRITICAL".equals(warningLevel)) {
            BigDecimal requiredReduction = calculateRequiredDischargePower(
                    request.getStrategyCode(), currentDemand, predictedDemand);

            BigDecimal currentSoc = request.getCurrentSoc() != null ?
                    request.getCurrentSoc() : new BigDecimal("50");
            BigDecimal minSoc = config.getMinSoc() != null ? config.getMinSoc() : new BigDecimal("20");
            BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ?
                    config.getMaxDischargeRate() : new BigDecimal("0.5");
            BigDecimal maxDischargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxDischargeRate);

            BigDecimal availableDischarge = BigDecimal.ZERO;
            if (currentSoc.compareTo(minSoc) > 0) {
                BigDecimal availableEnergy = currentSoc.subtract(minSoc).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                        .multiply(DEFAULT_BATTERY_CAPACITY);
                availableDischarge = availableEnergy.multiply(new BigDecimal("4"));
                availableDischarge = availableDischarge.min(maxDischargePower);
            }

            BigDecimal dischargePower = requiredReduction.min(availableDischarge);

            if (dischargePower.compareTo(BigDecimal.ZERO) > 0) {
                result.setActionType("DISCHARGE");
                result.setTargetPower(dischargePower);
                result.setUrgencyLevel("CRITICAL");
                result.setMessage("需量超标，紧急放电抑制需量");

                List<String> actions = new ArrayList<>();
                actions.add("紧急放电 " + dischargePower.setScale(2, RoundingMode.HALF_UP) + " kW");
                actions.add("当前需量: " + currentDemand.setScale(2, RoundingMode.HALF_UP) + " kW");
                actions.add("需量阈值: " + demandThreshold.setScale(2, RoundingMode.HALF_UP) + " kW");
                result.setRecommendedActions(actions);
            } else {
                result.setActionType("HOLD");
                result.setTargetPower(BigDecimal.ZERO);
                result.setUrgencyLevel("HIGH");
                result.setMessage("SOC过低，无法放电抑制需量");
            }

            BigDecimal demandSaving = calculateDemandChargeSaving(
                    request.getStrategyCode(), predictedDemand, predictedDemand.subtract(dischargePower));
            result.setExpectedDemandSaving(demandSaving);

        } else if ("WARNING".equals(warningLevel)) {
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setUrgencyLevel("MEDIUM");
            result.setMessage("需量接近阈值，密切监控");
        } else {
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setUrgencyLevel("NORMAL");
            result.setMessage("需量正常，无需控制");
        }

        result.setCurrentPrice(request.getCurrentPrice());
        result.setExpectedSoc(request.getCurrentSoc());

        Map<String, BigDecimal> scores = calculateScores(request, config, result);
        result.setTotalObjectiveScore(scores.get("totalScore"));
        result.setArbitrageScore(scores.get("arbitrageScore"));
        result.setLifespanScore(scores.get("lifespanScore"));
        result.setDemandScore(scores.get("demandScore"));

        result.setStatus("success");

        return result;
    }

    /**
     * <p>执行峰谷套利控制 - 根据实时电价与平均电价的比值判断充放电时机
     * <p>核心逻辑：
     * <ol>
     *   <li>从TimeOfUsePriceService获取分时电价表，计算加权平均电价</li>
     *   <li>计算电价比值 = 当前电价 / 平均电价</li>
     *   <li>当电价比值 < 0.85（谷段）且 SOC < maxSoc时，执行充电</li>
     *   <li>当电价比值 > 1.15（峰段）且 SOC > minSoc时，执行放电</li>
     *   <li>否则保持待机，等待更好的套利机会</li>
     * </ol>
     *
     * <p>物理意义：
     * 利用峰谷电价差进行"低买高卖"，在电价低谷时段充电储存电能，
     * 在电价高峰时段放电获取收益。套利收益 = 放电收益 - 充电成本。
     *
     * @param request 实时控制请求，包含当前电价、SOC、电池温度等参数
     * @return 峰谷套利控制结果，包含充放电功率、预计SOC、预期收益等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyResultVO executePeakValleyArbitrage(RealTimeControlRequest request) {
        log.info("执行峰谷套利 - 策略: {}, 当前电价: {}", request.getStrategyCode(), request.getCurrentPrice());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());

        if (config.getPeakValleyArbitrageEnabled() == null || !config.getPeakValleyArbitrageEnabled()) {
            StrategyResultVO result = new StrategyResultVO();
            result.setStrategyCode(config.getStrategyCode());
            result.setStrategyName(config.getStrategyName());
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setStatus("success");
            result.setMessage("峰谷套利未启用");
            result.setUrgencyLevel("NORMAL");
            return result;
        }

        BigDecimal currentPrice = request.getCurrentPrice() != null ?
                request.getCurrentPrice() : new BigDecimal("0.5");
        BigDecimal avgPrice = calculateAveragePrice(request.getTransformerCode());
        BigDecimal currentSoc = request.getCurrentSoc() != null ?
                request.getCurrentSoc() : new BigDecimal("50");

        BigDecimal maxChargeRate = config.getMaxChargeRate() != null ?
                config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ?
                config.getMaxDischargeRate() : new BigDecimal("0.5");
        BigDecimal minSoc = config.getMinSoc() != null ? config.getMinSoc() : new BigDecimal("20");
        BigDecimal maxSoc = config.getMaxSoc() != null ? config.getMaxSoc() : new BigDecimal("90");

        StrategyResultVO result = new StrategyResultVO();
        result.setStrategyCode(config.getStrategyCode());
        result.setStrategyName(config.getStrategyName());

        BigDecimal priceRatio = currentPrice.divide(avgPrice, 4, RoundingMode.HALF_UP);
        BigDecimal targetPower = BigDecimal.ZERO;

        if (priceRatio.compareTo(new BigDecimal("0.85")) < 0 && currentSoc.compareTo(maxSoc) < 0) {
            BigDecimal maxChargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxChargeRate);
            BigDecimal availableChargeCapacity = maxSoc.subtract(currentSoc).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                    .multiply(DEFAULT_BATTERY_CAPACITY);
            BigDecimal desiredChargePower = availableChargeCapacity.multiply(new BigDecimal("2"));
            targetPower = maxChargePower.min(desiredChargePower);

            result.setActionType("CHARGE");
            result.setTargetPower(targetPower);
            result.setUrgencyLevel("LOW");
            result.setMessage("低价时段，执行充电");

            BigDecimal revenue = optimizationService.calculateArbitrageRevenue(
                    currentPrice, targetPower, new BigDecimal("0.95"));
            result.setExpectedRevenue(revenue.abs());

        } else if (priceRatio.compareTo(new BigDecimal("1.15")) > 0 && currentSoc.compareTo(minSoc) > 0) {
            BigDecimal maxDischargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxDischargeRate);
            BigDecimal availableDischargeCapacity = currentSoc.subtract(minSoc).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                    .multiply(DEFAULT_BATTERY_CAPACITY);
            BigDecimal desiredDischargePower = availableDischargeCapacity.multiply(new BigDecimal("2"));
            targetPower = maxDischargePower.min(desiredDischargePower);

            result.setActionType("DISCHARGE");
            result.setTargetPower(targetPower);
            result.setUrgencyLevel("LOW");
            result.setMessage("高价时段，执行放电");

            BigDecimal revenue = optimizationService.calculateArbitrageRevenue(
                    currentPrice, targetPower.negate(), new BigDecimal("0.95"));
            result.setExpectedRevenue(revenue);

        } else {
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setUrgencyLevel("NORMAL");
            result.setMessage("电价处于平段，等待更好的套利机会");
        }

        List<String> actions = new ArrayList<>();
        if (targetPower.compareTo(BigDecimal.ZERO) != 0) {
            String action = result.getActionType().equals("CHARGE") ? "充电 " : "放电 ";
            actions.add(action + targetPower.setScale(2, RoundingMode.HALF_UP) + " kW");
        }
        actions.add("当前电价: " + currentPrice.setScale(4, RoundingMode.HALF_UP) + " 元/kWh");
        actions.add("平均电价: " + avgPrice.setScale(4, RoundingMode.HALF_UP) + " 元/kWh");
        result.setRecommendedActions(actions);

        BigDecimal expectedSoc = optimizationService.calculateExpectedSoc(
                currentSoc, targetPower, targetPower.abs().divide(new BigDecimal("24"), 4, RoundingMode.HALF_UP),
                new BigDecimal("0.95"), new BigDecimal("0.95"), DEFAULT_BATTERY_CAPACITY);
        result.setExpectedSoc(expectedSoc);

        BigDecimal chargeRate = targetPower.abs().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP);
        BigDecimal degradationCost = calculateBatteryDegradationCost(
                request.getStrategyCode(),
                targetPower.compareTo(BigDecimal.ZERO) > 0 ? chargeRate : BigDecimal.ZERO,
                targetPower.compareTo(BigDecimal.ZERO) < 0 ? chargeRate : BigDecimal.ZERO,
                BigDecimal.ZERO,
                request.getBatteryTemperature() != null ? request.getBatteryTemperature() : new BigDecimal("25"));
        result.setExpectedDegradationCost(degradationCost);

        Map<String, BigDecimal> scores = calculateScores(request, config, result);
        result.setTotalObjectiveScore(scores.get("totalScore"));
        result.setArbitrageScore(scores.get("arbitrageScore"));
        result.setLifespanScore(scores.get("lifespanScore"));
        result.setDemandScore(scores.get("demandScore"));

        result.setStatus("success");

        return result;
    }

    /**
     * <p>执行削峰控制 - 在负荷高峰时段通过放电降低峰值需量
     * <p>核心逻辑：
     * <ol>
     *   <li>从ForecastService获取今日24小时负荷预测数据，计算全天平均负荷</li>
     *   <li>削峰阈值 = 平均负荷 × 1.2（可配置），超过此阈值判定为负荷高峰</li>
     *   <li>当当前负荷 > 削峰阈值 且 SOC > minSoc时，启动放电削峰</li>
     *   <li>计算所需放电功率 = 当前负荷 - 平均负荷</li>
     *   <li>考虑电池最大放电倍率和可用容量限制，取最小值作为实际放电功率</li>
     * </ol>
     *
     * <p>物理意义：
     * 通过在用电高峰时段释放电池储能，降低企业从电网的受电功率，
     * 从而降低最大需量，减少需量电费支出。
     *
     * @param request 实时控制请求，包含当前负荷、SOC、电价等参数
     * @return 削峰控制结果，包含放电功率、预计SOC、预期收益等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyResultVO executePeakShaving(RealTimeControlRequest request) {
        log.info("执行削峰 - 策略: {}, 当前负荷: {}", request.getStrategyCode(), request.getCurrentLoad());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());

        if (config.getPeakShavingEnabled() == null || !config.getPeakShavingEnabled()) {
            StrategyResultVO result = new StrategyResultVO();
            result.setStrategyCode(config.getStrategyCode());
            result.setStrategyName(config.getStrategyName());
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setStatus("success");
            result.setMessage("削峰未启用");
            result.setUrgencyLevel("NORMAL");
            return result;
        }

        BigDecimal currentLoad = request.getCurrentLoad() != null ? request.getCurrentLoad() : BigDecimal.ZERO;
        BigDecimal currentSoc = request.getCurrentSoc() != null ? request.getCurrentSoc() : new BigDecimal("50");

        BigDecimal avgLoad = calculateAverageLoad(request.getTransformerCode());
        BigDecimal peakThreshold = avgLoad.multiply(new BigDecimal("1.2"));

        BigDecimal maxDischargeRate = config.getMaxDischargeRate() != null ?
                config.getMaxDischargeRate() : new BigDecimal("0.5");
        BigDecimal minSoc = config.getMinSoc() != null ? config.getMinSoc() : new BigDecimal("20");

        StrategyResultVO result = new StrategyResultVO();
        result.setStrategyCode(config.getStrategyCode());
        result.setStrategyName(config.getStrategyName());

        if (currentLoad.compareTo(peakThreshold) > 0 && currentSoc.compareTo(minSoc) > 0) {
            BigDecimal requiredReduction = currentLoad.subtract(avgLoad);
            BigDecimal maxDischargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxDischargeRate);
            BigDecimal availableDischarge = currentSoc.subtract(minSoc).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                    .multiply(DEFAULT_BATTERY_CAPACITY).multiply(new BigDecimal("2"));

            BigDecimal dischargePower = requiredReduction.min(maxDischargePower).min(availableDischarge);

            if (dischargePower.compareTo(BigDecimal.ZERO) > 0) {
                result.setActionType("DISCHARGE");
                result.setTargetPower(dischargePower);
                result.setUrgencyLevel("MEDIUM");
                result.setMessage("负荷高峰，执行削峰放电");

                List<String> actions = new ArrayList<>();
                actions.add("削峰放电 " + dischargePower.setScale(2, RoundingMode.HALF_UP) + " kW");
                actions.add("当前负荷: " + currentLoad.setScale(2, RoundingMode.HALF_UP) + " kW");
                actions.add("平均负荷: " + avgLoad.setScale(2, RoundingMode.HALF_UP) + " kW");
                result.setRecommendedActions(actions);
            } else {
                result.setActionType("HOLD");
                result.setTargetPower(BigDecimal.ZERO);
                result.setUrgencyLevel("LOW");
                result.setMessage("SOC不足，无法执行削峰");
            }
        } else {
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setUrgencyLevel("NORMAL");
            result.setMessage("负荷正常，无需削峰");
        }

        BigDecimal expectedSoc = optimizationService.calculateExpectedSoc(
                currentSoc, result.getTargetPower().negate(),
                result.getTargetPower().divide(new BigDecimal("24"), 4, RoundingMode.HALF_UP),
                new BigDecimal("0.95"), new BigDecimal("0.95"), DEFAULT_BATTERY_CAPACITY);
        result.setExpectedSoc(expectedSoc);

        Map<String, BigDecimal> scores = calculateScores(request, config, result);
        result.setTotalObjectiveScore(scores.get("totalScore"));
        result.setArbitrageScore(scores.get("arbitrageScore"));
        result.setLifespanScore(scores.get("lifespanScore"));
        result.setDemandScore(scores.get("demandScore"));

        result.setStatus("success");

        return result;
    }

    /**
     * <p>执行填谷控制 - 在负荷低谷时段通过充电填补用电谷值
     * <p>核心逻辑：
     * <ol>
     *   <li>从ForecastService获取今日24小时负荷预测数据，计算全天平均负荷</li>
     *   <li>填谷阈值 = 平均负荷 × 0.7（可配置），低于此阈值判定为负荷低谷</li>
     *   <li>当当前负荷 < 填谷阈值 且 SOC < maxSoc时，启动充电填谷</li>
     *   <li>计算所需充电功率 = 平均负荷 - 当前负荷</li>
     *   <li>考虑电池最大充电倍率和可用容量限制，取最小值作为实际充电功率</li>
     * </ol>
     *
     * <p>物理意义：
     * 通过在用电低谷时段给电池充电，利用低谷电价降低充电成本，
     * 同时平滑负荷曲线，提高变压器和配电设施的利用率。
     *
     * @param request 实时控制请求，包含当前负荷、SOC、电价等参数
     * @return 填谷控制结果，包含充电功率、预计SOC、预期收益等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyResultVO executeValleyFilling(RealTimeControlRequest request) {
        log.info("执行填谷 - 策略: {}, 当前负荷: {}", request.getStrategyCode(), request.getCurrentLoad());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());

        if (config.getValleyFillingEnabled() == null || !config.getValleyFillingEnabled()) {
            StrategyResultVO result = new StrategyResultVO();
            result.setStrategyCode(config.getStrategyCode());
            result.setStrategyName(config.getStrategyName());
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setStatus("success");
            result.setMessage("填谷未启用");
            result.setUrgencyLevel("NORMAL");
            return result;
        }

        BigDecimal currentLoad = request.getCurrentLoad() != null ? request.getCurrentLoad() : BigDecimal.ZERO;
        BigDecimal currentSoc = request.getCurrentSoc() != null ? request.getCurrentSoc() : new BigDecimal("50");

        BigDecimal avgLoad = calculateAverageLoad(request.getTransformerCode());
        BigDecimal valleyThreshold = avgLoad.multiply(new BigDecimal("0.7"));

        BigDecimal maxChargeRate = config.getMaxChargeRate() != null ?
                config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal maxSoc = config.getMaxSoc() != null ? config.getMaxSoc() : new BigDecimal("90");

        StrategyResultVO result = new StrategyResultVO();
        result.setStrategyCode(config.getStrategyCode());
        result.setStrategyName(config.getStrategyName());

        if (currentLoad.compareTo(valleyThreshold) < 0 && currentSoc.compareTo(maxSoc) < 0) {
            BigDecimal requiredIncrease = avgLoad.subtract(currentLoad);
            BigDecimal maxChargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxChargeRate);
            BigDecimal availableCharge = maxSoc.subtract(currentSoc).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                    .multiply(DEFAULT_BATTERY_CAPACITY).multiply(new BigDecimal("2"));

            BigDecimal chargePower = requiredIncrease.min(maxChargePower).min(availableCharge);

            if (chargePower.compareTo(BigDecimal.ZERO) > 0) {
                result.setActionType("CHARGE");
                result.setTargetPower(chargePower);
                result.setUrgencyLevel("LOW");
                result.setMessage("负荷低谷，执行填谷充电");

                List<String> actions = new ArrayList<>();
                actions.add("填谷充电 " + chargePower.setScale(2, RoundingMode.HALF_UP) + " kW");
                actions.add("当前负荷: " + currentLoad.setScale(2, RoundingMode.HALF_UP) + " kW");
                actions.add("平均负荷: " + avgLoad.setScale(2, RoundingMode.HALF_UP) + " kW");
                result.setRecommendedActions(actions);
            } else {
                result.setActionType("HOLD");
                result.setTargetPower(BigDecimal.ZERO);
                result.setUrgencyLevel("NORMAL");
                result.setMessage("SOC已满，无法执行填谷");
            }
        } else {
            result.setActionType("HOLD");
            result.setTargetPower(BigDecimal.ZERO);
            result.setUrgencyLevel("NORMAL");
            result.setMessage("负荷正常，无需填谷");
        }

        BigDecimal expectedSoc = optimizationService.calculateExpectedSoc(
                currentSoc, result.getTargetPower(),
                result.getTargetPower().divide(new BigDecimal("24"), 4, RoundingMode.HALF_UP),
                new BigDecimal("0.95"), new BigDecimal("0.95"), DEFAULT_BATTERY_CAPACITY);
        result.setExpectedSoc(expectedSoc);

        Map<String, BigDecimal> scores = calculateScores(request, config, result);
        result.setTotalObjectiveScore(scores.get("totalScore"));
        result.setArbitrageScore(scores.get("arbitrageScore"));
        result.setLifespanScore(scores.get("lifespanScore"));
        result.setDemandScore(scores.get("demandScore"));

        result.setStatus("success");

        return result;
    }

    @Override
    public String checkDemandWarning(String strategyCode, BigDecimal currentDemand, BigDecimal predictedDemand) {
        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        BigDecimal baseDemand = currentDemand != null ? currentDemand : new BigDecimal("500");
        BigDecimal threshold = baseDemand.multiply(
                config.getDemandThresholdRatio() != null ? config.getDemandThresholdRatio() : new BigDecimal("0.9"));
        BigDecimal warningThreshold = threshold.multiply(WARNING_THRESHOLD_RATIO);
        BigDecimal alarmThreshold = threshold.multiply(ALARM_THRESHOLD_RATIO);

        BigDecimal demand = predictedDemand != null ? predictedDemand : baseDemand;

        if (demand.compareTo(threshold) >= 0) {
            return "CRITICAL";
        } else if (demand.compareTo(alarmThreshold) >= 0) {
            return "ALARM";
        } else if (demand.compareTo(warningThreshold) >= 0) {
            return "WARNING";
        } else {
            return "NORMAL";
        }
    }

    @Override
    public BigDecimal calculateRequiredDischargePower(String strategyCode, BigDecimal currentDemand, BigDecimal predictedDemand) {
        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        BigDecimal demand = predictedDemand != null ? predictedDemand : currentDemand;
        BigDecimal threshold = demand.multiply(
                config.getDemandThresholdRatio() != null ? config.getDemandThresholdRatio() : new BigDecimal("0.9"));

        if (demand.compareTo(threshold) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal reduction = demand.subtract(threshold);
        BigDecimal margin = new BigDecimal("5");

        return reduction.add(margin).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * <p>计算所需充电功率 - 用于填谷场景的充电功率计算
     * <p>核心逻辑：
     * <ol>
     *   <li>如果当前SOC已达上限，返回0，不充电</li>
     *   <li>从ForecastService获取动态平均负荷，计算填谷阈值 = 平均负荷 × 0.7</li>
     *   <li>如果当前负荷 >= 填谷阈值，说明不是负荷低谷，返回0</li>
     *   <li>根据可用容量计算期望充电功率：(maxSoc - currentSoc) × 容量 × 2</li>
     *   <li>取期望充电功率与最大充电功率（受限于C-rate）的较小值</li>
     * </ol>
     *
     * <p>物理意义：
     * 确保只有在真正的负荷低谷时段才进行充电，避免在平段或峰段充电增加电费支出。
     * 充电功率同时受限于电池可用容量和最大充电倍率。
     *
     * @param strategyCode 策略编码
     * @param transformerCode 变压器编码，用于获取对应负荷预测数据
     * @param currentDemand 当前需量（kW）
     * @param currentSoc 当前SOC（%）
     * @return 所需充电功率（kW，正数表示充电）
     */
    @Override
    public BigDecimal calculateRequiredChargePower(String strategyCode, String transformerCode,
                                                   BigDecimal currentDemand, BigDecimal currentSoc) {
        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        BigDecimal maxSoc = config.getMaxSoc() != null ? config.getMaxSoc() : new BigDecimal("90");
        BigDecimal soc = currentSoc != null ? currentSoc : new BigDecimal("50");

        if (soc.compareTo(maxSoc) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgLoad = calculateAverageLoad(transformerCode);
        BigDecimal demand = currentDemand != null ? currentDemand : avgLoad;
        BigDecimal valleyThreshold = avgLoad.multiply(new BigDecimal("0.7"));

        if (demand.compareTo(valleyThreshold) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal availableCapacity = maxSoc.subtract(soc).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                .multiply(DEFAULT_BATTERY_CAPACITY);
        BigDecimal desiredPower = availableCapacity.multiply(new BigDecimal("2"));

        BigDecimal maxChargeRate = config.getMaxChargeRate() != null ?
                config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal maxChargePower = DEFAULT_BATTERY_CAPACITY.multiply(maxChargeRate);

        return desiredPower.min(maxChargePower).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public List<StrategyExecutionLogDTO> listExecutionLogs(String strategyCode, LocalDateTime startTime, LocalDateTime endTime) {
        List<StrategyExecutionLog> logs = executionLogRepository
                .findByStrategyCodeAndExecutionTimeBetweenOrderByExecutionTimeDesc(
                        strategyCode, startTime, endTime);

        return logs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StrategyExecutionLogDTO> listRecentExecutions(String strategyCode, int hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        List<StrategyExecutionLog> logs = executionLogRepository.findRecentExecutions(strategyCode, startTime);
        return logs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public StrategyStatisticsVO getExecutionStatistics(String strategyCode, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        StrategyStatisticsVO stats = new StrategyStatisticsVO();
        stats.setStatisticsDate(startDate);
        stats.setStrategyCode(strategyCode);

        List<Object[]> benefits = executionLogRepository.sumBenefitsByStrategyCodeAndDateRange(
                strategyCode, startTime, endTime);

        if (benefits != null && !benefits.isEmpty()) {
            Object[] row = benefits.get(0);
            stats.setTotalRevenue((BigDecimal) row[0]);
            stats.setTotalDegradationCost((BigDecimal) row[1]);
            stats.setTotalDemandSaving((BigDecimal) row[2]);
            if (stats.getTotalRevenue() == null) stats.setTotalRevenue(BigDecimal.ZERO);
            if (stats.getTotalDegradationCost() == null) stats.setTotalDegradationCost(BigDecimal.ZERO);
            if (stats.getTotalDemandSaving() == null) stats.setTotalDemandSaving(BigDecimal.ZERO);
            stats.setNetBenefit(stats.getTotalRevenue().add(stats.getTotalDemandSaving())
                    .subtract(stats.getTotalDegradationCost()));
        }

        List<StrategyExecutionLog> logs = executionLogRepository
                .findByStrategyCodeAndExecutionTimeBetweenOrderByExecutionTimeDesc(
                        strategyCode, startTime, endTime);

        int chargeCount = 0;
        int dischargeCount = 0;
        int successCount = 0;
        int failureCount = 0;
        BigDecimal totalChargeEnergy = BigDecimal.ZERO;
        BigDecimal totalDischargeEnergy = BigDecimal.ZERO;
        BigDecimal totalChargeRate = BigDecimal.ZERO;
        BigDecimal totalDischargeRate = BigDecimal.ZERO;
        BigDecimal maxDemandReduction = BigDecimal.ZERO;

        for (StrategyExecutionLog log : logs) {
            if ("CHARGE".equals(log.getActionTaken())) {
                chargeCount++;
                if (log.getActualPower() != null) {
                    totalChargeEnergy = totalChargeEnergy.add(log.getActualPower().divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP));
                    totalChargeRate = totalChargeRate.add(log.getActualPower().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP));
                }
            } else if ("DISCHARGE".equals(log.getActionTaken())) {
                dischargeCount++;
                if (log.getActualPower() != null) {
                    totalDischargeEnergy = totalDischargeEnergy.add(log.getActualPower().divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP));
                    totalDischargeRate = totalDischargeRate.add(log.getActualPower().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP));
                }
            }

            if ("success".equals(log.getStatus())) {
                successCount++;
            } else {
                failureCount++;
            }

            if (log.getDemandSaving() != null && log.getDemandSaving().compareTo(maxDemandReduction) > 0) {
                maxDemandReduction = log.getDemandSaving();
            }
        }

        stats.setChargeCount(chargeCount);
        stats.setDischargeCount(dischargeCount);
        stats.setTotalChargeEnergy(totalChargeEnergy.setScale(2, RoundingMode.HALF_UP));
        stats.setTotalDischargeEnergy(totalDischargeEnergy.setScale(2, RoundingMode.HALF_UP));
        stats.setAverageChargeRate(chargeCount > 0 ?
                totalChargeRate.divide(new BigDecimal(chargeCount), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        stats.setAverageDischargeRate(dischargeCount > 0 ?
                totalDischargeRate.divide(new BigDecimal(dischargeCount), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        stats.setMaxDemandReduction(maxDemandReduction);
        stats.setExecutionCount(logs.size());
        stats.setSuccessCount(successCount);
        stats.setFailureCount(failureCount);
        stats.setSuccessRate(logs.size() > 0 ?
                new BigDecimal(successCount).divide(new BigDecimal(logs.size()), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        stats.setDemandControlEvents((int) executionLogRepository.countByActionTypeAndDateRange(
                "DISCHARGE", startTime, endTime));

        return stats;
    }

    @Override
    public Map<String, Object> getRealTimeStatus(String strategyCode, String batterySn, String transformerCode) {
        Map<String, Object> status = new HashMap<>();

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        status.put("strategyCode", config.getStrategyCode());
        status.put("strategyName", config.getStrategyName());
        status.put("enabled", config.getEnabled());
        status.put("arbitrageWeight", config.getArbitrageWeight());
        status.put("lifespanWeight", config.getLifespanWeight());
        status.put("demandWeight", config.getDemandWeight());

        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        List<StrategyExecutionLog> recentLogs = executionLogRepository.findRecentExecutions(strategyCode, startTime);

        if (!recentLogs.isEmpty()) {
            StrategyExecutionLog lastLog = recentLogs.get(0);
            status.put("lastExecutionTime", lastLog.getExecutionTime());
            status.put("lastAction", lastLog.getActionTaken());
            status.put("lastPower", lastLog.getTargetPower());
            status.put("lastStatus", lastLog.getStatus());
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        List<Object[]> todayBenefits = executionLogRepository.sumBenefitsByStrategyCodeAndDateRange(
                strategyCode, todayStart, todayEnd);

        if (todayBenefits != null && !todayBenefits.isEmpty()) {
            Object[] row = todayBenefits.get(0);
            status.put("todayRevenue", row[0] != null ? row[0] : BigDecimal.ZERO);
            status.put("todayDegradationCost", row[1] != null ? row[1] : BigDecimal.ZERO);
            status.put("todayDemandSaving", row[2] != null ? row[2] : BigDecimal.ZERO);
        }

        return status;
    }

    @Override
    public Map<String, BigDecimal> getTotalBenefits(String strategyCode, LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> benefits = new HashMap<>();

        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        List<Object[]> results = executionLogRepository.sumBenefitsByStrategyCodeAndDateRange(
                strategyCode, startTime, endTime);

        if (results != null && !results.isEmpty()) {
            Object[] row = results.get(0);
            benefits.put("totalRevenue", row[0] != null ? (BigDecimal) row[0] : BigDecimal.ZERO);
            benefits.put("totalDegradationCost", row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO);
            benefits.put("totalDemandSaving", row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO);
            benefits.put("netBenefit",
                    benefits.get("totalRevenue").add(benefits.get("totalDemandSaving"))
                            .subtract(benefits.get("totalDegradationCost")));
        } else {
            benefits.put("totalRevenue", BigDecimal.ZERO);
            benefits.put("totalDegradationCost", BigDecimal.ZERO);
            benefits.put("totalDemandSaving", BigDecimal.ZERO);
            benefits.put("netBenefit", BigDecimal.ZERO);
        }

        return benefits;
    }

    @Override
    public Map<String, Object> getControlActionStatistics(String strategyCode, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        stats.put("chargeCount", executionLogRepository.countByActionTypeAndDateRange(
                "CHARGE", startTime, endTime));
        stats.put("dischargeCount", executionLogRepository.countByActionTypeAndDateRange(
                "DISCHARGE", startTime, endTime));
        stats.put("holdCount", executionLogRepository.countByActionTypeAndDateRange(
                "HOLD", startTime, endTime));

        List<Object[]> demandStats = executionLogRepository.getDemandStatsByStrategyCodeAndDateRange(
                strategyCode, startTime, endTime);

        if (demandStats != null && !demandStats.isEmpty()) {
            Object[] row = demandStats.get(0);
            stats.put("maxDemand", row[0] != null ? row[0] : BigDecimal.ZERO);
            stats.put("avgDemand", row[1] != null ? row[1] : BigDecimal.ZERO);
        }

        List<Object[]> statusStats = executionLogRepository.countByStatusSince(startTime);
        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] row : statusStats) {
            statusMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("statusStatistics", statusMap);

        return stats;
    }

    @Override
    public BigDecimal calculateBatteryDegradationCost(String strategyCode, BigDecimal chargeRate, BigDecimal dischargeRate,
                                                       BigDecimal depthOfDischarge, BigDecimal temperature) {
        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        BigDecimal penalty = optimizationService.calculateLifespanPenalty(
                chargeRate, dischargeRate, depthOfDischarge, temperature, config);

        BigDecimal batteryValue = DEFAULT_BATTERY_CAPACITY.multiply(DEFAULT_BATTERY_COST);

        return penalty.multiply(batteryValue).divide(new BigDecimal("10000"), 4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateDemandChargeSaving(String strategyCode, BigDecimal originalDemand, BigDecimal controlledDemand) {
        if (originalDemand == null || controlledDemand == null) {
            return BigDecimal.ZERO;
        }

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);
        BigDecimal threshold = originalDemand.multiply(
                config.getDemandThresholdRatio() != null ? config.getDemandThresholdRatio() : new BigDecimal("0.9"));

        BigDecimal originalExcess = originalDemand.subtract(threshold).max(BigDecimal.ZERO);
        BigDecimal controlledExcess = controlledDemand.subtract(threshold).max(BigDecimal.ZERO);

        BigDecimal saving = originalExcess.subtract(controlledExcess).max(BigDecimal.ZERO);

        return saving.multiply(DEFAULT_DEMAND_PRICE).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyResultVO executeMultiObjectiveOptimization(RealTimeControlRequest request) {
        log.info("执行多目标优化 - 策略: {}", request.getStrategyCode());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());
        StrategyResultVO result = optimizationService.optimize(request, config);

        String demandWarning = checkDemandWarning(request.getStrategyCode(),
                request.getCurrentDemand(), request.getCurrentLoad());

        if ("CRITICAL".equals(demandWarning) || "ALARM".equals(demandWarning)) {
            StrategyResultVO demandResult = executeDemandControl(request);
            if (demandResult.getActionType().equals("DISCHARGE")
                    && demandResult.getTargetPower().compareTo(BigDecimal.ZERO) > 0) {
                log.info("需量告警优先级最高，覆盖多目标优化结果");
                return demandResult;
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logExecution(StrategyResultVO result, RealTimeControlRequest request) {
        StrategyExecutionLog log = new StrategyExecutionLog();
        log.setStrategyCode(request.getStrategyCode());
        log.setExecutionTime(LocalDateTime.now());
        log.setExecutionType(request.getExecutionType());
        log.setCurrentSoc(request.getCurrentSoc());
        log.setCurrentLoad(request.getCurrentLoad());
        log.setCurrentPv(request.getCurrentPv());
        log.setCurrentDemand(request.getCurrentDemand());
        log.setPredictedDemand(request.getCurrentDemand());
        log.setCurrentPrice(request.getCurrentPrice());
        log.setActionTaken(result.getActionType());
        log.setTargetPower(result.getTargetPower());
        log.setActualPower(result.getTargetPower());
        log.setRevenue(result.getExpectedRevenue());
        log.setDegradationCost(result.getExpectedDegradationCost());
        log.setDemandSaving(result.getExpectedDemandSaving());
        log.setBatteryTemperature(request.getBatteryTemperature());
        log.setBatteryHealth(request.getBatteryHealth());
        log.setStatus(result.getStatus() != null ? result.getStatus() : "success");
        log.setErrorMessage(result.getMessage());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());
        log.setStrategyId(config.getId());

        if (request.getCurrentDemand() != null) {
            BigDecimal threshold = request.getCurrentDemand().multiply(
                    config.getDemandThresholdRatio() != null ? config.getDemandThresholdRatio() : new BigDecimal("0.9"));
            log.setDemandThreshold(threshold);
            log.setPeriodType(determinePeriodType());
        }

        executionLogRepository.save(log);
    }

    /**
     * <p>计算多目标优化得分 - 评估控制策略的综合表现
     * <p>核心逻辑：
     * <ol>
     *   <li>从TimeOfUsePriceService获取真实分时电价数据，计算加权平均电价</li>
     *   <li>套利得分：根据当前电价与平均电价的相对关系，充电时电价越低得分越高，放电时电价越高得分越高</li>
     *   <li>寿命得分：根据充放电倍率（C-rate）计算，倍率越低得分越高，采用线性变换映射到[0.3, 1.0]区间</li>
     *   <li>需量得分：控制后需量低于阈值得满分，超出部分按比例扣分</li>
     *   <li>综合得分：三项得分加权求和，权重由策略配置决定</li>
     * </ol>
     *
     * <p>物理意义：
     * 通过量化三个维度的目标实现程度，为策略选择和调整提供可比较的评价指标。
     * 所有得分均归一化到[0,1]区间，确保目标间的可加性。
     *
     * @param request 实时控制请求
     * @param config 策略配置
     * @param result 策略执行结果
     * @return 包含各项得分的Map：arbitrageScore, lifespanScore, demandScore, totalScore
     */
    private Map<String, BigDecimal> calculateScores(RealTimeControlRequest request,
                                                     StrategyConfigDTO config,
                                                     StrategyResultVO result) {
        Map<String, BigDecimal> scores = new HashMap<>();

        BigDecimal price = request.getCurrentPrice() != null ? request.getCurrentPrice() : new BigDecimal("0.5");
        BigDecimal avgPrice = calculateAveragePrice(request.getTransformerCode());

        BigDecimal arbitrageScore;
        if ("CHARGE".equals(result.getActionType()) && price.compareTo(avgPrice) < 0) {
            arbitrageScore = BigDecimal.ONE.subtract(price.divide(avgPrice, 4, RoundingMode.HALF_UP))
                    .add(BigDecimal.ONE).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
        } else if ("DISCHARGE".equals(result.getActionType()) && price.compareTo(avgPrice) > 0) {
            arbitrageScore = price.divide(avgPrice, 4, RoundingMode.HALF_UP)
                    .add(BigDecimal.ONE).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
        } else {
            arbitrageScore = new BigDecimal("0.5");
        }
        arbitrageScore = arbitrageScore.min(BigDecimal.ONE).max(BigDecimal.ZERO);

        BigDecimal chargeRate = result.getTargetPower() != null ?
                result.getTargetPower().abs().divide(DEFAULT_BATTERY_CAPACITY, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal maxRate = config.getMaxChargeRate() != null ? config.getMaxChargeRate() : new BigDecimal("0.5");
        BigDecimal lifespanScore = BigDecimal.ONE.subtract(chargeRate.divide(maxRate, 4, RoundingMode.HALF_UP))
                .multiply(new BigDecimal("0.7")).add(new BigDecimal("0.3"));
        lifespanScore = lifespanScore.min(BigDecimal.ONE).max(BigDecimal.ZERO);

        BigDecimal demand = request.getCurrentDemand() != null ? request.getCurrentDemand() :
                request.getCurrentLoad() != null ? request.getCurrentLoad() : BigDecimal.ZERO;
        BigDecimal threshold = demand.multiply(
                config.getDemandThresholdRatio() != null ? config.getDemandThresholdRatio() : new BigDecimal("0.9"));
        BigDecimal demandAfterControl = demand.subtract(
                "DISCHARGE".equals(result.getActionType()) ? result.getTargetPower() : BigDecimal.ZERO);
        BigDecimal demandScore;
        if (demandAfterControl.compareTo(threshold) <= 0) {
            demandScore = BigDecimal.ONE;
        } else {
            BigDecimal excess = demandAfterControl.subtract(threshold);
            BigDecimal maxExcess = threshold.multiply(new BigDecimal("0.5"));
            demandScore = BigDecimal.ONE.subtract(excess.divide(maxExcess, 4, RoundingMode.HALF_UP));
        }
        demandScore = demandScore.min(BigDecimal.ONE).max(BigDecimal.ZERO);

        BigDecimal totalScore = optimizationService.calculateTotalObjectiveScore(
                arbitrageScore, lifespanScore, demandScore, config);

        scores.put("arbitrageScore", arbitrageScore);
        scores.put("lifespanScore", lifespanScore);
        scores.put("demandScore", demandScore);
        scores.put("totalScore", totalScore);

        return scores;
    }

    /**
     * <p>计算动态平均负荷 - 从负荷预测数据中获取
     * <p>核心逻辑：
     * <ol>
     *   <li>调用ForecastService获取今日24小时负荷预测数据</li>
     *   <li>计算全天平均负荷作为削峰填谷的基准</li>
     *   <li>如果预测数据不可用，返回默认值400kW作为兜底</li>
     * </ol>
     *
     * @param transformerCode 变压器编码
     * @return 平均负荷（kW）
     */
    private BigDecimal calculateAverageLoad(String transformerCode) {
        try {
            List<BigDecimal> loadForecast = forecastService.generateLoadForecast(
                    transformerCode, LocalDate.now(), LocalDate.now());
            if (loadForecast != null && !loadForecast.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal load : loadForecast) {
                    if (load != null) {
                        sum = sum.add(load);
                    }
                }
                return sum.divide(new BigDecimal(loadForecast.size()), 4, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("获取负荷预测数据失败，使用默认平均负荷: {}", e.getMessage());
        }
        return new BigDecimal("400");
    }

    /**
     * <p>计算动态平均电价 - 从分时电价表中获取
     * <p>核心逻辑：
     * <ol>
     *   <li>调用TimeOfUsePriceService获取今日有效的分时电价数据</li>
     *   <li>计算加权平均电价作为峰谷套利的比较基准</li>
     *   <li>如果电价数据不可用，返回默认值0.55元/kWh作为兜底</li>
     * </ol>
     *
     * @param transformerCode 变压器编码
     * @return 加权平均电价（元/kWh）
     */
    private BigDecimal calculateAveragePrice(String transformerCode) {
        try {
            List<TimeOfUsePriceDTO> prices = timeOfUsePriceService.listValidPrices(LocalDate.now());
            if (prices != null && !prices.isEmpty()) {
                BigDecimal totalWeightedPrice = BigDecimal.ZERO;
                BigDecimal totalDuration = BigDecimal.ZERO;
                for (TimeOfUsePriceDTO price : prices) {
                    if (price != null && price.getPrice() != null && price.getDurationHours() != null) {
                        totalWeightedPrice = totalWeightedPrice.add(
                                price.getPrice().multiply(price.getDurationHours()));
                        totalDuration = totalDuration.add(price.getDurationHours());
                    }
                }
                if (totalDuration.compareTo(BigDecimal.ZERO) > 0) {
                    return totalWeightedPrice.divide(totalDuration, 4, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            log.warn("获取分时电价数据失败，使用默认平均电价: {}", e.getMessage());
        }
        return new BigDecimal("0.55");
    }

    /**
     * <p>判断当前时段类型 - 基于硬编码的峰谷时段划分
     * <p>时段划分（可配置化）：
     * <ul>
     *   <li>CRITICAL_PEAK: 10:00-12:00, 18:00-21:00</li>
     *   <li>PEAK: 07:00-10:00, 14:00-18:00</li>
     *   <li>VALLEY: 23:00-06:00</li>
     *   <li>FLAT: 其他时段</li>
     * </ul>
     *
     * @return 时段类型字符串
     */
    private String determinePeriodType() {
        int hour = LocalTime.now().getHour();
        if (hour >= 7 && hour < 10) return "PEAK";
        if (hour >= 10 && hour < 12) return "CRITICAL_PEAK";
        if (hour >= 14 && hour < 18) return "PEAK";
        if (hour >= 18 && hour < 21) return "CRITICAL_PEAK";
        if (hour >= 23 || hour < 6) return "VALLEY";
        return "FLAT";
    }

    /**
     * <p>实体转换 - 将策略执行日志实体转换为DTO
     * <p>使用Spring BeanUtils进行属性拷贝，减少手动赋值代码。
     *
     * @param log 策略执行日志实体
     * @return 策略执行日志DTO
     */
    private StrategyExecutionLogDTO convertToDTO(StrategyExecutionLog log) {
        StrategyExecutionLogDTO dto = new StrategyExecutionLogDTO();
        BeanUtils.copyProperties(log, dto);
        return dto;
    }
}
