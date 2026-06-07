package com.ems.service;

import com.ems.domain.dto.battery.BatteryDegradationModelDTO;
import com.ems.domain.dto.battery.BatteryDegradationPointDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BatteryDegradationService {

    BatteryDegradationModelDTO create(BatteryDegradationModelDTO dto);

    BatteryDegradationModelDTO update(Long id, BatteryDegradationModelDTO dto);

    void delete(Long id);

    BatteryDegradationModelDTO getById(Long id);

    List<BatteryDegradationModelDTO> listAll();

    List<BatteryDegradationModelDTO> listEnabled();

    List<BatteryDegradationModelDTO> listByModelType(String modelType);

    List<BatteryDegradationModelDTO> listByBatteryType(String batteryType);

    BatteryDegradationModelDTO getDefaultModel();

    BatteryDegradationModelDTO getDefaultModelByBatteryType(String batteryType);

    BigDecimal estimateSoh(Long modelId, Integer cycleCount);

    BigDecimal estimateSohWithFactors(Long modelId, Integer cycleCount,
                                      BigDecimal avgTemperature, BigDecimal avgSoc,
                                      BigDecimal avgChargeRate, BigDecimal avgDischargeRate,
                                      BigDecimal avgDepthOfDischarge);

    Integer estimateRemainingCycles(Long modelId, BigDecimal currentSoh, Integer currentCycleCount);

    BigDecimal estimateRemainingLifespan(Long modelId, BigDecimal currentSoh,
                                         Integer currentCycleCount, BigDecimal dailyCycles);

    List<BatteryDegradationPointDTO> generateDegradationCurve(Long modelId,
                                                              Integer startCycle,
                                                              Integer endCycle,
                                                              Integer step);

    BigDecimal calculateCalendarAging(Long modelId, LocalDate startDate, LocalDate endDate,
                                      BigDecimal storageSoc, BigDecimal storageTemperature);

    void updateEnabled(Long id, Boolean enabled);

    void setDefaultModel(Long id);

    BatteryDegradationModelDTO addDegradationPoint(Long modelId, BatteryDegradationPointDTO pointDTO);

    BatteryDegradationModelDTO addDegradationPoints(Long modelId, List<BatteryDegradationPointDTO> pointDTOs);

    void removeDegradationPoint(Long modelId, Long pointId);

    List<BatteryDegradationPointDTO> generateStandardLFPCurve();

    List<BatteryDegradationPointDTO> generateStandardNMCCurve();

    Map<String, String> validateDegradationModel(BatteryDegradationModelDTO dto);
}
