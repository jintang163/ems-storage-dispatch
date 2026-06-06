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

    private static final BigDecimal DEFAULT_BATTERY_CAPACITY = new BigDecimal("1000");

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

        RealTimeControlRequest request = new RealTimeControlRequest();
        request.setStrategyCode(strategyCode);
        request.setBatterySn(batterySn);
        request.setExecutionType("MULTI_OBJECTIVE");
        request.setCurrentPrice(currentHourPlan.getPrice());
        request.setCurrentLoad(currentHourPlan.getForecastLoad());
        request.setCurrentPv(currentHourPlan.getForecastPv());
        request.setCurrentDemand(currentHourPlan.getExpectedDemand());

        return realTimeStrategyService.executeRealTimeControl(request);
    }

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

    private DispatchPlanHourDTO convertHourToDTO(DispatchPlanHour hour) {
        DispatchPlanHourDTO dto = new DispatchPlanHourDTO();
        BeanUtils.copyProperties(hour, dto);
        return dto;
    }

    private DispatchPlan convertToEntity(DispatchPlanDTO dto) {
        DispatchPlan plan = new DispatchPlan();
        BeanUtils.copyProperties(dto, plan, "planHours");
        return plan;
    }
}
