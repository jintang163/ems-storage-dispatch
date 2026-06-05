package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.battery.BatteryDegradationModelDTO;
import com.ems.domain.dto.battery.BatteryDegradationPointDTO;
import com.ems.domain.entity.BatteryDegradationModel;
import com.ems.domain.entity.BatteryDegradationPoint;
import com.ems.repository.BatteryDegradationModelRepository;
import com.ems.service.BatteryDegradationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 电池衰减模型服务实现类
 * 提供电池衰减模型的CRUD操作和SOH预估核心算法
 *
 * SOH预估算法：
 * 1. 线性衰减模型：SOH = 1 - (cycleCount × degradationRatePerCycle)
 * 2. 指数衰减模型：SOH = exp(-cycleCount × decayConstant)
 * 3. 分段线性衰减：根据循环次数分段应用不同衰减率
 * 4. 经验公式模型：基于厂家提供的衰减曲线数据点线性插值
 *
 * 环境影响因子：
 * - 温度：高温加速衰减，低温也有影响
 * - SOC：长期高SOC存储加速老化
 * - 充放电倍率：大倍率充放电加速衰减
 * - 放电深度：深循环加速衰减
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatteryDegradationServiceImpl implements BatteryDegradationService {

    private final BatteryDegradationModelRepository degradationModelRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatteryDegradationModelDTO create(BatteryDegradationModelDTO dto) {
        log.info("创建电池衰减模型: {}", dto.getModelName());
        Map<String, String> validation = validateDegradationModel(dto);
        if (!validation.isEmpty()) {
            throw new EmsException("衰减模型参数校验失败: " + validation);
        }

        if (dto.getDefaultModel() != null && dto.getDefaultModel()) {
            degradationModelRepository.findByDefaultModelTrueAndEnabledTrue()
                    .ifPresent(existing -> {
                        existing.setDefaultModel(false);
                        degradationModelRepository.save(existing);
                    });
        }

        BatteryDegradationModel model = new BatteryDegradationModel();
        convertToEntity(dto, model);

        if (dto.getDegradationPoints() != null) {
            for (BatteryDegradationPointDTO pointDTO : dto.getDegradationPoints()) {
                BatteryDegradationPoint point = new BatteryDegradationPoint();
                BeanUtils.copyProperties(pointDTO, point, "id");
                model.addDegradationPoint(point);
            }
        }

        model = degradationModelRepository.save(model);
        log.info("电池衰减模型创建成功, ID: {}", model.getId());
        return convertToDTO(model, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatteryDegradationModelDTO update(Long id, BatteryDegradationModelDTO dto) {
        log.info("更新电池衰减模型, ID: {}", id);
        Map<String, String> validation = validateDegradationModel(dto);
        if (!validation.isEmpty()) {
            throw new EmsException("衰减模型参数校验失败: " + validation);
        }

        BatteryDegradationModel model = degradationModelRepository.findById(id)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + id));

        if (dto.getDefaultModel() != null && dto.getDefaultModel()
                && (model.getDefaultModel() == null || !model.getDefaultModel())) {
            degradationModelRepository.findByDefaultModelTrueAndEnabledTrue()
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            existing.setDefaultModel(false);
                            degradationModelRepository.save(existing);
                        }
                    });
        }

        convertToEntity(dto, model);

        if (dto.getDegradationPoints() != null) {
            model.getDegradationPoints().clear();
            for (BatteryDegradationPointDTO pointDTO : dto.getDegradationPoints()) {
                BatteryDegradationPoint point = new BatteryDegradationPoint();
                BeanUtils.copyProperties(pointDTO, point, "id");
                model.addDegradationPoint(point);
            }
        }

        model = degradationModelRepository.save(model);
        log.info("电池衰减模型更新成功, ID: {}", model.getId());
        return convertToDTO(model, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("删除电池衰减模型, ID: {}", id);
        BatteryDegradationModel model = degradationModelRepository.findById(id)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + id));
        degradationModelRepository.delete(model);
        log.info("电池衰减模型删除成功, ID: {}", id);
    }

    @Override
    public BatteryDegradationModelDTO getById(Long id) {
        BatteryDegradationModel model = degradationModelRepository.findById(id)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + id));
        return convertToDTO(model, true);
    }

    @Override
    public List<BatteryDegradationModelDTO> listAll() {
        return degradationModelRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(m -> convertToDTO(m, false))
                .collect(Collectors.toList());
    }

    @Override
    public List<BatteryDegradationModelDTO> listEnabled() {
        return degradationModelRepository.findByEnabledTrue()
                .stream()
                .map(m -> convertToDTO(m, false))
                .collect(Collectors.toList());
    }

    @Override
    public List<BatteryDegradationModelDTO> listByModelType(String modelType) {
        return degradationModelRepository.findByModelTypeAndEnabledTrue(modelType)
                .stream()
                .map(m -> convertToDTO(m, false))
                .collect(Collectors.toList());
    }

    @Override
    public List<BatteryDegradationModelDTO> listByBatteryType(String batteryType) {
        return degradationModelRepository.findByBatteryTypeAndEnabledTrue(batteryType)
                .stream()
                .map(m -> convertToDTO(m, false))
                .collect(Collectors.toList());
    }

    @Override
    public BatteryDegradationModelDTO getDefaultModel() {
        BatteryDegradationModel model = degradationModelRepository.findByDefaultModelTrueAndEnabledTrue()
                .orElseThrow(() -> new EmsException("未找到默认衰减模型"));
        return convertToDTO(model, true);
    }

    @Override
    public BatteryDegradationModelDTO getDefaultModelByBatteryType(String batteryType) {
        BatteryDegradationModel model = degradationModelRepository.findDefaultByBatteryType(batteryType)
                .orElseGet(() -> degradationModelRepository.findByDefaultModelTrueAndEnabledTrue()
                        .orElseThrow(() -> new EmsException("未找到衰减模型, 电池类型: " + batteryType)));
        return convertToDTO(model, true);
    }

    @Override
    public BigDecimal estimateSoh(Long modelId, Integer cycleCount) {
        BatteryDegradationModel model = degradationModelRepository.findById(modelId)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + modelId));

        if (cycleCount == null || cycleCount <= 0) {
            return BigDecimal.ONE;
        }

        BigDecimal soh;
        String modelType = model.getModelType();

        switch (modelType.toUpperCase()) {
            case "LINEAR":
                soh = calculateLinearDegradation(model, cycleCount);
                break;
            case "EXPONENTIAL":
                soh = calculateExponentialDegradation(model, cycleCount);
                break;
            case "PIECEWISE":
                soh = calculatePiecewiseDegradation(model, cycleCount);
                break;
            case "EMPIRICAL":
                soh = calculateEmpiricalDegradation(model, cycleCount);
                break;
            default:
                soh = calculateLinearDegradation(model, cycleCount);
        }

        if (soh.compareTo(model.getEndOfLifeSoh()) < 0) {
            soh = model.getEndOfLifeSoh();
        }
        if (soh.compareTo(BigDecimal.ONE) > 0) {
            soh = BigDecimal.ONE;
        }

        return soh.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal estimateSohWithFactors(Long modelId, Integer cycleCount,
                                              BigDecimal avgTemperature, BigDecimal avgSoc,
                                              BigDecimal avgChargeRate, BigDecimal avgDischargeRate,
                                              BigDecimal avgDepthOfDischarge) {
        BigDecimal baseSoh = estimateSoh(modelId, cycleCount);
        BatteryDegradationModel model = degradationModelRepository.findById(modelId)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + modelId));

        BigDecimal totalFactor = BigDecimal.ONE;

        if (avgTemperature != null && model.getTemperatureFactor() != null) {
            BigDecimal tempDiff = avgTemperature.subtract(new BigDecimal("25")).abs();
            BigDecimal tempFactor = BigDecimal.ONE.add(
                    tempDiff.multiply(model.getTemperatureFactor()).divide(new BigDecimal("10"), 8, RoundingMode.HALF_UP));
            totalFactor = totalFactor.multiply(tempFactor);
        }

        if (avgSoc != null && model.getSocFactor() != null) {
            BigDecimal socDiff = avgSoc.subtract(new BigDecimal("50")).abs();
            BigDecimal socFactor = BigDecimal.ONE.add(
                    socDiff.multiply(model.getSocFactor()).divide(new BigDecimal("10"), 8, RoundingMode.HALF_UP));
            totalFactor = totalFactor.multiply(socFactor);
        }

        if (avgChargeRate != null && model.getChargeRateFactor() != null) {
            if (avgChargeRate.compareTo(BigDecimal.ONE) > 0) {
                BigDecimal rateFactor = BigDecimal.ONE.add(
                        avgChargeRate.subtract(BigDecimal.ONE).multiply(model.getChargeRateFactor()));
                totalFactor = totalFactor.multiply(rateFactor);
            }
        }

        if (avgDischargeRate != null && model.getDischargeRateFactor() != null) {
            if (avgDischargeRate.compareTo(BigDecimal.ONE) > 0) {
                BigDecimal rateFactor = BigDecimal.ONE.add(
                        avgDischargeRate.subtract(BigDecimal.ONE).multiply(model.getDischargeRateFactor()));
                totalFactor = totalFactor.multiply(rateFactor);
            }
        }

        if (avgDepthOfDischarge != null && model.getDepthOfDischargeFactor() != null) {
            if (avgDepthOfDischarge.compareTo(new BigDecimal("80")) > 0) {
                BigDecimal dodFactor = BigDecimal.ONE.add(
                        avgDepthOfDischarge.subtract(new BigDecimal("80"))
                                .multiply(model.getDepthOfDischargeFactor()).divide(new BigDecimal("10"), 8, RoundingMode.HALF_UP));
                totalFactor = totalFactor.multiply(dodFactor);
            }
        }

        BigDecimal adjustedSoh = BigDecimal.ONE.subtract(
                BigDecimal.ONE.subtract(baseSoh).multiply(totalFactor));

        if (adjustedSoh.compareTo(model.getEndOfLifeSoh()) < 0) {
            adjustedSoh = model.getEndOfLifeSoh();
        }

        return adjustedSoh.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Integer estimateRemainingCycles(Long modelId, BigDecimal currentSoh, Integer currentCycleCount) {
        BatteryDegradationModel model = degradationModelRepository.findById(modelId)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + modelId));

        BigDecimal endOfLifeSoh = model.getEndOfLifeSoh();
        if (currentSoh.compareTo(endOfLifeSoh) <= 0) {
            return 0;
        }

        int remainingCycles = 0;
        BigDecimal testSoh = currentSoh;
        int testCycles = currentCycleCount;

        while (testSoh.compareTo(endOfLifeSoh) > 0) {
            testCycles += 10;
            testSoh = estimateSoh(modelId, testCycles);
            remainingCycles += 10;

            if (remainingCycles > 100000) {
                break;
            }
        }

        return remainingCycles;
    }

    @Override
    public BigDecimal estimateRemainingLifespan(Long modelId, BigDecimal currentSoh,
                                                 Integer currentCycleCount, BigDecimal dailyCycles) {
        Integer remainingCycles = estimateRemainingCycles(modelId, currentSoh, currentCycleCount);

        if (dailyCycles == null || dailyCycles.compareTo(BigDecimal.ZERO) <= 0) {
            dailyCycles = new BigDecimal("0.5");
        }

        BigDecimal remainingDays = new BigDecimal(remainingCycles).divide(dailyCycles, 2, RoundingMode.HALF_UP);
        BigDecimal remainingYears = remainingDays.divide(new BigDecimal("365"), 2, RoundingMode.HALF_UP);

        return remainingYears;
    }

    @Override
    public List<BatteryDegradationPointDTO> generateDegradationCurve(Long modelId,
                                                                      Integer startCycle,
                                                                      Integer endCycle,
                                                                      Integer step) {
        List<BatteryDegradationPointDTO> curve = new ArrayList<>();

        for (int cycle = startCycle; cycle <= endCycle; cycle += step) {
            BigDecimal soh = estimateSoh(modelId, cycle);
            BatteryDegradationPointDTO point = new BatteryDegradationPointDTO();
            point.setCycleCount(cycle);
            point.setSoh(soh);
            point.setCapacityRetention(soh);
            curve.add(point);
        }

        return curve;
    }

    @Override
    public BigDecimal calculateCalendarAging(Long modelId, LocalDate startDate, LocalDate endDate,
                                              BigDecimal storageSoc, BigDecimal storageTemperature) {
        BatteryDegradationModel model = degradationModelRepository.findById(modelId)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + modelId));

        if (model.getCalendarAgingRatePerYear() == null) {
            return BigDecimal.ZERO;
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal years = new BigDecimal(daysBetween).divide(new BigDecimal("365"), 8, RoundingMode.HALF_UP);
        BigDecimal baseAging = years.multiply(model.getCalendarAgingRatePerYear());

        BigDecimal factor = BigDecimal.ONE;
        if (storageSoc != null && storageSoc.compareTo(new BigDecimal("60")) > 0) {
            factor = factor.multiply(new BigDecimal("1.5"));
        }
        if (storageTemperature != null && storageTemperature.compareTo(new BigDecimal("30")) > 0) {
            factor = factor.multiply(new BigDecimal("2.0"));
        }

        return baseAging.multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Boolean enabled) {
        BatteryDegradationModel model = degradationModelRepository.findById(id)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + id));
        model.setEnabled(enabled);
        if (!enabled && model.getDefaultModel() != null && model.getDefaultModel()) {
            model.setDefaultModel(false);
        }
        degradationModelRepository.save(model);
        log.info("衰减模型状态更新, ID: {}, enabled: {}", id, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultModel(Long id) {
        degradationModelRepository.findByDefaultModelTrueAndEnabledTrue()
                .ifPresent(existing -> {
                    existing.setDefaultModel(false);
                    degradationModelRepository.save(existing);
                });

        BatteryDegradationModel model = degradationModelRepository.findById(id)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + id));
        model.setDefaultModel(true);
        model.setEnabled(true);
        degradationModelRepository.save(model);
        log.info("设置默认衰减模型, ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatteryDegradationModelDTO addDegradationPoint(Long modelId, BatteryDegradationPointDTO pointDTO) {
        BatteryDegradationModel model = degradationModelRepository.findById(modelId)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + modelId));

        BatteryDegradationPoint point = new BatteryDegradationPoint();
        BeanUtils.copyProperties(pointDTO, point, "id");
        model.addDegradationPoint(point);

        model = degradationModelRepository.save(model);
        return convertToDTO(model, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatteryDegradationModelDTO addDegradationPoints(Long modelId, List<BatteryDegradationPointDTO> pointDTOs) {
        BatteryDegradationModel model = degradationModelRepository.findById(modelId)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + modelId));

        for (BatteryDegradationPointDTO pointDTO : pointDTOs) {
            BatteryDegradationPoint point = new BatteryDegradationPoint();
            BeanUtils.copyProperties(pointDTO, point, "id");
            model.addDegradationPoint(point);
        }

        model = degradationModelRepository.save(model);
        return convertToDTO(model, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDegradationPoint(Long modelId, Long pointId) {
        BatteryDegradationModel model = degradationModelRepository.findById(modelId)
                .orElseThrow(() -> new EmsException("衰减模型不存在, ID: " + modelId));

        Optional<BatteryDegradationPoint> pointToRemove = model.getDegradationPoints().stream()
                .filter(p -> p.getId().equals(pointId))
                .findFirst();

        pointToRemove.ifPresent(point -> {
            model.removeDegradationPoint(point);
            degradationModelRepository.save(model);
        });
    }

    @Override
    public List<BatteryDegradationPointDTO> generateStandardLFPCurve() {
        List<BatteryDegradationPointDTO> curve = new ArrayList<>();
        int[] cycles = {0, 500, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000};
        double[] sohs = {1.0, 0.98, 0.96, 0.92, 0.88, 0.84, 0.80, 0.76, 0.72, 0.68, 0.60};

        for (int i = 0; i < cycles.length; i++) {
            BatteryDegradationPointDTO point = new BatteryDegradationPointDTO();
            point.setCycleCount(cycles[i]);
            point.setSoh(BigDecimal.valueOf(sohs[i]));
            point.setCapacityRetention(BigDecimal.valueOf(sohs[i]));
            point.setDepthOfDischarge(BigDecimal.valueOf(80));
            point.setTemperature(BigDecimal.valueOf(25));
            point.setChargeRate(BigDecimal.valueOf(0.5));
            point.setDischargeRate(BigDecimal.valueOf(0.5));
            point.setRemarks("磷酸铁锂电池标准衰减曲线 (80% DOD, 25°C, 0.5C)");
            curve.add(point);
        }
        return curve;
    }

    @Override
    public List<BatteryDegradationPointDTO> generateStandardNMCCurve() {
        List<BatteryDegradationPointDTO> curve = new ArrayList<>();
        int[] cycles = {0, 300, 500, 800, 1000, 1500, 2000, 2500, 3000, 4000, 5000};
        double[] sohs = {1.0, 0.96, 0.92, 0.86, 0.82, 0.74, 0.68, 0.62, 0.58, 0.50, 0.45};

        for (int i = 0; i < cycles.length; i++) {
            BatteryDegradationPointDTO point = new BatteryDegradationPointDTO();
            point.setCycleCount(cycles[i]);
            point.setSoh(BigDecimal.valueOf(sohs[i]));
            point.setCapacityRetention(BigDecimal.valueOf(sohs[i]));
            point.setDepthOfDischarge(BigDecimal.valueOf(80));
            point.setTemperature(BigDecimal.valueOf(25));
            point.setChargeRate(BigDecimal.valueOf(1.0));
            point.setDischargeRate(BigDecimal.valueOf(1.0));
            point.setRemarks("三元锂电池标准衰减曲线 (80% DOD, 25°C, 1C)");
            curve.add(point);
        }
        return curve;
    }

    @Override
    public Map<String, String> validateDegradationModel(BatteryDegradationModelDTO dto) {
        Map<String, String> errors = new HashMap<>();

        if (dto.getEndOfLifeSoh() == null) {
            errors.put("endOfLifeSoh", "寿命终止SOH不能为空");
        } else if (dto.getEndOfLifeSoh().compareTo(new BigDecimal("0.5")) < 0
                || dto.getEndOfLifeSoh().compareTo(new BigDecimal("0.9")) > 0) {
            errors.put("endOfLifeSoh", "寿命终止SOH应在0.5-0.9之间");
        }

        String modelType = dto.getModelType() != null ? dto.getModelType().toUpperCase() : "";
        switch (modelType) {
            case "LINEAR":
                if (dto.getDegradationRatePerCycle() == null
                        || dto.getDegradationRatePerCycle().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.put("degradationRatePerCycle", "线性衰减模型需要指定每循环衰减率");
                }
                break;
            case "EXPONENTIAL":
                if (dto.getDecayConstant() == null
                        || dto.getDecayConstant().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.put("decayConstant", "指数衰减模型需要指定衰减常数");
                }
                break;
            case "EMPIRICAL":
                if (dto.getDegradationPoints() == null || dto.getDegradationPoints().size() < 2) {
                    errors.put("degradationPoints", "经验模型需要至少2个衰减数据点");
                }
                break;
            default:
                break;
        }

        return errors;
    }

    private BigDecimal calculateLinearDegradation(BatteryDegradationModel model, int cycleCount) {
        BigDecimal rate = model.getDegradationRatePerCycle();
        if (rate == null) {
            rate = new BigDecimal("0.00002");
        }
        BigDecimal totalDegradation = rate.multiply(new BigDecimal(cycleCount));
        return BigDecimal.ONE.subtract(totalDegradation);
    }

    private BigDecimal calculateExponentialDegradation(BatteryDegradationModel model, int cycleCount) {
        BigDecimal decayConstant = model.getDecayConstant();
        if (decayConstant == null) {
            decayConstant = new BigDecimal("0.00001");
        }
        double exponent = -cycleCount * decayConstant.doubleValue();
        return BigDecimal.valueOf(Math.exp(exponent));
    }

    private BigDecimal calculatePiecewiseDegradation(BatteryDegradationModel model, int cycleCount) {
        List<BatteryDegradationPoint> points = model.getDegradationPoints();
        if (points == null || points.size() < 2) {
            return calculateLinearDegradation(model, cycleCount);
        }

        List<BatteryDegradationPoint> sortedPoints = points.stream()
                .sorted(Comparator.comparingInt(BatteryDegradationPoint::getCycleCount))
                .collect(Collectors.toList());

        if (cycleCount <= sortedPoints.get(0).getCycleCount()) {
            return sortedPoints.get(0).getSoh();
        }

        if (cycleCount >= sortedPoints.get(sortedPoints.size() - 1).getCycleCount()) {
            return sortedPoints.get(sortedPoints.size() - 1).getSoh();
        }

        for (int i = 1; i < sortedPoints.size(); i++) {
            BatteryDegradationPoint prev = sortedPoints.get(i - 1);
            BatteryDegradationPoint curr = sortedPoints.get(i);

            if (cycleCount >= prev.getCycleCount() && cycleCount <= curr.getCycleCount()) {
                int cycleDiff = curr.getCycleCount() - prev.getCycleCount();
                int cycleOffset = cycleCount - prev.getCycleCount();
                BigDecimal sohDiff = curr.getSoh().subtract(prev.getSoh());
                return prev.getSoh().add(
                        sohDiff.multiply(new BigDecimal(cycleOffset))
                                .divide(new BigDecimal(cycleDiff), 8, RoundingMode.HALF_UP));
            }
        }

        return calculateLinearDegradation(model, cycleCount);
    }

    private BigDecimal calculateEmpiricalDegradation(BatteryDegradationModel model, int cycleCount) {
        return calculatePiecewiseDegradation(model, cycleCount);
    }

    private void convertToEntity(BatteryDegradationModelDTO dto, BatteryDegradationModel model) {
        BeanUtils.copyProperties(dto, model, "degradationPoints", "id");
    }

    private BatteryDegradationModelDTO convertToDTO(BatteryDegradationModel model, boolean includePoints) {
        BatteryDegradationModelDTO dto = new BatteryDegradationModelDTO();
        BeanUtils.copyProperties(model, dto, "degradationPoints");

        if (includePoints && model.getDegradationPoints() != null) {
            List<BatteryDegradationPointDTO> pointDTOs = model.getDegradationPoints().stream()
                    .map(point -> {
                        BatteryDegradationPointDTO pointDTO = new BatteryDegradationPointDTO();
                        BeanUtils.copyProperties(point, pointDTO, "degradationModel");
                        return pointDTO;
                    })
                    .sorted(Comparator.comparingInt(BatteryDegradationPointDTO::getCycleCount))
                    .collect(Collectors.toList());
            dto.setDegradationPoints(pointDTOs);
        }

        return dto;
    }
}
