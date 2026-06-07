package com.ems.service;

import com.ems.domain.dto.simulation.SimulationBatchRequestDTO;
import com.ems.domain.dto.simulation.SimulationDataPointDTO;
import com.ems.domain.dto.simulation.SimulationRequestDTO;
import com.ems.domain.vo.simulation.SimulationReportVO;
import com.ems.domain.vo.simulation.SimulationResultVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SimulationService {

    SimulationResultVO createSimulation(SimulationRequestDTO request);

    SimulationResultVO getSimulationById(Long id);

    Page<SimulationResultVO> listSimulations(String strategyCode, String status,
                                              LocalDate startDate, LocalDate endDate,
                                              String keyword, Pageable pageable);

    SimulationResultVO runSimulation(Long id);

    SimulationResultVO runSimulationWithStrategy(Long id, String strategyType);

    List<SimulationResultVO> runBatchSimulation(SimulationBatchRequestDTO request);

    SimulationReportVO generateReport(Long id);

    SimulationReportVO generateComparisonReport(List<Long> simulationIds);

    void deleteSimulation(Long id);

    Map<String, Object> importLoadData(List<SimulationDataPointDTO> data);

    Map<String, Object> importPvData(List<SimulationDataPointDTO> data);

    Map<String, Object> importPriceData(List<SimulationDataPointDTO> data);

    Map<String, Object> importHistoricalData(Map<String, List<SimulationDataPointDTO>> data);

    Map<String, List<SimulationDataPointDTO>> generateSampleData(LocalDate date);

    Map<String, Object> validateSimulationData(SimulationRequestDTO request);

    List<String> getAvailableStrategyTypes();

    Map<String, Object> getSimulationStatistics(LocalDate startDate, LocalDate endDate);
}
