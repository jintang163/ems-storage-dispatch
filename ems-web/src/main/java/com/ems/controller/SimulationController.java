package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.simulation.SimulationBatchRequestDTO;
import com.ems.domain.dto.simulation.SimulationDataPointDTO;
import com.ems.domain.dto.simulation.SimulationRequestDTO;
import com.ems.domain.vo.simulation.SimulationReportVO;
import com.ems.domain.vo.simulation.SimulationResultVO;
import com.ems.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@CrossOrigin
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping
    public Result<SimulationResultVO> createSimulation(@Valid @RequestBody SimulationRequestDTO request) {
        return Result.success(simulationService.createSimulation(request));
    }

    @GetMapping("/{id}")
    public Result<SimulationResultVO> getSimulationById(@PathVariable Long id) {
        return Result.success(simulationService.getSimulationById(id));
    }

    @GetMapping("/list")
    public Result<Page<SimulationResultVO>> listSimulations(
            @RequestParam(required = false) String strategyCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return Result.success(simulationService.listSimulations(
                strategyCode, status, startDate, endDate, keyword, pageable));
    }

    @PostMapping("/{id}/run")
    public Result<SimulationResultVO> runSimulation(@PathVariable Long id) {
        return Result.success(simulationService.runSimulation(id));
    }

    @PostMapping("/{id}/run/{strategyType}")
    public Result<SimulationResultVO> runSimulationWithStrategy(
            @PathVariable Long id,
            @PathVariable String strategyType) {
        return Result.success(simulationService.runSimulationWithStrategy(id, strategyType));
    }

    @PostMapping("/batch")
    public Result<List<SimulationResultVO>> runBatchSimulation(
            @Valid @RequestBody SimulationBatchRequestDTO request) {
        return Result.success(simulationService.runBatchSimulation(request));
    }

    @GetMapping("/{id}/report")
    public Result<SimulationReportVO> generateReport(@PathVariable Long id) {
        return Result.success(simulationService.generateReport(id));
    }

    @PostMapping("/comparison-report")
    public Result<SimulationReportVO> generateComparisonReport(
            @RequestBody List<Long> simulationIds) {
        return Result.success(simulationService.generateComparisonReport(simulationIds));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSimulation(@PathVariable Long id) {
        simulationService.deleteSimulation(id);
        return Result.success();
    }

    @PostMapping("/import/load")
    public Result<Map<String, Object>> importLoadData(
            @Valid @RequestBody List<SimulationDataPointDTO> data) {
        return Result.success(simulationService.importLoadData(data));
    }

    @PostMapping("/import/pv")
    public Result<Map<String, Object>> importPvData(
            @Valid @RequestBody List<SimulationDataPointDTO> data) {
        return Result.success(simulationService.importPvData(data));
    }

    @PostMapping("/import/price")
    public Result<Map<String, Object>> importPriceData(
            @Valid @RequestBody List<SimulationDataPointDTO> data) {
        return Result.success(simulationService.importPriceData(data));
    }

    @PostMapping("/import/batch")
    public Result<Map<String, Object>> importHistoricalData(
            @RequestBody Map<String, List<SimulationDataPointDTO>> data) {
        return Result.success(simulationService.importHistoricalData(data));
    }

    @GetMapping("/sample-data")
    public Result<Map<String, List<SimulationDataPointDTO>>> generateSampleData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(simulationService.generateSampleData(date));
    }

    @PostMapping("/validate")
    public Result<Map<String, Object>> validateSimulationData(
            @Valid @RequestBody SimulationRequestDTO request) {
        return Result.success(simulationService.validateSimulationData(request));
    }

    @GetMapping("/strategy-types")
    public Result<List<String>> getAvailableStrategyTypes() {
        return Result.success(simulationService.getAvailableStrategyTypes());
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getSimulationStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(simulationService.getSimulationStatistics(startDate, endDate));
    }

    @PostMapping("/quick-run")
    public Result<SimulationResultVO> quickRunSimulation(@Valid @RequestBody SimulationRequestDTO request) {
        SimulationResultVO created = simulationService.createSimulation(request);
        return Result.success(simulationService.runSimulation(created.getId()));
    }

    @PostMapping("/compare")
    public Result<List<SimulationResultVO>> compareStrategies(
            @Valid @RequestBody SimulationRequestDTO request,
            @RequestParam(defaultValue = "PURE_ARBITRAGE,PEAK_VALLEY,DEMAND_FIRST") List<String> strategyCodes) {
        SimulationBatchRequestDTO batchRequest = new SimulationBatchRequestDTO();
        batchRequest.setSimulationName(request.getSimulationName() + " - 多策略对比");
        batchRequest.setStrategyCodes(strategyCodes);
        batchRequest.setBaseConfig(request);
        batchRequest.setCompareMode(true);
        return Result.success(simulationService.runBatchSimulation(batchRequest));
    }
}
