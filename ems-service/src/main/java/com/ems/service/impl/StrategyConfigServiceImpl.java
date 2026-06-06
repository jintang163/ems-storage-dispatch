package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.strategy.StrategyConfigDTO;
import com.ems.domain.entity.StrategyConfig;
import com.ems.repository.StrategyConfigRepository;
import com.ems.service.StrategyConfigService;
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
 * 策略配置服务实现类
 * 提供策略配置的CRUD操作和多目标权重配置管理
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyConfigServiceImpl implements StrategyConfigService {

    private final StrategyConfigRepository strategyConfigRepository;

    private static final BigDecimal MIN_VALID_WEIGHT = new BigDecimal("0.01");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyConfigDTO create(StrategyConfigDTO dto) {
        log.info("创建策略配置: {}", dto.getStrategyName());
        Map<String, String> validation = validateStrategyConfig(dto);
        if (!validation.isEmpty()) {
            throw new EmsException("策略配置参数校验失败: " + validation);
        }

        if (strategyConfigRepository.findByStrategyCode(dto.getStrategyCode()).isPresent()) {
            throw new EmsException("策略编码已存在: " + dto.getStrategyCode());
        }

        if (dto.getDefaultStrategy() != null && dto.getDefaultStrategy()) {
            strategyConfigRepository.clearDefaultStrategy();
        }

        normalizeWeights(dto);

        StrategyConfig config = new StrategyConfig();
        convertToEntity(dto, config);
        config = strategyConfigRepository.save(config);

        log.info("策略配置创建成功, ID: {}", config.getId());
        return convertToDTO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrategyConfigDTO update(Long id, StrategyConfigDTO dto) {
        log.info("更新策略配置, ID: {}", id);
        Map<String, String> validation = validateStrategyConfig(dto);
        if (!validation.isEmpty()) {
            throw new EmsException("策略配置参数校验失败: " + validation);
        }

        StrategyConfig config = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("策略配置不存在, ID: " + id));

        if (!config.getStrategyCode().equals(dto.getStrategyCode())) {
            strategyConfigRepository.findByStrategyCode(dto.getStrategyCode())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new EmsException("策略编码已存在: " + dto.getStrategyCode());
                        }
                    });
        }

        if (dto.getDefaultStrategy() != null && dto.getDefaultStrategy()
                && (config.getDefaultStrategy() == null || !config.getDefaultStrategy())) {
            strategyConfigRepository.clearDefaultStrategy();
        }

        normalizeWeights(dto);
        convertToEntity(dto, config);
        config = strategyConfigRepository.save(config);

        log.info("策略配置更新成功, ID: {}", config.getId());
        return convertToDTO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("删除策略配置, ID: {}", id);
        StrategyConfig config = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("策略配置不存在, ID: " + id));
        strategyConfigRepository.delete(config);
        log.info("策略配置删除成功, ID: {}", id);
    }

    @Override
    public StrategyConfigDTO getById(Long id) {
        StrategyConfig config = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("策略配置不存在, ID: " + id));
        return convertToDTO(config);
    }

    @Override
    public StrategyConfigDTO getByStrategyCode(String strategyCode) {
        StrategyConfig config = strategyConfigRepository.findByStrategyCode(strategyCode)
                .orElseThrow(() -> new EmsException("策略配置不存在, 编码: " + strategyCode));
        return convertToDTO(config);
    }

    @Override
    public List<StrategyConfigDTO> listAll() {
        return strategyConfigRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StrategyConfigDTO> listEnabled() {
        return strategyConfigRepository.findByEnabledTrue()
                .stream()
                .sorted(Comparator.comparing(StrategyConfig::getPriority).reversed()
                        .thenComparing(StrategyConfig::getCreatedAt).reversed())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StrategyConfigDTO> listByStrategyType(String strategyType) {
        return strategyConfigRepository.findByStrategyTypeAndEnabledTrue(strategyType)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public StrategyConfigDTO getDefaultStrategy() {
        StrategyConfig config = strategyConfigRepository.findByDefaultStrategyTrueAndEnabledTrue()
                .orElseGet(() -> strategyConfigRepository.findByEnabledTrue().stream()
                        .findFirst()
                        .orElseThrow(() -> new EmsException("未找到可用的策略配置")));
        return convertToDTO(config);
    }

    @Override
    public List<StrategyConfigDTO> listByBatterySn(String batterySn) {
        return strategyConfigRepository.findByBatterySnAndEnabledTrue(batterySn)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StrategyConfigDTO> listByTransformerCode(String transformerCode) {
        return strategyConfigRepository.findByTransformerCodeAndEnabledTrue(transformerCode)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Boolean enabled) {
        StrategyConfig config = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("策略配置不存在, ID: " + id));
        config.setEnabled(enabled);
        if (!enabled && config.getDefaultStrategy()) {
            config.setDefaultStrategy(false);
        }
        strategyConfigRepository.save(config);
        log.info("策略配置状态更新, ID: {}, enabled: {}", id, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultStrategy(Long id) {
        strategyConfigRepository.clearDefaultStrategy();
        StrategyConfig config = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new EmsException("策略配置不存在, ID: " + id));
        config.setDefaultStrategy(true);
        config.setEnabled(true);
        strategyConfigRepository.save(config);
        log.info("设置默认策略, ID: {}", id);
    }

    @Override
    public Map<String, String> validateStrategyConfig(StrategyConfigDTO dto) {
        Map<String, String> errors = new HashMap<>();

        BigDecimal totalWeight = BigDecimal.ZERO;
        if (dto.getArbitrageWeight() != null) {
            totalWeight = totalWeight.add(dto.getArbitrageWeight());
        }
        if (dto.getLifespanWeight() != null) {
            totalWeight = totalWeight.add(dto.getLifespanWeight());
        }
        if (dto.getDemandWeight() != null) {
            totalWeight = totalWeight.add(dto.getDemandWeight());
        }

        if (totalWeight.compareTo(MIN_VALID_WEIGHT) < 0) {
            errors.put("weights", "权重之和必须大于0");
        }

        if (dto.getMinSoc() != null && dto.getMaxSoc() != null
                && dto.getMinSoc().compareTo(dto.getMaxSoc()) >= 0) {
            errors.put("socRange", "最小SOC必须小于最大SOC");
        }

        if (dto.getMaxChargeRate() != null && dto.getMaxChargeRate().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("maxChargeRate", "最大充电倍率必须大于0");
        }

        if (dto.getMaxDischargeRate() != null && dto.getMaxDischargeRate().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("maxDischargeRate", "最大放电倍率必须大于0");
        }

        if (dto.getMaxDepthOfDischarge() != null
                && (dto.getMaxDepthOfDischarge().compareTo(BigDecimal.ZERO) <= 0
                || dto.getMaxDepthOfDischarge().compareTo(new BigDecimal("100")) > 0)) {
            errors.put("maxDepthOfDischarge", "最大放电深度必须在0-100之间");
        }

        if (dto.getScheduleIntervalMinutes() != null
                && (dto.getScheduleIntervalMinutes() < 1 || dto.getScheduleIntervalMinutes() > 1440)) {
            errors.put("scheduleIntervalMinutes", "调度间隔必须在1-1440分钟之间");
        }

        if (dto.getLookAheadHours() != null
                && (dto.getLookAheadHours() < 1 || dto.getLookAheadHours() > 168)) {
            errors.put("lookAheadHours", "前瞻时间必须在1-168小时之间");
        }

        return errors;
    }

    @Override
    public BigDecimal normalizeWeights(StrategyConfigDTO dto) {
        BigDecimal arbitrageWeight = dto.getArbitrageWeight() != null ? dto.getArbitrageWeight() : BigDecimal.ZERO;
        BigDecimal lifespanWeight = dto.getLifespanWeight() != null ? dto.getLifespanWeight() : BigDecimal.ZERO;
        BigDecimal demandWeight = dto.getDemandWeight() != null ? dto.getDemandWeight() : BigDecimal.ZERO;

        BigDecimal totalWeight = arbitrageWeight.add(lifespanWeight).add(demandWeight);

        if (totalWeight.compareTo(MIN_VALID_WEIGHT) <= 0) {
            dto.setArbitrageWeight(new BigDecimal("0.50"));
            dto.setLifespanWeight(new BigDecimal("0.30"));
            dto.setDemandWeight(new BigDecimal("0.20"));
            return BigDecimal.ONE;
        }

        dto.setArbitrageWeight(arbitrageWeight.divide(totalWeight, 4, RoundingMode.HALF_UP));
        dto.setLifespanWeight(lifespanWeight.divide(totalWeight, 4, RoundingMode.HALF_UP));
        dto.setDemandWeight(demandWeight.divide(totalWeight, 4, RoundingMode.HALF_UP));

        return totalWeight;
    }

    @Override
    public Map<String, Long> getStrategyTypeStatistics() {
        List<Object[]> results = strategyConfigRepository.countByStrategyType();
        Map<String, Long> statistics = new HashMap<>();
        for (Object[] result : results) {
            String type = (String) result[0];
            Long count = (Long) result[1];
            statistics.put(type != null ? type : "未知", count);
        }
        return statistics;
    }

    @Override
    public long getEnabledStrategyCount() {
        return strategyConfigRepository.countEnabled();
    }

    private void convertToEntity(StrategyConfigDTO dto, StrategyConfig config) {
        BeanUtils.copyProperties(dto, config, "id");
    }

    private StrategyConfigDTO convertToDTO(StrategyConfig config) {
        StrategyConfigDTO dto = new StrategyConfigDTO();
        BeanUtils.copyProperties(config, dto);
        return dto;
    }
}
