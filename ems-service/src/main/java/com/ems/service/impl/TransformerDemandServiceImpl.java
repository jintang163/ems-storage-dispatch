package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.battery.TransformerDemandConfigDTO;
import com.ems.domain.entity.TransformerDemandConfig;
import com.ems.repository.TransformerDemandConfigRepository;
import com.ems.service.TransformerDemandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 变压器需量管理服务实现类
 * 提供变压器需量配置的CRUD操作和需量控制核心算法
 *
 * 需量控制核心算法：
 * 1. 滑动窗口需量预测：
 *    P_pred = (P_avg × T_elapsed + P_current × T_remaining) / T_total
 *    其中：
 *    - P_pred: 预测需量
 *    - P_avg: 已过时间的平均功率
 *    - P_current: 当前实时功率
 *    - T_elapsed: 已过时间（分钟）
 *    - T_remaining: 剩余时间（分钟）
 *    - T_total: 考核周期总时长（分钟）
 *
 * 2. 控制策略优先级：
 *    - 优先级1：储能放电（if SOC > 20%）
 *    - 优先级2：光伏自发自用最大化
 *    - 优先级3：可中断负荷切除
 *
 * 3. 预警机制：
 *    - 预测需量 > 阈值×80%：黄色预警
 *    - 预测需量 > 阈值×90%：橙色预警
 *    - 预测需量 > 阈值×100%：红色告警，立即执行控制
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransformerDemandServiceImpl implements TransformerDemandService {

    private final TransformerDemandConfigRepository demandConfigRepository;

    private static final BigDecimal WARNING_THRESHOLD_RATIO = new BigDecimal("0.80");
    private static final BigDecimal ALARM_THRESHOLD_RATIO = new BigDecimal("0.90");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransformerDemandConfigDTO create(TransformerDemandConfigDTO dto) {
        log.info("创建变压器需量配置: {}", dto.getTransformerCode());
        Map<String, String> validation = validateDemandConfig(dto);
        if (!validation.isEmpty()) {
            throw new EmsException("需量配置参数校验失败: " + validation);
        }

        if (demandConfigRepository.findByTransformerCode(dto.getTransformerCode()).isPresent()) {
            throw new EmsException("变压器编号已存在: " + dto.getTransformerCode());
        }

        TransformerDemandConfig config = new TransformerDemandConfig();
        convertToEntity(dto, config);
        config = demandConfigRepository.save(config);

        log.info("变压器需量配置创建成功, ID: {}", config.getId());
        return convertToDTO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransformerDemandConfigDTO update(Long id, TransformerDemandConfigDTO dto) {
        log.info("更新变压器需量配置, ID: {}", id);
        Map<String, String> validation = validateDemandConfig(dto);
        if (!validation.isEmpty()) {
            throw new EmsException("需量配置参数校验失败: " + validation);
        }

        TransformerDemandConfig config = demandConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, ID: " + id));

        if (!config.getTransformerCode().equals(dto.getTransformerCode())) {
            demandConfigRepository.findByTransformerCode(dto.getTransformerCode())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new EmsException("变压器编号已存在: " + dto.getTransformerCode());
                        }
                    });
        }

        convertToEntity(dto, config);
        config = demandConfigRepository.save(config);

        log.info("变压器需量配置更新成功, ID: {}", config.getId());
        return convertToDTO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("删除变压器需量配置, ID: {}", id);
        TransformerDemandConfig config = demandConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, ID: " + id));
        demandConfigRepository.delete(config);
        log.info("变压器需量配置删除成功, ID: {}", id);
    }

    @Override
    public TransformerDemandConfigDTO getById(Long id) {
        TransformerDemandConfig config = demandConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, ID: " + id));
        return convertToDTO(config);
    }

    @Override
    public TransformerDemandConfigDTO getByTransformerCode(String transformerCode) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));
        return convertToDTO(config);
    }

    @Override
    public List<TransformerDemandConfigDTO> listAll() {
        return demandConfigRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TransformerDemandConfigDTO> listEnabled() {
        return demandConfigRepository.findByEnabledTrue()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TransformerDemandConfigDTO> listDemandControlEnabled() {
        return demandConfigRepository.findByDemandControlEnabledTrueAndEnabledTrue()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Boolean enabled) {
        TransformerDemandConfig config = demandConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, ID: " + id));
        config.setEnabled(enabled);
        if (!enabled && config.getDemandControlEnabled()) {
            config.setDemandControlEnabled(false);
        }
        demandConfigRepository.save(config);
        log.info("变压器需量配置状态更新, ID: {}, enabled: {}", id, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDemandControlEnabled(Long id, Boolean demandControlEnabled) {
        TransformerDemandConfig config = demandConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, ID: " + id));
        if (demandControlEnabled && (config.getEnabled() == null || !config.getEnabled())) {
            throw new EmsException("需先启用变压器配置");
        }
        config.setDemandControlEnabled(demandControlEnabled);
        demandConfigRepository.save(config);
        log.info("需量控制状态更新, ID: {}, demandControlEnabled: {}", id, demandControlEnabled);
    }

    @Override
    public BigDecimal predictDemand(String transformerCode, BigDecimal currentPower,
                                     Integer cycleElapsedMinutes, List<BigDecimal> historyPowerData) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));

        int totalMinutes = config.getAssessmentCycleMinutes();
        if (cycleElapsedMinutes == null || cycleElapsedMinutes <= 0) {
            cycleElapsedMinutes = 1;
        }
        if (cycleElapsedMinutes >= totalMinutes) {
            cycleElapsedMinutes = totalMinutes - 1;
        }

        int remainingMinutes = totalMinutes - cycleElapsedMinutes;
        BigDecimal averagePower;

        if (historyPowerData != null && !historyPowerData.isEmpty()) {
            BigDecimal sum = historyPowerData.stream()
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            averagePower = sum.divide(new BigDecimal(historyPowerData.size()), 4, RoundingMode.HALF_UP);
        } else {
            averagePower = currentPower;
        }

        BigDecimal elapsedTotalPower = averagePower.multiply(new BigDecimal(cycleElapsedMinutes));
        BigDecimal remainingTotalPower = currentPower.multiply(new BigDecimal(remainingMinutes));
        BigDecimal predictedTotalPower = elapsedTotalPower.add(remainingTotalPower);
        BigDecimal predictedDemand = predictedTotalPower.divide(new BigDecimal(totalMinutes), 2, RoundingMode.HALF_UP);

        if (config.getDemandLimit() != null && predictedDemand.compareTo(config.getDemandLimit()) > 0) {
            predictedDemand = config.getDemandLimit();
        }

        log.debug("需量预测 - 变压器: {}, 当前功率: {} kW, 已过时间: {} min, 预测需量: {} kW",
                transformerCode, currentPower, cycleElapsedMinutes, predictedDemand);

        return predictedDemand;
    }

    @Override
    public BigDecimal calculatePowerReduction(String transformerCode, BigDecimal predictedDemand) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));

        BigDecimal threshold = config.getDemandThreshold();
        BigDecimal reduction = predictedDemand.subtract(threshold);

        if (reduction.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal margin = new BigDecimal("5");
        reduction = reduction.add(margin);

        if (config.getDemandLimit() != null) {
            BigDecimal maxReduction = predictedDemand.subtract(config.getDemandLimit().multiply(new BigDecimal("0.95")));
            if (reduction.compareTo(maxReduction) > 0) {
                reduction = maxReduction;
            }
        }

        return reduction.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> generateControlRecommendation(String transformerCode,
                                                              BigDecimal currentLoad,
                                                              BigDecimal currentPvPower,
                                                              BigDecimal currentSoc,
                                                              BigDecimal predictedDemand) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));

        Map<String, Object> recommendation = new HashMap<>();
        List<String> actions = new ArrayList<>();

        BigDecimal threshold = config.getDemandThreshold();
        BigDecimal thresholdRatio = predictedDemand.divide(threshold, 4, RoundingMode.HALF_UP);

        String urgencyLevel;
        if (thresholdRatio.compareTo(new BigDecimal("1.0")) >= 0) {
            urgencyLevel = "CRITICAL";
        } else if (thresholdRatio.compareTo(new BigDecimal("0.95")) >= 0) {
            urgencyLevel = "HIGH";
        } else if (thresholdRatio.compareTo(new BigDecimal("0.90")) >= 0) {
            urgencyLevel = "MEDIUM";
        } else if (thresholdRatio.compareTo(new BigDecimal("0.80")) >= 0) {
            urgencyLevel = "LOW";
        } else {
            urgencyLevel = "NORMAL";
        }

        recommendation.put("urgencyLevel", urgencyLevel);
        recommendation.put("thresholdRatio", thresholdRatio);

        BigDecimal requiredReduction = calculatePowerReduction(transformerCode, predictedDemand);
        recommendation.put("requiredReduction", requiredReduction);

        BigDecimal dischargePower = BigDecimal.ZERO;
        BigDecimal chargePower = BigDecimal.ZERO;
        BigDecimal loadSheddingPower = BigDecimal.ZERO;
        BigDecimal remainingReduction = requiredReduction;

        BigDecimal minSocProtection = config.getMinSocProtection() != null ?
                config.getMinSocProtection() : new BigDecimal("20");

        if (remainingReduction.compareTo(BigDecimal.ZERO) > 0
                && currentSoc.compareTo(minSocProtection) > 0
                && (config.getDischargePriority() == null
                || config.getDischargePriority().compareTo(new BigDecimal("50")) >= 0)) {

            BigDecimal availableDischarge = currentSoc.subtract(minSocProtection)
                    .multiply(new BigDecimal("0.5"))
                    .setScale(2, RoundingMode.HALF_UP);

            dischargePower = remainingReduction.min(availableDischarge);
            remainingReduction = remainingReduction.subtract(dischargePower);
            actions.add("储能放电 " + dischargePower + " kW");
        }

        if (remainingReduction.compareTo(BigDecimal.ZERO) > 0
                && (config.getPvSelfUsePriority() == null
                || config.getPvSelfUsePriority().compareTo(new BigDecimal("50")) >= 0)) {

            if (currentPvPower.compareTo(BigDecimal.ZERO) > 0) {
                actions.add("最大化光伏自用，当前出力 " + currentPvPower + " kW");
            }
        }

        if (remainingReduction.compareTo(BigDecimal.ZERO) > 0
                && urgencyLevel.equals("CRITICAL")
                && (config.getLoadSheddingPriority() == null
                || config.getLoadSheddingPriority().compareTo(new BigDecimal("50")) >= 0)) {

            loadSheddingPower = remainingReduction;
            actions.add("切除可中断负荷 " + loadSheddingPower + " kW");
        }

        BigDecimal netLoadAfterControl = currentLoad.subtract(dischargePower).subtract(currentPvPower).add(chargePower);
        if (netLoadAfterControl.compareTo(BigDecimal.ZERO) < 0) {
            netLoadAfterControl = BigDecimal.ZERO;
        }

        recommendation.put("dischargePower", dischargePower);
        recommendation.put("chargePower", chargePower);
        recommendation.put("loadSheddingPower", loadSheddingPower);
        recommendation.put("netLoadAfterControl", netLoadAfterControl);
        recommendation.put("predictedDemandAfterControl",
                predictedDemand.subtract(dischargePower).subtract(loadSheddingPower).max(BigDecimal.ZERO));
        recommendation.put("recommendedActions", actions);

        log.info("需量控制建议 - 变压器: {}, 紧急程度: {}, 建议放电: {} kW, 建议切负荷: {} kW, 措施: {}",
                transformerCode, urgencyLevel, dischargePower, loadSheddingPower, actions);

        return recommendation;
    }

    @Override
    public String checkDemandWarning(String transformerCode, BigDecimal predictedDemand) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));

        BigDecimal threshold = config.getDemandThreshold();
        BigDecimal warningThreshold = config.getDemandWarningThreshold() != null ?
                config.getDemandWarningThreshold() :
                threshold.multiply(WARNING_THRESHOLD_RATIO);
        BigDecimal alarmThreshold = threshold.multiply(ALARM_THRESHOLD_RATIO);

        if (predictedDemand.compareTo(threshold) >= 0) {
            return "ALARM";
        } else if (predictedDemand.compareTo(alarmThreshold) >= 0) {
            return "ALARM";
        } else if (predictedDemand.compareTo(warningThreshold) >= 0) {
            return "WARNING";
        } else {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentMaxDemand(String transformerCode, BigDecimal maxDemand) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));

        if (config.getMaxDemandCurrent() == null || maxDemand.compareTo(config.getMaxDemandCurrent()) > 0) {
            config.setMaxDemandCurrent(maxDemand);
            demandConfigRepository.save(config);
            log.info("更新当前考核周期最大需量 - 变压器: {}, 最大需量: {} kW", transformerCode, maxDemand);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetAssessmentCycle(String transformerCode) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));

        if (config.getMaxDemandCurrent() != null) {
            config.setMaxDemandPrevious(config.getMaxDemandCurrent());
        }
        config.setMaxDemandCurrent(BigDecimal.ZERO);
        demandConfigRepository.save(config);

        log.info("考核周期重置 - 变压器: {}, 上周期最大需量: {} kW",
                transformerCode, config.getMaxDemandPrevious());
    }

    @Override
    public BigDecimal calculateDemandCharge(String transformerCode, BigDecimal maxDemand, Integer days) {
        TransformerDemandConfig config = demandConfigRepository.findEnabledByTransformerCode(transformerCode)
                .orElseThrow(() -> new EmsException("变压器需量配置不存在, 编号: " + transformerCode));

        String billingMethod = config.getDemandBillingMethod();
        if (billingMethod == null) {
            billingMethod = "DEMAND";
        }

        BigDecimal charge;
        if ("CAPACITY".equalsIgnoreCase(billingMethod)) {
            BigDecimal capacityPrice = config.getCapacityPrice() != null ?
                    config.getCapacityPrice() : new BigDecimal("23");
            charge = config.getRatedCapacity().multiply(capacityPrice)
                    .multiply(new BigDecimal(days))
                    .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal demandPrice = config.getDemandPrice() != null ?
                    config.getDemandPrice() : new BigDecimal("35");
            charge = maxDemand.multiply(demandPrice)
                    .multiply(new BigDecimal(days))
                    .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        }

        return charge;
    }

    @Override
    public BigDecimal calculateDemandSaving(String transformerCode,
                                             BigDecimal originalMaxDemand,
                                             BigDecimal optimizedMaxDemand,
                                             Integer days) {
        BigDecimal originalCharge = calculateDemandCharge(transformerCode, originalMaxDemand, days);
        BigDecimal optimizedCharge = calculateDemandCharge(transformerCode, optimizedMaxDemand, days);
        return originalCharge.subtract(optimizedCharge);
    }

    @Override
    public Map<String, Long> getBillingMethodStatistics() {
        List<Object[]> results = demandConfigRepository.countByDemandBillingMethod();
        Map<String, Long> statistics = new HashMap<>();
        for (Object[] result : results) {
            String method = (String) result[0];
            Long count = (Long) result[1];
            statistics.put(method != null ? method : "未知", count);
        }
        return statistics;
    }

    @Override
    public BigDecimal getTotalRatedCapacity() {
        return demandConfigRepository.getTotalRatedCapacity();
    }

    @Override
    public Map<String, String> validateDemandConfig(TransformerDemandConfigDTO dto) {
        Map<String, String> errors = new HashMap<>();

        if (dto.getDemandThreshold() == null || dto.getDemandThreshold().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("demandThreshold", "需量阈值必须大于0");
        }

        if (dto.getAssessmentCycleMinutes() == null
                || dto.getAssessmentCycleMinutes() < 1
                || dto.getAssessmentCycleMinutes() > 1440) {
            errors.put("assessmentCycleMinutes", "考核周期必须在1-1440分钟之间");
        }

        if (dto.getRatedCapacity() == null || dto.getRatedCapacity().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("ratedCapacity", "额定容量必须大于0");
        }

        if (dto.getDemandWarningThreshold() != null && dto.getDemandThreshold() != null
                && dto.getDemandWarningThreshold().compareTo(dto.getDemandThreshold()) > 0) {
            errors.put("demandWarningThreshold", "预警阈值不能大于控制阈值");
        }

        if (dto.getDemandLimit() != null && dto.getDemandThreshold() != null
                && dto.getDemandLimit().compareTo(dto.getDemandThreshold()) < 0) {
            errors.put("demandLimit", "需量限制不能小于控制阈值");
        }

        return errors;
    }

    private void convertToEntity(TransformerDemandConfigDTO dto, TransformerDemandConfig config) {
        BeanUtils.copyProperties(dto, config);
    }

    private TransformerDemandConfigDTO convertToDTO(TransformerDemandConfig config) {
        TransformerDemandConfigDTO dto = new TransformerDemandConfigDTO();
        BeanUtils.copyProperties(config, dto);
        return dto;
    }
}
