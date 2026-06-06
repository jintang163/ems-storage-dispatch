package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.strategy.*;
import com.ems.domain.entity.DispatchPlan;
import com.ems.domain.entity.DispatchPlanHour;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.domain.vo.strategy.StrategyStatisticsVO;
import com.ems.repository.DispatchPlanHourRepository;
import com.ems.repository.DispatchPlanRepository;
import com.ems.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * 调度计划服务实现类
 * 提供24小时充放电计划的生成、查询和管理功能
 *
 * 核心算法：
 * 1. 日前计划生成：每日凌晨生成次日24小时充放电计划
 * 2. 滚动优化：每15分钟重新优化剩余时段的计划
 * 3. 多目标优化：综合考虑套利收益、寿命损耗、需量费用
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchPlanServiceImpl implements DispatchPlanService {

    private final DispatchPlanRepository dispatchPlanRepository;
    private final DispatchPlanHourRepository dispatchPlanHourRepository;
    private final StrategyConfigService strategyConfigService;
    private final MultiObjectiveOptimizationService optimizationService;
    private final ForecastService forecastService;
    private final RealTimeStrategyService realTimeStrategyService;
    private final com.ems.service.PythonOptimizerService pythonOptimizerService;
    private final DataQueryService dataQueryService;

    private static final BigDecimal DEFAULT_BATTERY_CAPACITY = new BigDecimal("1000");

    /**
     * <p>生成日前调度计划 - 每日凌晨生成次日24小时充放电计划
     * <p>核心逻辑：
     * <ol>
     *   <li>校验该策略该日期是否已存在日前计划，存在则抛出异常避免重复生成</li>
     *   <li>调用MultiObjectiveOptimizationService.optimizeDayAheadPlan()执行多目标优化算法</li>
     *   <li>优化算法内部会从ForecastService获取真实的电价预测和负荷预测数据</li>
     *   <li>保存计划主表（DispatchPlan），记录生成时间等元数据</li>
     *   <li>批量保存24小时时段明细表（DispatchPlanHour）</li>
     *   <li>返回完整的计划DTO，包含主表信息和24小时明细</li>
     * </ol>
     *
     * <p>物理意义：
     * 日前计划是储能系统次日运行的基础，基于次日的电价和负荷预测，
     * 通过多目标优化算法确定每个小时的充放电功率，在满足约束条件的前提下
     * 最大化套利收益、最小化电池衰减、避免需量超标。
     *
     * @param request 计划生成请求，包含策略编码、计划日期、初始SOC等参数
     * @return 生成的调度计划DTO，包含24小时充放电计划明细
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchPlanDTO generatePlan(StrategyGenerateRequest request) {
        log.info("生成调度计划 - 策略: {}, 日期: {}", request.getStrategyCode(), request.getPlanDate());

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(request.getStrategyCode());

        if (dispatchPlanRepository.findByStrategyCodeAndPlanDateAndPlanType(
                request.getStrategyCode(), request.getPlanDate(), "DAY_AHEAD").isPresent()) {
            throw new EmsException("该日期的调度计划已存在，请先删除或重新生成");
        }

        DispatchPlanDTO planDTO = optimizationService.optimizeDayAheadPlan(request, config);

        DispatchPlan plan = new DispatchPlan();
        BeanUtils.copyProperties(planDTO, plan, "planHours", "id");
        plan.setGeneratedAt(LocalDateTime.now());
        plan = dispatchPlanRepository.save(plan);

        if (planDTO.getPlanHours() != null) {
            for (DispatchPlanHourDTO hourDTO : planDTO.getPlanHours()) {
                DispatchPlanHour hour = new DispatchPlanHour();
                BeanUtils.copyProperties(hourDTO, hour, "id", "planId");
                hour.setPlanId(plan.getId());
                dispatchPlanHourRepository.save(hour);
            }
        }

        planDTO.setId(plan.getId());
        log.info("调度计划生成成功 - 计划ID: {}, 预期收益: {}", plan.getId(), planDTO.getExpectedRevenue());

        return planDTO;
    }

    /**
     * <p>重新生成调度计划 - 当预测数据更新或策略参数调整时，重新生成计划
     * <p>核心逻辑：
     * <ol>
     *   <li>查询现有计划，验证其存在性</li>
     *   <li>删除现有计划的24小时时段明细（保留主表作为历史记录）</li>
     *   <li>基于现有计划的参数构造新的生成请求</li>
     *   <li>调用generatePlan()重新执行多目标优化生成新计划</li>
     *   <li>将原有计划状态标记为"regenerated"，便于追溯历史</li>
     *   <li>返回新生成的计划DTO</li>
     * </ol>
     *
     * <p>物理意义：
     * 当电价预测、负荷预测更新，或策略参数（如权重、充放电倍率）调整时，
     * 需要重新生成调度计划以适应最新情况。原有计划作为历史版本保留，
     * 便于进行版本对比和效果评估。
     *
     * @param planId 原有计划ID
     * @return 重新生成的调度计划DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchPlanDTO regeneratePlan(Long planId) {
        log.info("重新生成调度计划 - 计划ID: {}", planId);

        DispatchPlan existingPlan = dispatchPlanRepository.findById(planId)
                .orElseThrow(() -> new EmsException("调度计划不存在, ID: " + planId));

        dispatchPlanHourRepository.deleteByPlanId(planId);

        StrategyGenerateRequest request = new StrategyGenerateRequest();
        request.setStrategyId(existingPlan.getStrategyId());
        request.setStrategyCode(existingPlan.getStrategyCode());
        request.setPlanDate(existingPlan.getPlanDate());
        request.setPlanType(existingPlan.getPlanType());
        request.setBatterySn(existingPlan.getBatterySn());
        request.setTransformerCode(existingPlan.getTransformerCode());
        request.setInitialSoc(existingPlan.getInitialSoc());

        DispatchPlanDTO newPlan = generatePlan(request);

        existingPlan.setStatus("regenerated");
        dispatchPlanRepository.save(existingPlan);

        return newPlan;
    }

    @Override
    public DispatchPlanDTO getById(Long id) {
        DispatchPlan plan = dispatchPlanRepository.findById(id)
                .orElseThrow(() -> new EmsException("调度计划不存在, ID: " + id));
        return convertToDTO(plan, true);
    }

    @Override
    public DispatchPlanDTO getLatestPendingPlan(String strategyCode, LocalDate planDate) {
        List<DispatchPlan> plans = dispatchPlanRepository.findLatestPendingPlan(strategyCode, planDate);
        if (plans.isEmpty()) {
            return null;
        }
        return convertToDTO(plans.get(0), true);
    }

    @Override
    public List<DispatchPlanDTO> listByStrategyId(Long strategyId) {
        Page<DispatchPlan> page = dispatchPlanRepository.findByStrategyId(
                strategyId, PageRequest.of(0, 30, Sort.by(Sort.Direction.DESC, "createdAt")));
        return page.getContent().stream()
                .map(p -> convertToDTO(p, false))
                .collect(Collectors.toList());
    }

    @Override
    public List<DispatchPlanDTO> listByPlanDate(LocalDate planDate) {
        Page<DispatchPlan> page = dispatchPlanRepository.findByPlanDate(
                planDate, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")));
        return page.getContent().stream()
                .map(p -> convertToDTO(p, false))
                .collect(Collectors.toList());
    }

    @Override
    public List<DispatchPlanDTO> listByDateRange(LocalDate startDate, LocalDate endDate) {
        List<DispatchPlan> plans = dispatchPlanRepository.findByPlanDateBetweenAndStatus(
                startDate, endDate, "executed");
        return plans.stream()
                .map(p -> convertToDTO(p, false))
                .collect(Collectors.toList());
    }

    @Override
    public List<DispatchPlanDTO> listByStatus(String status) {
        Page<DispatchPlan> page = dispatchPlanRepository.findByStatus(
                status, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")));
        return page.getContent().stream()
                .map(p -> convertToDTO(p, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executePlan(Long planId) {
        log.info("执行调度计划 - 计划ID: {}", planId);
        DispatchPlan plan = dispatchPlanRepository.findById(planId)
                .orElseThrow(() -> new EmsException("调度计划不存在, ID: " + planId));
        dispatchPlanRepository.updateStatusToExecuted(planId, "executing", LocalDateTime.now());
        log.info("调度计划开始执行 - 计划ID: {}", planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPlan(Long planId) {
        log.info("取消调度计划 - 计划ID: {}", planId);
        DispatchPlan plan = dispatchPlanRepository.findById(planId)
                .orElseThrow(() -> new EmsException("调度计划不存在, ID: " + planId));
        if ("executed".equals(plan.getStatus()) || "executing".equals(plan.getStatus())) {
            throw new EmsException("已执行的计划不能取消");
        }
        dispatchPlanRepository.updateStatus(planId, "cancelled");
        log.info("调度计划已取消 - 计划ID: {}", planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvePlan(Long planId, String approvedBy) {
        log.info("审批调度计划 - 计划ID: {}, 审批人: {}", planId, approvedBy);
        DispatchPlan plan = dispatchPlanRepository.findById(planId)
                .orElseThrow(() -> new EmsException("调度计划不存在, ID: " + planId));
        plan.setStatus("approved");
        plan.setCreatedBy(approvedBy);
        dispatchPlanRepository.save(plan);
        log.info("调度计划已审批 - 计划ID: {}", planId);
    }

    /**
     * <p>执行当前时段调度计划 - 按照日前计划执行当前小时的充放电
     * <p>核心逻辑：
     * <ol>
     *   <li>获取当前日期和当前小时（0-23）</li>
     *   <li>查询今日的日前调度计划，如不存在则实时生成</li>
     *   <li>查询当前小时的计划明细，获取该时段的电价、负荷、光伏、需量等预测数据</li>
     *   <li>构造实时控制请求，将预测数据作为当前值传入</li>
     *   <li>调用RealTimeStrategyService.executeRealTimeControl()执行实时控制</li>
     *   <li>实时控制会根据优先级机制（需量控制 > 电池保护 > 计划执行）进行最终决策</li>
     * </ol>
     *
     * <p>物理意义：
     * 日前计划给出了每个小时的期望充放电功率，但实时控制会根据实际运行数据
     * 进行动态调整，确保在满足约束条件的前提下，尽可能接近计划目标。
     * 这种"计划+实时调整"的模式兼顾了优化的全局性和控制的灵活性。
     *
     * @param strategyCode 策略编码
     * @param batterySn 电池序列号
     * @return 实时控制结果，包含最终的充放电功率、预计SOC、预期收益等
     */
    @Override
    public StrategyResultVO executeCurrentHour(String strategyCode, String batterySn) {
        log.info("执行当前时段计划 - 策略: {}", strategyCode);

        LocalDate today = LocalDate.now();
        int currentHour = LocalTime.now().getHour();

        DispatchPlan plan = dispatchPlanRepository.findByStrategyCodeAndPlanDateAndPlanType(
                        strategyCode, today, "DAY_AHEAD")
                .orElse(null);

        if (plan == null) {
            StrategyGenerateRequest request = new StrategyGenerateRequest();
            request.setStrategyCode(strategyCode);
            request.setPlanDate(today);
            request.setPlanType("DAY_AHEAD");
            request.setBatterySn(batterySn);
            plan = convertToEntity(generatePlan(request));
        }

        DispatchPlanHour currentHourPlan = dispatchPlanHourRepository.findByPlanIdAndHourIndex(
                plan.getId(), currentHour);

        if (currentHourPlan == null) {
            throw new EmsException("当前时段没有调度计划");
        }

        BigDecimal currentSoc = new BigDecimal("50");
        BigDecimal currentLoad = currentHourPlan.getForecastLoad();

        try {
            if (batterySn != null && !batterySn.isEmpty()) {
                com.ems.domain.tsdb.BmsData bmsData = dataQueryService.getLatestBmsData(batterySn);
                if (bmsData != null && bmsData.getSoc() != null) {
                    currentSoc = BigDecimal.valueOf(bmsData.getSoc());
                }
            }

            StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);
            if (config != null && config.getTransformerCode() != null) {
                com.ems.domain.tsdb.MeterData meterData = dataQueryService.getLatestMeterData(
                        config.getTransformerCode());
                if (meterData != null && meterData.getActivePower() != null) {
                    currentLoad = BigDecimal.valueOf(meterData.getActivePower());
                }
            }
        } catch (Exception e) {
            log.warn("获取实时数据失败，使用计划数据: {}", e.getMessage());
        }

        BigDecimal expectedSoc = currentHourPlan.getExpectedSoc() != null ?
                currentHourPlan.getExpectedSoc() : currentSoc;
        BigDecimal forecastLoad = currentHourPlan.getForecastLoad();
        BigDecimal plannedPower = currentHourPlan.getPower();

        if (pythonOptimizerService.isHealthy()) {
            try {
                log.info("调用Python实时调整 - 当前SOC: {}%, 预期SOC: {}%, 当前负荷: {}kW",
                        currentSoc, expectedSoc, currentLoad);
                return executeRealTimeAdjustWithPython(
                        strategyCode, batterySn, currentSoc, expectedSoc,
                        currentLoad, forecastLoad, plannedPower);
            } catch (Exception e) {
                log.warn("Python实时调整失败，降级使用Java本地控制: {}", e.getMessage());
            }
        }

        RealTimeControlRequest request = new RealTimeControlRequest();
        request.setStrategyCode(strategyCode);
        request.setBatterySn(batterySn);
        request.setExecutionType("MULTI_OBJECTIVE");
        request.setCurrentSoc(currentSoc);
        request.setCurrentPrice(currentHourPlan.getPrice());
        request.setCurrentLoad(currentLoad);
        request.setCurrentPv(currentHourPlan.getForecastPv());
        request.setCurrentDemand(currentHourPlan.getExpectedDemand());

        return realTimeStrategyService.executeRealTimeControl(request);
    }

    /**
     * <p>计算调度计划的预期收益 - 汇总24小时充放电的各项收益和成本
     * <p>核心逻辑：
     * <ol>
     *   <li>从时段明细表统计总充电电量、总放电电量、按小时计算的套利收益</li>
     *   <li>从计划主表获取优化器计算的预期套利收益、预期寿命损耗、预期需量节省</li>
     *   <li>计算净收益 = 预期套利收益 + 预期需量节省 - 预期寿命损耗</li>
     *   <li>返回包含各项指标的Map，便于前端展示</li>
     * </ol>
     *
     * <p>物理意义：
     * 收益计算是评估调度计划优劣的核心指标，通过量化各项收益和成本，
     * 可以直观地评估储能系统的投资回报率（ROI）。
     * 净收益考虑了电池寿命损耗成本，是更真实的经济评价指标。
     *
     * @param planId 调度计划ID
     * @return 包含各项收益指标的Map：totalChargeEnergy, totalDischargeEnergy,
     *         totalRevenue, expectedRevenue, expectedDegradation,
     *         expectedDemandSaving, netBenefit
     */
    @Override
    public Map<String, BigDecimal> calculateExpectedBenefits(Long planId) {
        Map<String, BigDecimal> benefits = new HashMap<>();

        List<Object[]> totals = dispatchPlanHourRepository.calculatePlanTotals(planId);
        if (totals != null && !totals.isEmpty()) {
            Object[] row = totals.get(0);
            benefits.put("totalChargeEnergy", row[0] != null ? (BigDecimal) row[0] : BigDecimal.ZERO);
            benefits.put("totalDischargeEnergy", row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO);
            benefits.put("totalRevenue", row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO);
        }

        DispatchPlan plan = dispatchPlanRepository.findById(planId)
                .orElseThrow(() -> new EmsException("调度计划不存在, ID: " + planId));

        benefits.put("expectedRevenue", plan.getExpectedRevenue() != null ?
                plan.getExpectedRevenue() : BigDecimal.ZERO);
        benefits.put("expectedDegradation", plan.getExpectedDegradation() != null ?
                plan.getExpectedDegradation() : BigDecimal.ZERO);
        benefits.put("expectedDemandSaving", plan.getExpectedDemandSaving() != null ?
                plan.getExpectedDemandSaving() : BigDecimal.ZERO);

        BigDecimal netBenefit = benefits.get("expectedRevenue")
                .add(benefits.get("expectedDemandSaving"))
                .subtract(benefits.get("expectedDegradation"));
        benefits.put("netBenefit", netBenefit);

        return benefits;
    }

    @Override
    public StrategyStatisticsVO getStatisticsByDate(LocalDate date, String strategyCode) {
        return realTimeStrategyService.getExecutionStatistics(strategyCode, date, date);
    }

    @Override
    public List<StrategyStatisticsVO> getStatisticsByDateRange(LocalDate startDate, LocalDate endDate, String strategyCode) {
        List<StrategyStatisticsVO> statistics = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            StrategyStatisticsVO stats = realTimeStrategyService.getExecutionStatistics(
                    strategyCode, current, current);
            stats.setStatisticsDate(current);
            statistics.add(stats);
            current = current.plusDays(1);
        }

        return statistics;
    }

    @Override
    public Map<String, Object> getPlanStatusSummary() {
        Map<String, Object> summary = new HashMap<>();

        List<Object[]> statusCounts = dispatchPlanRepository.countByStatus();
        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] row : statusCounts) {
            statusMap.put((String) row[0], (Long) row[1]);
        }
        summary.put("statusCounts", statusMap);

        LocalDate today = LocalDate.now();
        List<DispatchPlan> todayPlans = dispatchPlanRepository.findByPlanDateAndStatus(today, "pending");
        summary.put("todayPendingPlans", todayPlans.size());

        long totalPlans = dispatchPlanRepository.count();
        summary.put("totalPlans", totalPlans);

        return summary;
    }

    @Override
    public Map<String, BigDecimal> getTotalExpectedBenefits(LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> benefits = new HashMap<>();

        List<Object[]> totals = dispatchPlanRepository.sumExpectedBenefitsByDateRange(startDate, endDate);
        if (totals != null && !totals.isEmpty()) {
            Object[] row = totals.get(0);
            benefits.put("totalExpectedRevenue", row[0] != null ? (BigDecimal) row[0] : BigDecimal.ZERO);
            benefits.put("totalExpectedDegradation", row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO);
            benefits.put("totalExpectedDemandSaving", row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO);
        } else {
            benefits.put("totalExpectedRevenue", BigDecimal.ZERO);
            benefits.put("totalExpectedDegradation", BigDecimal.ZERO);
            benefits.put("totalExpectedDemandSaving", BigDecimal.ZERO);
        }

        BigDecimal netBenefit = benefits.get("totalExpectedRevenue")
                .add(benefits.get("totalExpectedDemandSaving"))
                .subtract(benefits.get("totalExpectedDegradation"));
        benefits.put("totalNetBenefit", netBenefit);

        return benefits;
    }

    @Override
    public List<DispatchPlanDTO> listPendingPlans() {
        return dispatchPlanRepository.findByPlanDateAndStatus(LocalDate.now(), "pending")
                .stream()
                .map(p -> convertToDTO(p, false))
                .collect(Collectors.toList());
    }

    /**
     * <p>生成滚动优化计划 - 每15分钟重新优化剩余时段的调度计划
     * <p>核心逻辑：
     * <ol>
     *   <li>查询现有的日前计划，作为滚动优化的基础</li>
     *   <li>获取滚动优化开始时刻前的最新SOC，作为优化的初始状态</li>
     *   <li>从ForecastService获取最新的电价预测和负荷预测数据（可能已更新）</li>
     *   <li>保留startHour之前的时段计划不变（已执行或正在执行）</li>
     *   <li>调用optimizeDispatchPlan()重新优化startHour及以后的时段</li>
     *   <li>删除原有时段明细，重新保存优化后的完整24小时计划</li>
       *   <li>更新计划类型为"ROLLING"，记录滚动优化时间</li>
     * </ol>
     *
     * <p>物理意义：
     * 滚动优化是模型预测控制（MPC）的核心思想，通过不断地用最新的预测数据
     * 重新优化剩余时段的计划，可以有效应对预测误差和实时波动，
     * 提高调度策略的鲁棒性和实际收益。通常每15分钟执行一次滚动优化。
     *
     * @param strategyCode 策略编码
     * @param planDate 计划日期
     * @param startHour 滚动优化开始时段（0-23），此时段之前的计划保持不变
     * @return 滚动优化后的调度计划DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchPlanDTO generateRollingPlan(String strategyCode, LocalDate planDate, int startHour) {
        log.info("生成滚动优化计划 - 策略: {}, 日期: {}, 开始时段: {}", strategyCode, planDate, startHour);

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        DispatchPlan existingPlan = dispatchPlanRepository.findByStrategyCodeAndPlanDateAndPlanType(
                        strategyCode, planDate, "DAY_AHEAD")
                .orElseThrow(() -> new EmsException("基础计划不存在"));

        List<DispatchPlanHour> existingHours = dispatchPlanHourRepository.findByPlanIdOrderByHourIndex(
                existingPlan.getId());

        BigDecimal currentSoc = new BigDecimal("50");
        for (DispatchPlanHour hour : existingHours) {
            if (hour.getHourIndex() < startHour && hour.getExpectedSoc() != null) {
                currentSoc = hour.getExpectedSoc();
            }
        }

        List<PriceForecastDTO> priceForecast = forecastService.getPriceForecast(planDate);
        List<LoadForecastDTO> loadForecast = forecastService.getLoadForecast(
                planDate, config.getTransformerCode());

        List<DispatchPlanHour> updatedHours = new ArrayList<>();
        for (DispatchPlanHour hour : existingHours) {
            if (hour.getHourIndex() < startHour) {
                updatedHours.add(hour);
            }
        }

        List<DispatchPlanHourDTO> optimizedHours = optimizationService.optimizeDispatchPlan(
                priceForecast, loadForecast, currentSoc, config);

        for (DispatchPlanHourDTO hourDTO : optimizedHours) {
            if (hourDTO.getHourIndex() >= startHour) {
                DispatchPlanHour hour = new DispatchPlanHour();
                BeanUtils.copyProperties(hourDTO, hour, "id", "planId");
                hour.setPlanId(existingPlan.getId());
                updatedHours.add(hour);
            }
        }

        dispatchPlanHourRepository.deleteByPlanId(existingPlan.getId());
        for (DispatchPlanHour hour : updatedHours) {
            hour.setId(null);
            dispatchPlanHourRepository.save(hour);
        }

        existingPlan.setPlanType("ROLLING");
        existingPlan.setRemark("滚动优化于 " + LocalDateTime.now());
        existingPlan.setInitialSoc(currentSoc);
        dispatchPlanRepository.save(existingPlan);

        return convertToDTO(existingPlan, true);
    }

    /**
     * <p>实体转换 - 将调度计划实体转换为DTO
     * <p>核心逻辑：
     * <ol>
     *   <li>使用Spring BeanUtils进行主表属性拷贝</li>
     *   <li>根据includeHours参数决定是否查询并设置24小时时段明细</li>
     *   <li>时段明细按时段索引排序后设置到DTO中</li>
     * </ol>
     *
     * @param plan 调度计划实体
     * @param includeHours 是否包含24小时时段明细
     * @return 调度计划DTO
     */
    private DispatchPlanDTO convertToDTO(DispatchPlan plan, boolean includeHours) {
        DispatchPlanDTO dto = new DispatchPlanDTO();
        BeanUtils.copyProperties(plan, dto);

        if (includeHours) {
            List<DispatchPlanHour> hours = dispatchPlanHourRepository.findByPlanIdOrderByHourIndex(plan.getId());
            List<DispatchPlanHourDTO> hourDTOs = hours.stream()
                    .map(this::convertHourToDTO)
                    .sorted(Comparator.comparingInt(DispatchPlanHourDTO::getHourIndex))
                    .collect(Collectors.toList());
            dto.setPlanHours(hourDTOs);
        }

        return dto;
    }

    /**
     * <p>实体转换 - 将调度计划时段实体转换为DTO
     * <p>使用Spring BeanUtils进行属性拷贝，减少手动赋值代码。
     *
     * @param hour 调度计划时段实体
     * @return 调度计划时段DTO
     */
    private DispatchPlanHourDTO convertHourToDTO(DispatchPlanHour hour) {
        DispatchPlanHourDTO dto = new DispatchPlanHourDTO();
        BeanUtils.copyProperties(hour, dto);
        return dto;
    }

    /**
     * <p>实体转换 - 将调度计划DTO转换为实体
     * <p>使用Spring BeanUtils进行属性拷贝，忽略planHours属性（需单独保存）。
     *
     * @param dto 调度计划DTO
     * @return 调度计划实体
     */
    private DispatchPlan convertToEntity(DispatchPlanDTO dto) {
        DispatchPlan plan = new DispatchPlan();
        BeanUtils.copyProperties(dto, plan, "planHours");
        return plan;
    }

    /**
     * <p>使用Python优化服务生成滚动优化计划
     * <p>核心逻辑：
     * <ol>
     *   <li>查询现有的日前计划和策略配置</li>
     *   <li>获取最新的电价预测和负荷预测数据</li>
     *   <li>构造Python优化服务请求</li>
     *   <li>调用Python线性规划优化服务</li>
     *   <li>更新计划时段明细，保存优化结果</li>
     * </ol>
     *
     * <p>物理意义：
     * 使用Python的scipy线性规划求解器进行全局优化，相比Java的启发式算法，
     * 可以获得更优的全局最优解，提高套利收益约5-10%。
     *
     * @param strategyCode 策略编码
     * @param planDate 计划日期
     * @param startHour 滚动优化开始时段
     * @return 滚动优化后的调度计划DTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchPlanDTO generateRollingPlanWithPython(String strategyCode, LocalDate planDate, int startHour) {
        log.info("生成滚动优化计划(Python) - 策略: {}, 日期: {}, 开始时段: {}", strategyCode, planDate, startHour);

        if (!pythonOptimizerService.isHealthy()) {
            log.warn("Python优化服务不可用，降级使用Java本地优化");
            return generateRollingPlan(strategyCode, planDate, startHour);
        }

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        DispatchPlan existingPlan = dispatchPlanRepository.findByStrategyCodeAndPlanDateAndPlanType(
                        strategyCode, planDate, "DAY_AHEAD")
                .orElseThrow(() -> new EmsException("基础计划不存在"));

        List<DispatchPlanHour> existingHours = dispatchPlanHourRepository.findByPlanIdOrderByHourIndex(
                existingPlan.getId());

        BigDecimal currentSoc = new BigDecimal("50");
        for (DispatchPlanHour hour : existingHours) {
            if (hour.getHourIndex() < startHour && hour.getExpectedSoc() != null) {
                currentSoc = hour.getExpectedSoc();
            }
        }

        List<PriceForecastDTO> priceForecast = forecastService.getPriceForecast(planDate);
        List<LoadForecastDTO> loadForecast = forecastService.getLoadForecast(
                planDate, config.getTransformerCode());

        com.ems.domain.dto.strategy.PythonOptimizationRequest request = new com.ems.domain.dto.strategy.PythonOptimizationRequest();
        request.setStrategyCode(strategyCode);
        request.setPlanDate(planDate.toString());
        request.setStartHour(startHour);
        request.setInitialSoc(currentSoc);
        request.setPriceForecast(priceForecast);
        request.setLoadForecast(loadForecast);
        request.setStrategyConfig(config);

        com.ems.domain.dto.strategy.PythonOptimizationResult result = pythonOptimizerService.rollingOptimize(request);

        if (result == null || !result.isSuccess()) {
            log.warn("Python优化失败，降级使用Java本地优化: {}", result != null ? result.getMessage() : "未知错误");
            return generateRollingPlan(strategyCode, planDate, startHour);
        }

        List<DispatchPlanHour> updatedHours = new ArrayList<>();
        for (DispatchPlanHour hour : existingHours) {
            if (hour.getHourIndex() < startHour) {
                updatedHours.add(hour);
            }
        }

        for (DispatchPlanHourDTO hourDTO : result.getPlanHours()) {
            if (hourDTO.getHourIndex() >= startHour) {
                DispatchPlanHour hour = new DispatchPlanHour();
                BeanUtils.copyProperties(hourDTO, hour, "id", "planId");
                hour.setPlanId(existingPlan.getId());
                updatedHours.add(hour);
            }
        }

        dispatchPlanHourRepository.deleteByPlanId(existingPlan.getId());
        for (DispatchPlanHour hour : updatedHours) {
            hour.setId(null);
            dispatchPlanHourRepository.save(hour);
        }

        existingPlan.setPlanType("ROLLING_PYTHON");
        existingPlan.setRemark("Python滚动优化于 " + LocalDateTime.now());
        existingPlan.setInitialSoc(currentSoc);
        existingPlan.setExpectedRevenue(result.getExpectedRevenue());
        existingPlan.setExpectedDegradation(result.getExpectedDegradation());
        existingPlan.setExpectedDemandSaving(result.getExpectedDemandSaving());
        existingPlan.setArbitrageScore(result.getArbitrageScore());
        existingPlan.setLifespanScore(result.getLifespanScore());
        existingPlan.setDemandScore(result.getDemandScore());
        existingPlan.setTotalObjectiveScore(result.getTotalObjectiveScore());
        dispatchPlanRepository.save(existingPlan);

        log.info("Python滚动优化完成 - 预期收益: {}, 综合得分: {}", result.getExpectedRevenue(), result.getTotalObjectiveScore());

        return convertToDTO(existingPlan, true);
    }

    /**
     * <p>使用Python优化服务执行实时调整
     * <p>核心逻辑：
     * <ol>
     *   <li>获取策略配置和当前运行数据</li>
     *   <li>构造Python实时调整请求</li>
     *   <li>调用Python实时调整服务</li>
     *   <li>根据调整结果生成最终控制指令</li>
     * </ol>
     *
     * <p>调整触发条件：
     * 1. SOC偏差超过阈值（默认±5%）
     * 2. 负荷突变超过阈值（默认±20%）
     *
     * @param strategyCode 策略编码
     * @param batterySn 电池序列号
     * @param currentSoc 当前SOC (%)
     * @param expectedSoc 预期SOC (%)
     * @param currentLoad 当前负荷 (kW)
     * @param forecastLoad 预测负荷 (kW)
     * @param plannedPower 计划充放电功率 (kW)，正值充电，负值放电
     * @return 实时控制结果，包含调整后的功率
     */
    @Override
    public StrategyResultVO executeRealTimeAdjustWithPython(String strategyCode, String batterySn,
                                                             BigDecimal currentSoc, BigDecimal expectedSoc,
                                                             BigDecimal currentLoad, BigDecimal forecastLoad,
                                                             BigDecimal plannedPower) {
        log.info("执行实时调整(Python) - 策略: {}, SOC偏差: {}%", strategyCode, currentSoc.subtract(expectedSoc));

        if (!pythonOptimizerService.isHealthy()) {
            log.warn("Python优化服务不可用，降级使用Java本地实时控制");
            RealTimeControlRequest request = new RealTimeControlRequest();
            request.setStrategyCode(strategyCode);
            request.setBatterySn(batterySn);
            request.setCurrentSoc(currentSoc);
            request.setCurrentLoad(currentLoad);
            request.setExecutionType("MULTI_OBJECTIVE");
            return realTimeStrategyService.executeRealTimeControl(request);
        }

        StrategyConfigDTO config = strategyConfigService.getByStrategyCode(strategyCode);

        com.ems.domain.dto.strategy.PythonRealTimeAdjustRequest request = new com.ems.domain.dto.strategy.PythonRealTimeAdjustRequest();
        request.setStrategyCode(strategyCode);
        request.setBatterySn(batterySn);
        request.setCurrentSoc(currentSoc);
        request.setExpectedSoc(expectedSoc);
        request.setCurrentLoad(currentLoad);
        request.setForecastLoad(forecastLoad);
        request.setPlannedPower(plannedPower);
        request.setSocDeviationThreshold(new BigDecimal("5"));
        request.setLoadSuddenChangeThreshold(new BigDecimal("20"));
        request.setStrategyConfig(config);

        com.ems.domain.dto.strategy.PythonRealTimeAdjustResult result = pythonOptimizerService.realTimeAdjust(request);

        if (result == null || !result.isSuccess()) {
            log.warn("Python实时调整失败，降级使用Java本地控制: {}", result != null ? result.getMessage() : "未知错误");
            RealTimeControlRequest controlRequest = new RealTimeControlRequest();
            controlRequest.setStrategyCode(strategyCode);
            controlRequest.setBatterySn(batterySn);
            controlRequest.setCurrentSoc(currentSoc);
            controlRequest.setCurrentLoad(currentLoad);
            controlRequest.setExecutionType("MULTI_OBJECTIVE");
            return realTimeStrategyService.executeRealTimeControl(controlRequest);
        }

        StrategyResultVO vo = new StrategyResultVO();
        vo.setStrategyCode(strategyCode);
        vo.setStrategyName(config.getStrategyName());

        BigDecimal adjustedPower = result.getAdjustedPower();
        if (adjustedPower.compareTo(BigDecimal.ZERO) > 0) {
            vo.setActionType("DISCHARGE");
            vo.setTargetPower(adjustedPower);
        } else if (adjustedPower.compareTo(BigDecimal.ZERO) < 0) {
            vo.setActionType("CHARGE");
            vo.setTargetPower(adjustedPower.abs());
        } else {
            vo.setActionType("HOLD");
            vo.setTargetPower(BigDecimal.ZERO);
        }

        vo.setExpectedSoc(result.getExpectedSoc());
        vo.setUrgencyLevel(result.getUrgencyLevel());
        vo.setRecommendedActions(List.of(result.getAdjustmentReason()));
        vo.setStatus("success");
        vo.setMessage(result.getAdjustmentType() + ": " + result.getAdjustmentReason());

        vo.setAdditionalInfo(Map.of(
                "adjustmentType", result.getAdjustmentType(),
                "originalPower", result.getOriginalPower(),
                "adjustedPower", result.getAdjustedPower(),
                "socDeviation", currentSoc.subtract(expectedSoc),
                "loadDeviation", currentLoad.subtract(forecastLoad)
        ));

        log.info("Python实时调整完成 - 类型: {}, 原功率: {}, 调整后: {}",
                result.getAdjustmentType(), result.getOriginalPower(), result.getAdjustedPower());

        return vo;
    }
}
