package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.simulation.SimulationBatchRequestDTO;
import com.ems.domain.dto.simulation.SimulationDataPointDTO;
import com.ems.domain.dto.simulation.SimulationRequestDTO;
import com.ems.domain.entity.Simulation;
import com.ems.domain.entity.SimulationHourData;
import com.ems.domain.vo.simulation.SimulationHourDataVO;
import com.ems.domain.vo.simulation.SimulationReportVO;
import com.ems.domain.vo.simulation.SimulationResultVO;
import com.ems.repository.SimulationHourDataRepository;
import com.ems.repository.SimulationRepository;
import com.ems.service.SimulationService;
import com.ems.service.simulation.StrategySimulationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationServiceImpl implements SimulationService {

    private final SimulationRepository simulationRepository;
    private final SimulationHourDataRepository hourDataRepository;
    private final StrategySimulationEngine simulationEngine;

    private static final List<String> AVAILABLE_STRATEGY_TYPES = Arrays.asList(
            "PURE_ARBITRAGE", "PEAK_VALLEY", "DEMAND_FIRST"
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationResultVO createSimulation(SimulationRequestDTO request) {
        log.info("创建仿真任务: {}", request.getSimulationName());

        Map<String, Object> validation = validateSimulationData(request);
        if (!(Boolean) validation.get("valid")) {
            throw new EmsException("仿真数据校验失败: " + validation.get("errors"));
        }

        Simulation simulation = new Simulation();
        BeanUtils.copyProperties(request, simulation);
        simulation.setStatus("PENDING");

        simulation = simulationRepository.save(simulation);
        log.info("仿真任务创建成功, ID: {}", simulation.getId());

        return convertToVO(simulation, false);
    }

    @Override
    public SimulationResultVO getSimulationById(Long id) {
        Simulation simulation = simulationRepository.findById(id)
                .orElseThrow(() -> new EmsException("仿真任务不存在, ID: " + id));
        return convertToVO(simulation, true);
    }

    @Override
    public Page<SimulationResultVO> listSimulations(String strategyCode, String status,
                                                    LocalDate startDate, LocalDate endDate,
                                                    String keyword, Pageable pageable) {
        Page<Simulation> page;

        if (keyword != null && !keyword.isEmpty()) {
            page = simulationRepository.searchByKeyword(keyword, pageable);
        } else if (strategyCode != null && !strategyCode.isEmpty()) {
            page = simulationRepository.findByStrategyCode(strategyCode, pageable);
        } else if (status != null && !status.isEmpty()) {
            page = simulationRepository.findByStatus(status, pageable);
        } else if (startDate != null && endDate != null) {
            page = simulationRepository.findBySimulationDateBetween(startDate, endDate, pageable);
        } else {
            page = simulationRepository.findAll(pageable);
        }

        return page.map(s -> convertToVO(s, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationResultVO runSimulation(Long id) {
        log.info("执行仿真任务, ID: {}", id);

        Simulation simulation = simulationRepository.findById(id)
                .orElseThrow(() -> new EmsException("仿真任务不存在, ID: " + id));

        String strategyType = simulation.getStrategyType();
        if (strategyType == null || strategyType.isEmpty()) {
            strategyType = "PEAK_VALLEY";
        }

        return runSimulationWithStrategy(id, strategyType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationResultVO runSimulationWithStrategy(Long id, String strategyType) {
        log.info("执行仿真任务, ID: {}, 策略类型: {}", id, strategyType);

        Simulation existing = simulationRepository.findById(id)
                .orElseThrow(() -> new EmsException("仿真任务不存在, ID: " + id));

        SimulationRequestDTO request = convertToRequestDTO(existing);
        Simulation simulation = simulationEngine.executeSimulation(request, strategyType);
        simulation.setId(id);

        hourDataRepository.deleteBySimulationId(id);

        for (SimulationHourData hourData : simulation.getHourData()) {
            hourData.setSimulation(simulation);
        }

        simulation = simulationRepository.save(simulation);
        log.info("仿真任务执行完成, ID: {}, 状态: {}", id, simulation.getStatus());

        return convertToVO(simulation, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SimulationResultVO> runBatchSimulation(SimulationBatchRequestDTO request) {
        log.info("执行批量仿真, 策略数量: {}", request.getStrategyCodes().size());

        List<SimulationResultVO> results = new ArrayList<>();
        SimulationRequestDTO baseConfig = request.getBaseConfig();

        for (String strategyCode : request.getStrategyCodes()) {
            SimulationRequestDTO simRequest = new SimulationRequestDTO();
            if (baseConfig != null) {
                BeanUtils.copyProperties(baseConfig, simRequest);
            }
            simRequest.setSimulationName(request.getSimulationName() + " - " + strategyCode);
            simRequest.setStrategyCode(strategyCode);
            simRequest.setStrategyType(strategyCode);

            Simulation simulation = simulationEngine.executeSimulation(simRequest, strategyCode);
            simulation = simulationRepository.save(simulation);

            SimulationResultVO vo = convertToVO(simulation, true);
            if (request.getCompareMode()) {
                SimulationReportVO report = simulationEngine.generateReport(simulation, simulation.getHourData());
                vo.setReport(report);
            }
            results.add(vo);
        }

        if (request.getCompareMode() && results.size() > 1) {
            SimulationReportVO.StrategyComparisonVO comparison =
                    simulationEngine.generateStrategyComparison(results);
            for (SimulationResultVO vo : results) {
                if (vo.getReport() != null) {
                    vo.getReport().setStrategyComparison(comparison);
                }
            }
        }

        log.info("批量仿真完成, 成功执行: {} 个策略", results.size());
        return results;
    }

    @Override
    public SimulationReportVO generateReport(Long id) {
        Simulation simulation = simulationRepository.findById(id)
                .orElseThrow(() -> new EmsException("仿真任务不存在, ID: " + id));

        if (!"COMPLETED".equals(simulation.getStatus())) {
            throw new EmsException("仿真任务尚未完成，无法生成报告");
        }

        List<SimulationHourData> hourData = hourDataRepository.findBySimulationIdOrderByHourIndexAsc(id);
        return simulationEngine.generateReport(simulation, hourData);
    }

    @Override
    public SimulationReportVO generateComparisonReport(List<Long> simulationIds) {
        List<SimulationResultVO> results = simulationIds.stream()
                .map(this::getSimulationById)
                .collect(Collectors.toList());

        SimulationReportVO report = new SimulationReportVO();
        SimulationReportVO.StrategyComparisonVO comparison =
                simulationEngine.generateStrategyComparison(results);
        report.setStrategyComparison(comparison);
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSimulation(Long id) {
        log.info("删除仿真任务, ID: {}", id);

        if (!simulationRepository.existsById(id)) {
            throw new EmsException("仿真任务不存在, ID: " + id);
        }

        hourDataRepository.deleteBySimulationId(id);
        simulationRepository.deleteById(id);
        log.info("仿真任务删除成功, ID: {}", id);
    }

    @Override
    public Map<String, Object> importLoadData(List<SimulationDataPointDTO> data) {
        log.info("导入负荷数据, 数据点数: {}", data.size());
        return validateAndProcessData(data, "LOAD");
    }

    @Override
    public Map<String, Object> importPvData(List<SimulationDataPointDTO> data) {
        log.info("导入光伏数据, 数据点数: {}", data.size());
        return validateAndProcessData(data, "PV");
    }

    @Override
    public Map<String, Object> importPriceData(List<SimulationDataPointDTO> data) {
        log.info("导入电价数据, 数据点数: {}", data.size());
        return validateAndProcessData(data, "PRICE");
    }

    @Override
    public Map<String, Object> importHistoricalData(Map<String, List<SimulationDataPointDTO>> data) {
        log.info("导入历史数据, 类型数量: {}", data.size());

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> errors = new HashMap<>();
        boolean allValid = true;

        for (Map.Entry<String, List<SimulationDataPointDTO>> entry : data.entrySet()) {
            try {
                Map<String, Object> typeResult = validateAndProcessData(entry.getValue(), entry.getKey());
                result.put(entry.getKey(), typeResult);
                if (!(Boolean) typeResult.get("valid")) {
                    allValid = false;
                    errors.put(entry.getKey(), typeResult.get("errors"));
                }
            } catch (Exception e) {
                allValid = false;
                errors.put(entry.getKey(), e.getMessage());
            }
        }

        result.put("valid", allValid);
        result.put("errors", errors);
        return result;
    }

    @Override
    public Map<String, List<SimulationDataPointDTO>> generateSampleData(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }

        Map<String, List<SimulationDataPointDTO>> data = new HashMap<>();

        double[] loadProfile = {
                50, 45, 40, 38, 40, 50, 80, 120, 150, 160, 155, 150,
                140, 145, 150, 155, 160, 170, 180, 175, 150, 120, 90, 65
        };
        double[] pvProfile = {
                0, 0, 0, 0, 0, 5, 30, 80, 130, 170, 190, 200,
                195, 180, 150, 110, 60, 20, 0, 0, 0, 0, 0, 0
        };
        double[] priceProfile = {
                0.35, 0.35, 0.35, 0.35, 0.35, 0.40, 0.55, 0.70, 0.85, 1.00, 1.00, 1.00,
                0.85, 0.85, 0.85, 0.85, 1.00, 1.20, 1.50, 1.50, 1.20, 0.85, 0.55, 0.40
        };
        String[] periodTypes = {
                "VALLEY", "VALLEY", "VALLEY", "VALLEY", "VALLEY", "VALLEY",
                "FLAT", "FLAT", "PEAK", "PEAK", "PEAK", "PEAK",
                "FLAT", "FLAT", "FLAT", "FLAT", "PEAK", "CRITICAL_PEAK",
                "CRITICAL_PEAK", "CRITICAL_PEAK", "PEAK", "FLAT", "FLAT", "VALLEY"
        };

        List<SimulationDataPointDTO> loadData = new ArrayList<>();
        List<SimulationDataPointDTO> pvData = new ArrayList<>();
        List<SimulationDataPointDTO> priceData = new ArrayList<>();

        for (int i = 0; i < 24; i++) {
            LocalTime startTime = LocalTime.of(i, 0);
            LocalTime endTime = LocalTime.of((i + 1) % 24, 0);

            SimulationDataPointDTO loadPoint = new SimulationDataPointDTO();
            loadPoint.setDate(date);
            loadPoint.setStartTime(startTime);
            loadPoint.setEndTime(endTime);
            loadPoint.setHourIndex(i);
            loadPoint.setValue(BigDecimal.valueOf(loadProfile[i]));
            loadData.add(loadPoint);

            SimulationDataPointDTO pvPoint = new SimulationDataPointDTO();
            pvPoint.setDate(date);
            pvPoint.setStartTime(startTime);
            pvPoint.setEndTime(endTime);
            pvPoint.setHourIndex(i);
            pvPoint.setValue(BigDecimal.valueOf(pvProfile[i]));
            pvData.add(pvPoint);

            SimulationDataPointDTO pricePoint = new SimulationDataPointDTO();
            pricePoint.setDate(date);
            pricePoint.setStartTime(startTime);
            pricePoint.setEndTime(endTime);
            pricePoint.setHourIndex(i);
            pricePoint.setValue(BigDecimal.valueOf(priceProfile[i]));
            pricePoint.setPeriodType(periodTypes[i]);
            priceData.add(pricePoint);
        }

        data.put("load", loadData);
        data.put("pv", pvData);
        data.put("price", priceData);

        return data;
    }

    @Override
    public Map<String, Object> validateSimulationData(SimulationRequestDTO request) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        boolean valid = true;

        if (request.getSimulationName() == null || request.getSimulationName().trim().isEmpty()) {
            errors.add("仿真名称不能为空");
            valid = false;
        }

        if (request.getStrategyCode() == null || request.getStrategyCode().trim().isEmpty()) {
            errors.add("策略编码不能为空");
            valid = false;
        }

        if (request.getBatteryCapacity() == null || request.getBatteryCapacity().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("电池容量必须大于0");
            valid = false;
        }

        if (request.getBatteryPower() == null || request.getBatteryPower().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("电池功率必须大于0");
            valid = false;
        }

        if (request.getMinSoc() != null && request.getMaxSoc() != null
                && request.getMinSoc().compareTo(request.getMaxSoc()) >= 0) {
            errors.add("最小SOC必须小于最大SOC");
            valid = false;
        }

        if (request.getInitialSoc() != null) {
            BigDecimal minSoc = request.getMinSoc() != null ? request.getMinSoc() : BigDecimal.ZERO;
            BigDecimal maxSoc = request.getMaxSoc() != null ? request.getMaxSoc() : BigDecimal.valueOf(100);
            if (request.getInitialSoc().compareTo(minSoc) < 0
                    || request.getInitialSoc().compareTo(maxSoc) > 0) {
                errors.add("初始SOC必须在最小SOC和最大SOC之间");
                valid = false;
            }
        }

        if (request.getLoadData() != null && !request.getLoadData().isEmpty()) {
            Map<String, Object> loadValidation = validateAndProcessData(request.getLoadData(), "LOAD");
            if (!(Boolean) loadValidation.get("valid")) {
                errors.add("负荷数据校验失败: " + loadValidation.get("errors"));
                valid = false;
            }
        }

        if (request.getPvData() != null && !request.getPvData().isEmpty()) {
            Map<String, Object> pvValidation = validateAndProcessData(request.getPvData(), "PV");
            if (!(Boolean) pvValidation.get("valid")) {
                errors.add("光伏数据校验失败: " + pvValidation.get("errors"));
                valid = false;
            }
        }

        if (request.getPriceData() != null && !request.getPriceData().isEmpty()) {
            Map<String, Object> priceValidation = validateAndProcessData(request.getPriceData(), "PRICE");
            if (!(Boolean) priceValidation.get("valid")) {
                errors.add("电价数据校验失败: " + priceValidation.get("errors"));
                valid = false;
            }
        }

        result.put("valid", valid);
        result.put("errors", errors);
        result.put("dataPoints", Map.of(
                "load", request.getLoadData() != null ? request.getLoadData().size() : 0,
                "pv", request.getPvData() != null ? request.getPvData().size() : 0,
                "price", request.getPriceData() != null ? request.getPriceData().size() : 0
        ));

        return result;
    }

    @Override
    public List<String> getAvailableStrategyTypes() {
        return AVAILABLE_STRATEGY_TYPES;
    }

    @Override
    public Map<String, Object> getSimulationStatistics(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> statistics = new HashMap<>();

        List<String> strategyCodes = simulationRepository.findDistinctStrategyCodes();
        statistics.put("strategyCount", strategyCodes.size());

        long totalSimulations = simulationRepository.count();
        statistics.put("totalSimulations", totalSimulations);

        Map<String, Long> statusCounts = new HashMap<>();
        statusCounts.put("PENDING", simulationRepository.findByStatus("PENDING", null).getTotalElements());
        statusCounts.put("RUNNING", simulationRepository.findByStatus("RUNNING", null).getTotalElements());
        statusCounts.put("COMPLETED", simulationRepository.findByStatus("COMPLETED", null).getTotalElements());
        statusCounts.put("FAILED", simulationRepository.findByStatus("FAILED", null).getTotalElements());
        statistics.put("statusCounts", statusCounts);

        return statistics;
    }

    private Map<String, Object> validateAndProcessData(List<SimulationDataPointDTO> data, String dataType) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        boolean valid = true;

        if (data == null || data.isEmpty()) {
            errors.add("数据不能为空");
            valid = false;
            result.put("valid", valid);
            result.put("errors", errors);
            return result;
        }

        Set<Integer> hourIndices = new HashSet<>();
        BigDecimal minValue = BigDecimal.ZERO;
        BigDecimal maxValue = switch (dataType.toUpperCase()) {
            case "LOAD", "PV" -> BigDecimal.valueOf(10000);
            case "PRICE" -> BigDecimal.valueOf(10);
            default -> BigDecimal.valueOf(1000000);
        };

        for (int i = 0; i < data.size(); i++) {
            SimulationDataPointDTO point = data.get(i);

            if (point.getDate() == null) {
                errors.add("第" + (i + 1) + "条数据: 日期不能为空");
                valid = false;
                continue;
            }

            if (point.getStartTime() == null) {
                errors.add("第" + (i + 1) + "条数据: 开始时间不能为空");
                valid = false;
                continue;
            }

            if (point.getValue() == null) {
                errors.add("第" + (i + 1) + "条数据: 数值不能为空");
                valid = false;
                continue;
            }

            if (point.getValue().compareTo(minValue) < 0) {
                errors.add("第" + (i + 1) + "条数据: 数值不能小于" + minValue);
                valid = false;
            }

            if (point.getValue().compareTo(maxValue) > 0) {
                errors.add("第" + (i + 1) + "条数据: 数值不能大于" + maxValue);
                valid = false;
            }

            int hourIndex = point.getHourIndex() != null ? point.getHourIndex() : point.getStartTime().getHour();
            if (hourIndices.contains(hourIndex)) {
                errors.add("第" + (i + 1) + "条数据: 时段" + hourIndex + "重复");
                valid = false;
            }
            hourIndices.add(hourIndex);
            point.setHourIndex(hourIndex);

            if (point.getEndTime() == null) {
                point.setEndTime(LocalTime.of((hourIndex + 1) % 24, 0));
            }
        }

        result.put("valid", valid);
        result.put("errors", errors);
        result.put("count", data.size());
        result.put("uniqueHours", hourIndices.size());
        result.put("data", data);

        return result;
    }

    private SimulationRequestDTO convertToRequestDTO(Simulation simulation) {
        SimulationRequestDTO request = new SimulationRequestDTO();
        BeanUtils.copyProperties(simulation, request);

        List<SimulationHourData> hourDataList = hourDataRepository.findBySimulationIdOrderByHourIndexAsc(simulation.getId());

        List<SimulationDataPointDTO> loadData = new ArrayList<>();
        List<SimulationDataPointDTO> pvData = new ArrayList<>();
        List<SimulationDataPointDTO> priceData = new ArrayList<>();

        for (SimulationHourData hourData : hourDataList) {
            SimulationDataPointDTO loadPoint = new SimulationDataPointDTO();
            loadPoint.setDate(simulation.getSimulationDate());
            loadPoint.setStartTime(hourData.getStartTime());
            loadPoint.setEndTime(hourData.getEndTime());
            loadPoint.setHourIndex(hourData.getHourIndex());
            loadPoint.setValue(hourData.getLoadPower());
            loadData.add(loadPoint);

            if (hourData.getPvPower() != null) {
                SimulationDataPointDTO pvPoint = new SimulationDataPointDTO();
                pvPoint.setDate(simulation.getSimulationDate());
                pvPoint.setStartTime(hourData.getStartTime());
                pvPoint.setEndTime(hourData.getEndTime());
                pvPoint.setHourIndex(hourData.getHourIndex());
                pvPoint.setValue(hourData.getPvPower());
                pvData.add(pvPoint);
            }

            SimulationDataPointDTO pricePoint = new SimulationDataPointDTO();
            pricePoint.setDate(simulation.getSimulationDate());
            pricePoint.setStartTime(hourData.getStartTime());
            pricePoint.setEndTime(hourData.getEndTime());
            pricePoint.setHourIndex(hourData.getHourIndex());
            pricePoint.setValue(hourData.getPrice());
            pricePoint.setPeriodType(hourData.getPeriodType());
            priceData.add(pricePoint);
        }

        request.setLoadData(loadData);
        request.setPvData(pvData.isEmpty() ? null : pvData);
        request.setPriceData(priceData);

        return request;
    }

    private SimulationResultVO convertToVO(Simulation simulation, boolean includeHourData) {
        SimulationResultVO vo = new SimulationResultVO();
        BeanUtils.copyProperties(simulation, vo);
        vo.setCreatedAt(simulation.getCreatedAt());

        if (includeHourData && simulation.getHourData() != null) {
            List<SimulationHourDataVO> hourDataVOList = simulation.getHourData().stream()
                    .sorted(Comparator.comparingInt(SimulationHourData::getHourIndex))
                    .map(this::convertHourDataToVO)
                    .collect(Collectors.toList());
            vo.setHourData(hourDataVOList);
        } else if (includeHourData) {
            List<SimulationHourData> hourDataList = hourDataRepository
                    .findBySimulationIdOrderByHourIndexAsc(simulation.getId());
            List<SimulationHourDataVO> hourDataVOList = hourDataList.stream()
                    .map(this::convertHourDataToVO)
                    .collect(Collectors.toList());
            vo.setHourData(hourDataVOList);
        }

        return vo;
    }

    private SimulationHourDataVO convertHourDataToVO(SimulationHourData hourData) {
        SimulationHourDataVO vo = new SimulationHourDataVO();
        BeanUtils.copyProperties(hourData, vo);
        return vo;
    }
}
