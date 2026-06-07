package com.ems.service.simulation;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.simulation.SimulationRequestDTO;
import com.ems.domain.entity.Simulation;
import com.ems.domain.entity.SimulationHourData;
import com.ems.domain.vo.simulation.SimulationReportVO;
import com.ems.domain.vo.simulation.SimulationResultVO;
import com.ems.service.BatteryDegradationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrategySimulationEngine {

    private final BatteryDegradationService batteryDegradationService;

    private static final int HOURS_PER_DAY = 24;
    private static final BigDecimal HOURS_PER_CYCLE = BigDecimal.valueOf(1.0);
    private static final BigDecimal DEFAULT_DEMAND_PRICE = new BigDecimal("30.00");
    private static final BigDecimal DEFAULT_BATTERY_COST_PER_KWH = new BigDecimal("1500.00");

    public Simulation executeSimulation(SimulationRequestDTO request, String strategyType) {
        log.info("开始执行策略仿真 - 策略类型: {}, 仿真名称: {}", strategyType, request.getSimulationName());

        Simulation simulation = initializeSimulation(request, strategyType);
        simulation.setStatus("RUNNING");
        simulation.setStartedAt(LocalDateTime.now());

        try {
            List<SimulationHourData> hourDataList = switch (strategyType.toUpperCase()) {
                case "ARBITRAGE", "PURE_ARBITRAGE" -> executeArbitrageStrategy(request, simulation);
                case "PEAK_VALLEY", "PEAK_SHAVING" -> executePeakValleyStrategy(request, simulation);
                case "DEMAND", "DEMAND_FIRST" -> executeDemandFirstStrategy(request, simulation);
                default -> executePeakValleyStrategy(request, simulation);
            };

            calculateSummaryStatistics(simulation, hourDataList);
            calculateBatteryDegradation(simulation, hourDataList);
            calculateEconomicMetrics(simulation, hourDataList);

            simulation.setStatus("COMPLETED");
            simulation.setCompletedAt(LocalDateTime.now());
            log.info("策略仿真完成 - 策略类型: {}, 总收益: {}, 净收益: {}",
                    strategyType, simulation.getTotalRevenue(), simulation.getNetRevenue());

        } catch (Exception e) {
            log.error("策略仿真执行失败: {}", e.getMessage(), e);
            simulation.setStatus("FAILED");
            simulation.setErrorMessage(e.getMessage());
            simulation.setCompletedAt(LocalDateTime.now());
            throw new EmsException("策略仿真执行失败: " + e.getMessage());
        }

        return simulation;
    }

    private Simulation initializeSimulation(SimulationRequestDTO request, String strategyType) {
        Simulation simulation = new Simulation();
        simulation.setSimulationName(request.getSimulationName());
        simulation.setSimulationDate(request.getSimulationDate() != null ? request.getSimulationDate() : LocalDate.now());
        simulation.setStrategyCode(request.getStrategyCode());
        simulation.setStrategyName(request.getStrategyName());
        simulation.setStrategyType(strategyType);
        simulation.setBatterySn(request.getBatterySn());
        simulation.setTransformerCode(request.getTransformerCode());
        simulation.setInitialSoc(request.getInitialSoc());
        simulation.setBatteryCapacity(request.getBatteryCapacity());
        simulation.setBatteryPower(request.getBatteryPower());
        simulation.setChargeEfficiency(request.getChargeEfficiency());
        simulation.setDischargeEfficiency(request.getDischargeEfficiency());
        simulation.setMinSoc(request.getMinSoc());
        simulation.setMaxSoc(request.getMaxSoc());
        simulation.setDemandThreshold(request.getDemandThreshold());
        simulation.setDemandPrice(request.getDemandPrice() != null ? request.getDemandPrice() : DEFAULT_DEMAND_PRICE);
        simulation.setDegradationModelId(request.getDegradationModelId());
        simulation.setDataSource(request.getDataSource());
        simulation.setDataStartDate(request.getDataStartDate());
        simulation.setDataEndDate(request.getDataEndDate());
        simulation.setRemark(request.getRemark());
        return simulation;
    }

    private List<SimulationHourData> executeArbitrageStrategy(SimulationRequestDTO request, Simulation simulation) {
        log.info("执行纯套利策略仿真");

        List<SimulationHourData> hourDataList = initializeHourData(request, simulation);
        BigDecimal[] prices = hourDataList.stream()
                .map(SimulationHourData::getPrice)
                .toArray(BigDecimal[]::new);

        List<Integer> buyHours = new ArrayList<>();
        List<Integer> sellHours = new ArrayList<>();
        identifyArbitrageOpportunities(prices, buyHours, sellHours);

        BigDecimal currentSoc = request.getInitialSoc();
        BigDecimal maxCapacity = request.getBatteryCapacity();
        BigDecimal maxPower = request.getBatteryPower();
        BigDecimal chargeEfficiency = request.getChargeEfficiency();
        BigDecimal dischargeEfficiency = request.getDischargeEfficiency();
        BigDecimal minSoc = request.getMinSoc();
        BigDecimal maxSoc = request.getMaxSoc();

        for (int i = 0; i < HOURS_PER_DAY; i++) {
            SimulationHourData hourData = hourDataList.get(i);
            BigDecimal load = hourData.getLoadPower();
            BigDecimal pv = hourData.getPvPower() != null ? hourData.getPvPower() : BigDecimal.ZERO;
            BigDecimal netLoad = load.subtract(pv);

            BigDecimal batteryPower = BigDecimal.ZERO;
            String actionType = "IDLE";

            if (buyHours.contains(i)) {
                BigDecimal maxChargeEnergy = maxCapacity.multiply(maxSoc.subtract(currentSoc))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal chargeEnergy = maxPower.min(maxChargeEnergy);
                if (chargeEnergy.compareTo(BigDecimal.ZERO) > 0) {
                    batteryPower = chargeEnergy.negate();
                    BigDecimal actualCharge = chargeEnergy.multiply(chargeEfficiency);
                    currentSoc = currentSoc.add(actualCharge.divide(maxCapacity, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                    actionType = "CHARGE";
                }
            } else if (sellHours.contains(i)) {
                BigDecimal maxDischargeEnergy = maxCapacity.multiply(currentSoc.subtract(minSoc))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal dischargeEnergy = maxPower.min(maxDischargeEnergy);
                if (dischargeEnergy.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal actualDischarge = dischargeEnergy.multiply(dischargeEfficiency);
                    batteryPower = actualDischarge;
                    currentSoc = currentSoc.subtract(dischargeEnergy.divide(maxCapacity, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                    actionType = "DISCHARGE";
                }
            }

            BigDecimal gridPower = netLoad.subtract(batteryPower);
            BigDecimal price = hourData.getPrice();
            BigDecimal revenue = calculateHourlyRevenue(price, batteryPower, gridPower, netLoad,
                    chargeEfficiency, dischargeEfficiency);

            hourData.setBatteryPower(batteryPower);
            hourData.setBatteryEnergy(batteryPower.abs());
            hourData.setExpectedSoc(currentSoc.setScale(2, RoundingMode.HALF_UP));
            hourData.setActionType(actionType);
            hourData.setGridPower(gridPower);
            hourData.setDemand(gridPower.max(BigDecimal.ZERO));
            hourData.setRevenue(revenue);
            hourData.setArbitrageRevenue(calculateArbitrageRevenue(price, batteryPower, chargeEfficiency, dischargeEfficiency));
            hourData.setDemandSaving(BigDecimal.ZERO);
            hourData.setNetProfit(revenue);

            if (i > 0) {
                BigDecimal prevCumulative = hourDataList.get(i - 1).getCumulativeRevenue();
                hourData.setCumulativeRevenue(prevCumulative.add(revenue));
            } else {
                hourData.setCumulativeRevenue(revenue);
            }

            if (currentSoc.compareTo(minSoc) < 0) currentSoc = minSoc;
            if (currentSoc.compareTo(maxSoc) > 0) currentSoc = maxSoc;
        }

        return hourDataList;
    }

    private List<SimulationHourData> executePeakValleyStrategy(SimulationRequestDTO request, Simulation simulation) {
        log.info("执行削峰填谷策略仿真");

        List<SimulationHourData> hourDataList = initializeHourData(request, simulation);

        BigDecimal currentSoc = request.getInitialSoc();
        BigDecimal maxCapacity = request.getBatteryCapacity();
        BigDecimal maxPower = request.getBatteryPower();
        BigDecimal chargeEfficiency = request.getChargeEfficiency();
        BigDecimal dischargeEfficiency = request.getDischargeEfficiency();
        BigDecimal minSoc = request.getMinSoc();
        BigDecimal maxSoc = request.getMaxSoc();

        Map<Integer, String> periodTypes = identifyPeriodTypes(hourDataList);

        for (int i = 0; i < HOURS_PER_DAY; i++) {
            SimulationHourData hourData = hourDataList.get(i);
            String periodType = periodTypes.get(i);
            hourData.setPeriodType(periodType);

            BigDecimal load = hourData.getLoadPower();
            BigDecimal pv = hourData.getPvPower() != null ? hourData.getPvPower() : BigDecimal.ZERO;
            BigDecimal netLoad = load.subtract(pv);

            BigDecimal batteryPower = BigDecimal.ZERO;
            String actionType = "IDLE";

            if ("VALLEY".equals(periodType) || "FLAT".equals(periodType)) {
                BigDecimal maxChargeEnergy = maxCapacity.multiply(maxSoc.subtract(currentSoc))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal desiredCharge = maxPower.multiply("VALLEY".equals(periodType) ? BigDecimal.ONE : new BigDecimal("0.5"));
                BigDecimal chargeEnergy = desiredCharge.min(maxChargeEnergy);

                if (chargeEnergy.compareTo(BigDecimal.ZERO) > 0) {
                    batteryPower = chargeEnergy.negate();
                    BigDecimal actualCharge = chargeEnergy.multiply(chargeEfficiency);
                    currentSoc = currentSoc.add(actualCharge.divide(maxCapacity, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                    actionType = "CHARGE";
                }
            } else if ("PEAK".equals(periodType) || "CRITICAL_PEAK".equals(periodType)) {
                BigDecimal maxDischargeEnergy = maxCapacity.multiply(currentSoc.subtract(minSoc))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal desiredDischarge = maxPower.multiply("CRITICAL_PEAK".equals(periodType) ? BigDecimal.ONE : new BigDecimal("0.8"));
                BigDecimal dischargeEnergy = desiredDischarge.min(maxDischargeEnergy).min(netLoad);

                if (dischargeEnergy.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal actualDischarge = dischargeEnergy.multiply(dischargeEfficiency);
                    batteryPower = actualDischarge;
                    currentSoc = currentSoc.subtract(dischargeEnergy.divide(maxCapacity, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                    actionType = "DISCHARGE";
                }
            }

            BigDecimal gridPower = netLoad.subtract(batteryPower);
            BigDecimal price = hourData.getPrice();
            BigDecimal revenue = calculateHourlyRevenue(price, batteryPower, gridPower, netLoad,
                    chargeEfficiency, dischargeEfficiency);

            hourData.setBatteryPower(batteryPower);
            hourData.setBatteryEnergy(batteryPower.abs());
            hourData.setExpectedSoc(currentSoc.setScale(2, RoundingMode.HALF_UP));
            hourData.setActionType(actionType);
            hourData.setGridPower(gridPower);
            hourData.setDemand(gridPower.max(BigDecimal.ZERO));
            hourData.setRevenue(revenue);
            hourData.setArbitrageRevenue(calculateArbitrageRevenue(price, batteryPower, chargeEfficiency, dischargeEfficiency));
            hourData.setDemandSaving(BigDecimal.ZERO);
            hourData.setNetProfit(revenue);

            if (i > 0) {
                BigDecimal prevCumulative = hourDataList.get(i - 1).getCumulativeRevenue();
                hourData.setCumulativeRevenue(prevCumulative.add(revenue));
            } else {
                hourData.setCumulativeRevenue(revenue);
            }

            if (currentSoc.compareTo(minSoc) < 0) currentSoc = minSoc;
            if (currentSoc.compareTo(maxSoc) > 0) currentSoc = maxSoc;
        }

        return hourDataList;
    }

    private List<SimulationHourData> executeDemandFirstStrategy(SimulationRequestDTO request, Simulation simulation) {
        log.info("执行需量优先策略仿真");

        List<SimulationHourData> hourDataList = initializeHourData(request, simulation);

        BigDecimal currentSoc = request.getInitialSoc();
        BigDecimal maxCapacity = request.getBatteryCapacity();
        BigDecimal maxPower = request.getBatteryPower();
        BigDecimal chargeEfficiency = request.getChargeEfficiency();
        BigDecimal dischargeEfficiency = request.getDischargeEfficiency();
        BigDecimal minSoc = request.getMinSoc();
        BigDecimal maxSoc = request.getMaxSoc();
        BigDecimal demandThreshold = request.getDemandThreshold();

        if (demandThreshold == null) {
            BigDecimal maxLoad = hourDataList.stream()
                    .map(h -> h.getLoadPower().subtract(h.getPvPower() != null ? h.getPvPower() : BigDecimal.ZERO))
                    .max(BigDecimal::compareTo)
                    .orElse(maxPower);
            demandThreshold = maxLoad.multiply(new BigDecimal("0.85"));
            simulation.setDemandThreshold(demandThreshold);
        }

        BigDecimal baselineDemand = calculateBaselineDemand(hourDataList);

        for (int i = 0; i < HOURS_PER_DAY; i++) {
            SimulationHourData hourData = hourDataList.get(i);
            BigDecimal load = hourData.getLoadPower();
            BigDecimal pv = hourData.getPvPower() != null ? hourData.getPvPower() : BigDecimal.ZERO;
            BigDecimal netLoad = load.subtract(pv);

            BigDecimal batteryPower = BigDecimal.ZERO;
            String actionType = "IDLE";
            boolean demandControlRequired = false;

            BigDecimal projectedDemand = netLoad;

            if (projectedDemand.compareTo(demandThreshold) > 0 && currentSoc.compareTo(minSoc) > 0) {
                BigDecimal demandDeficit = projectedDemand.subtract(demandThreshold);
                BigDecimal maxDischargeEnergy = maxCapacity.multiply(currentSoc.subtract(minSoc))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal dischargeEnergy = demandDeficit.min(maxDischargeEnergy).min(maxPower);

                if (dischargeEnergy.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal actualDischarge = dischargeEnergy.multiply(dischargeEfficiency);
                    batteryPower = actualDischarge;
                    currentSoc = currentSoc.subtract(dischargeEnergy.divide(maxCapacity, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                    actionType = "DISCHARGE";
                    demandControlRequired = true;
                }
            } else if (projectedDemand.compareTo(demandThreshold.multiply(new BigDecimal("0.6"))) < 0
                    && currentSoc.compareTo(maxSoc) < 0) {
                BigDecimal maxChargeEnergy = maxCapacity.multiply(maxSoc.subtract(currentSoc))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal availableCapacity = demandThreshold.subtract(projectedDemand);
                BigDecimal chargeEnergy = availableCapacity.min(maxChargeEnergy).min(maxPower);

                if (chargeEnergy.compareTo(BigDecimal.ZERO) > 0) {
                    batteryPower = chargeEnergy.negate();
                    BigDecimal actualCharge = chargeEnergy.multiply(chargeEfficiency);
                    currentSoc = currentSoc.add(actualCharge.divide(maxCapacity, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                    actionType = "CHARGE";
                }
            }

            BigDecimal gridPower = netLoad.subtract(batteryPower);
            BigDecimal price = hourData.getPrice();
            BigDecimal revenue = calculateHourlyRevenue(price, batteryPower, gridPower, netLoad,
                    chargeEfficiency, dischargeEfficiency);
            BigDecimal demandSaving = calculateDemandSaving(gridPower, baselineDemand, demandThreshold,
                    simulation.getDemandPrice());

            hourData.setBatteryPower(batteryPower);
            hourData.setBatteryEnergy(batteryPower.abs());
            hourData.setExpectedSoc(currentSoc.setScale(2, RoundingMode.HALF_UP));
            hourData.setActionType(actionType);
            hourData.setGridPower(gridPower);
            hourData.setDemand(gridPower.max(BigDecimal.ZERO));
            hourData.setDemandControlRequired(demandControlRequired);
            hourData.setRevenue(revenue.add(demandSaving));
            hourData.setArbitrageRevenue(calculateArbitrageRevenue(price, batteryPower, chargeEfficiency, dischargeEfficiency));
            hourData.setDemandSaving(demandSaving);
            hourData.setNetProfit(revenue.add(demandSaving));

            if (i > 0) {
                BigDecimal prevCumulative = hourDataList.get(i - 1).getCumulativeRevenue();
                hourData.setCumulativeRevenue(prevCumulative.add(revenue).add(demandSaving));
            } else {
                hourData.setCumulativeRevenue(revenue.add(demandSaving));
            }

            if (currentSoc.compareTo(minSoc) < 0) currentSoc = minSoc;
            if (currentSoc.compareTo(maxSoc) > 0) currentSoc = maxSoc;
        }

        return hourDataList;
    }

    private List<SimulationHourData> initializeHourData(SimulationRequestDTO request, Simulation simulation) {
        List<SimulationHourData> hourDataList = new ArrayList<>();

        Map<Integer, BigDecimal> loadMap = request.getLoadData() != null ?
                dataPointListToMap(request.getLoadData()) : generateDefaultLoadProfile();
        Map<Integer, BigDecimal> pvMap = request.getPvData() != null ?
                dataPointListToMap(request.getPvData()) : generateDefaultPvProfile();
        Map<Integer, BigDecimal> priceMap = request.getPriceData() != null ?
                dataPointListToMap(request.getPriceData()) : generateDefaultPriceProfile();
        Map<Integer, String> periodMap = identifyPeriodTypesFromPrice(priceMap);

        for (int i = 0; i < HOURS_PER_DAY; i++) {
            SimulationHourData hourData = new SimulationHourData();
            hourData.setSimulation(simulation);
            hourData.setHourIndex(i);
            hourData.setStartTime(LocalTime.of(i, 0));
            hourData.setEndTime(LocalTime.of((i + 1) % HOURS_PER_DAY, 0));
            hourData.setLoadPower(loadMap.getOrDefault(i, BigDecimal.valueOf(100)));
            hourData.setPvPower(pvMap.getOrDefault(i, BigDecimal.ZERO));
            hourData.setPrice(priceMap.getOrDefault(i, BigDecimal.valueOf(0.5)));
            hourData.setPeriodType(periodMap.getOrDefault(i, "FLAT"));
            hourDataList.add(hourData);
        }

        return hourDataList;
    }

    private Map<Integer, BigDecimal> dataPointListToMap(List<com.ems.domain.dto.simulation.SimulationDataPointDTO> dataPoints) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (com.ems.domain.dto.simulation.SimulationDataPointDTO point : dataPoints) {
            int hourIndex = point.getHourIndex() != null ? point.getHourIndex() : point.getStartTime().getHour();
            map.put(hourIndex, point.getValue());
        }
        return map;
    }

    private Map<Integer, BigDecimal> generateDefaultLoadProfile() {
        Map<Integer, BigDecimal> map = new HashMap<>();
        double[] loadProfile = {
                50, 45, 40, 38, 40, 50, 80, 120, 150, 160, 155, 150,
                140, 145, 150, 155, 160, 170, 180, 175, 150, 120, 90, 65
        };
        for (int i = 0; i < HOURS_PER_DAY; i++) {
            map.put(i, BigDecimal.valueOf(loadProfile[i]));
        }
        return map;
    }

    private Map<Integer, BigDecimal> generateDefaultPvProfile() {
        Map<Integer, BigDecimal> map = new HashMap<>();
        double[] pvProfile = {
                0, 0, 0, 0, 0, 5, 30, 80, 130, 170, 190, 200,
                195, 180, 150, 110, 60, 20, 0, 0, 0, 0, 0, 0
        };
        for (int i = 0; i < HOURS_PER_DAY; i++) {
            map.put(i, BigDecimal.valueOf(pvProfile[i]));
        }
        return map;
    }

    private Map<Integer, BigDecimal> generateDefaultPriceProfile() {
        Map<Integer, BigDecimal> map = new HashMap<>();
        double[] priceProfile = {
                0.35, 0.35, 0.35, 0.35, 0.35, 0.40, 0.55, 0.70, 0.85, 1.00, 1.00, 1.00,
                0.85, 0.85, 0.85, 0.85, 1.00, 1.20, 1.50, 1.50, 1.20, 0.85, 0.55, 0.40
        };
        for (int i = 0; i < HOURS_PER_DAY; i++) {
            map.put(i, BigDecimal.valueOf(priceProfile[i]));
        }
        return map;
    }

    private void identifyArbitrageOpportunities(BigDecimal[] prices, List<Integer> buyHours, List<Integer> sellHours) {
        List<Map.Entry<Integer, BigDecimal>> priceWithIndex = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            priceWithIndex.add(new AbstractMap.SimpleEntry<>(i, prices[i]));
        }
        priceWithIndex.sort(Map.Entry.comparingByValue());

        int count = Math.min(6, prices.length / 4);
        for (int i = 0; i < count; i++) {
            buyHours.add(priceWithIndex.get(i).getKey());
        }
        for (int i = 0; i < count; i++) {
            sellHours.add(priceWithIndex.get(priceWithIndex.size() - 1 - i).getKey());
        }
        Collections.sort(buyHours);
        Collections.sort(sellHours);
    }

    private Map<Integer, String> identifyPeriodTypes(List<SimulationHourData> hourData) {
        Map<Integer, BigDecimal> priceMap = new HashMap<>();
        for (SimulationHourData data : hourData) {
            priceMap.put(data.getHourIndex(), data.getPrice());
        }
        return identifyPeriodTypesFromPrice(priceMap);
    }

    private Map<Integer, String> identifyPeriodTypesFromPrice(Map<Integer, BigDecimal> priceMap) {
        Map<Integer, String> periodMap = new HashMap<>();
        List<BigDecimal> prices = new ArrayList<>(priceMap.values());
        Collections.sort(prices);

        BigDecimal minPrice = prices.get(0);
        BigDecimal maxPrice = prices.get(prices.size() - 1);
        BigDecimal range = maxPrice.subtract(minPrice);

        BigDecimal criticalPeakThreshold = maxPrice.subtract(range.multiply(new BigDecimal("0.1")));
        BigDecimal peakThreshold = maxPrice.subtract(range.multiply(new BigDecimal("0.3")));
        BigDecimal valleyThreshold = minPrice.add(range.multiply(new BigDecimal("0.3")));

        for (Map.Entry<Integer, BigDecimal> entry : priceMap.entrySet()) {
            BigDecimal price = entry.getValue();
            String periodType;
            if (price.compareTo(criticalPeakThreshold) >= 0) {
                periodType = "CRITICAL_PEAK";
            } else if (price.compareTo(peakThreshold) >= 0) {
                periodType = "PEAK";
            } else if (price.compareTo(valleyThreshold) <= 0) {
                periodType = "VALLEY";
            } else {
                periodType = "FLAT";
            }
            periodMap.put(entry.getKey(), periodType);
        }
        return periodMap;
    }

    private BigDecimal calculateHourlyRevenue(BigDecimal price, BigDecimal batteryPower, BigDecimal gridPower,
                                              BigDecimal netLoad, BigDecimal chargeEfficiency, BigDecimal dischargeEfficiency) {
        BigDecimal baseCost = netLoad.multiply(price);

        if (batteryPower.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal chargeEnergy = batteryPower.abs();
            BigDecimal actualCharge = chargeEnergy.multiply(chargeEfficiency);
            BigDecimal chargeCost = chargeEnergy.multiply(price);
            BigDecimal gridCost = netLoad.add(chargeEnergy).multiply(price);
            return baseCost.subtract(gridCost);
        } else if (batteryPower.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal actualDischarge = batteryPower.divide(dischargeEfficiency, 8, RoundingMode.HALF_UP);
            BigDecimal gridCost = netLoad.subtract(batteryPower).max(BigDecimal.ZERO).multiply(price);
            return baseCost.subtract(gridCost);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateArbitrageRevenue(BigDecimal price, BigDecimal batteryPower,
                                                 BigDecimal chargeEfficiency, BigDecimal dischargeEfficiency) {
        if (batteryPower.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal chargeEnergy = batteryPower.abs();
            return chargeEnergy.multiply(price).negate();
        } else if (batteryPower.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal actualDischarge = batteryPower.divide(dischargeEfficiency, 8, RoundingMode.HALF_UP);
            return actualDischarge.multiply(price);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateDemandSaving(BigDecimal gridPower, BigDecimal baselineDemand,
                                             BigDecimal demandThreshold, BigDecimal demandPrice) {
        if (demandPrice == null || demandPrice.compareTo(BigDecimal.ZERO) <= 0) {
            demandPrice = DEFAULT_DEMAND_PRICE;
        }

        BigDecimal demandReduction = baselineDemand.subtract(gridPower);
        if (demandReduction.compareTo(BigDecimal.ZERO) > 0 && gridPower.compareTo(demandThreshold) < 0) {
            return demandReduction.multiply(demandPrice).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateBaselineDemand(List<SimulationHourData> hourData) {
        return hourData.stream()
                .map(h -> h.getLoadPower().subtract(h.getPvPower() != null ? h.getPvPower() : BigDecimal.ZERO))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private void calculateSummaryStatistics(Simulation simulation, List<SimulationHourData> hourDataList) {
        BigDecimal totalRevenue = hourDataList.stream()
                .map(SimulationHourData::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalArbitrage = hourDataList.stream()
                .map(SimulationHourData::getArbitrageRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDemandSaving = hourDataList.stream()
                .map(SimulationHourData::getDemandSaving)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCharge = hourDataList.stream()
                .filter(h -> h.getBatteryPower().compareTo(BigDecimal.ZERO) < 0)
                .map(h -> h.getBatteryPower().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDischarge = hourDataList.stream()
                .filter(h -> h.getBatteryPower().compareTo(BigDecimal.ZERO) > 0)
                .map(SimulationHourData::getBatteryPower)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> demands = hourDataList.stream()
                .map(SimulationHourData::getDemand)
                .collect(Collectors.toList());
        BigDecimal maxDemand = demands.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal minDemand = demands.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal avgDemand = demands.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(demands.size()), 4, RoundingMode.HALF_UP);

        BigDecimal cycleCount = totalDischarge.min(totalCharge)
                .divide(simulation.getBatteryCapacity(), 4, RoundingMode.HALF_UP);

        List<BigDecimal> dods = hourDataList.stream()
                .filter(h -> h.getActionType() != null && h.getActionType().equals("DISCHARGE"))
                .map(h -> {
                    BigDecimal soc = h.getExpectedSoc();
                    BigDecimal minSoc = simulation.getMinSoc();
                    return soc != null ? soc.subtract(minSoc) : BigDecimal.ZERO;
                })
                .filter(d -> d.compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        BigDecimal avgDod = dods.isEmpty() ? BigDecimal.ZERO :
                dods.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(dods.size()), 2, RoundingMode.HALF_UP);

        simulation.setTotalRevenue(totalRevenue.setScale(4, RoundingMode.HALF_UP));
        simulation.setTotalArbitrageRevenue(totalArbitrage.setScale(4, RoundingMode.HALF_UP));
        simulation.setTotalDemandSaving(totalDemandSaving.setScale(4, RoundingMode.HALF_UP));
        simulation.setTotalChargeEnergy(totalCharge.setScale(4, RoundingMode.HALF_UP));
        simulation.setTotalDischargeEnergy(totalDischarge.setScale(4, RoundingMode.HALF_UP));
        simulation.setCycleCount(cycleCount);
        simulation.setAvgDepthOfDischarge(avgDod);
        simulation.setMaxDemand(maxDemand.setScale(2, RoundingMode.HALF_UP));
        simulation.setMinDemand(minDemand.setScale(2, RoundingMode.HALF_UP));
        simulation.setAvgDemand(avgDemand.setScale(2, RoundingMode.HALF_UP));

        BigDecimal roundTripEfficiency = totalCharge.compareTo(BigDecimal.ZERO) > 0 ?
                totalDischarge.divide(totalCharge, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        simulation.setRoundTripEfficiency(roundTripEfficiency.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP));

        BigDecimal totalLoad = hourDataList.stream()
                .map(SimulationHourData::getLoadPower)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPv = hourDataList.stream()
                .map(h -> h.getPvPower() != null ? h.getPvPower() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal selfConsumption = totalPv.compareTo(BigDecimal.ZERO) > 0 ?
                totalPv.subtract(hourDataList.stream()
                        .map(h -> {
                            BigDecimal pv = h.getPvPower() != null ? h.getPvPower() : BigDecimal.ZERO;
                            BigDecimal load = h.getLoadPower();
                            BigDecimal batteryCharge = h.getBatteryPower().compareTo(BigDecimal.ZERO) < 0 ?
                                    h.getBatteryPower().abs() : BigDecimal.ZERO;
                            return pv.subtract(load).subtract(batteryCharge).max(BigDecimal.ZERO);
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .divide(totalPv, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        simulation.setSelfConsumptionRate(selfConsumption.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP));

        BigDecimal selfSufficiency = totalLoad.compareTo(BigDecimal.ZERO) > 0 ?
                totalLoad.subtract(hourDataList.stream()
                        .map(h -> h.getGridPower().max(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .divide(totalLoad, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        simulation.setSelfSufficiencyRate(selfSufficiency.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP));
    }

    private void calculateBatteryDegradation(Simulation simulation, List<SimulationHourData> hourDataList) {
        Long modelId = simulation.getDegradationModelId();
        BigDecimal initialSoh = BigDecimal.ONE;

        if (modelId != null) {
            try {
                Integer totalCycles = simulation.getCycleCount() != null ?
                        simulation.getCycleCount().multiply(BigDecimal.valueOf(365)).intValue() : 0;
                BigDecimal avgSoc = hourDataList.stream()
                        .map(SimulationHourData::getExpectedSoc)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(hourDataList.size()), 4, RoundingMode.HALF_UP);
                BigDecimal avgChargeRate = hourDataList.stream()
                        .filter(h -> h.getActionType() != null && h.getActionType().equals("CHARGE"))
                        .map(h -> h.getBatteryPower().abs().divide(simulation.getBatteryPower(), 4, RoundingMode.HALF_UP))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avgDischargeRate = hourDataList.stream()
                        .filter(h -> h.getActionType() != null && h.getActionType().equals("DISCHARGE"))
                        .map(h -> h.getBatteryPower().divide(simulation.getBatteryPower(), 4, RoundingMode.HALF_UP))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal finalSoh = batteryDegradationService.estimateSohWithFactors(
                        modelId, totalCycles,
                        BigDecimal.valueOf(25), avgSoc,
                        avgChargeRate, avgDischargeRate,
                        simulation.getAvgDepthOfDischarge());

                BigDecimal degradation = initialSoh.subtract(finalSoh);
                BigDecimal degradationCost = degradation.multiply(simulation.getBatteryCapacity())
                        .multiply(DEFAULT_BATTERY_COST_PER_KWH);

                simulation.setSohStart(initialSoh);
                simulation.setSohEnd(finalSoh);
                simulation.setSohDegradation(degradation);
                simulation.setTotalDegradationCost(degradationCost.setScale(4, RoundingMode.HALF_UP));
                simulation.setNetRevenue(simulation.getTotalRevenue().subtract(degradationCost)
                        .setScale(4, RoundingMode.HALF_UP));

                Integer remainingCycles = batteryDegradationService.estimateRemainingCycles(
                        modelId, finalSoh, totalCycles);
                simulation.setEstimatedRemainingCycles(remainingCycles);

                BigDecimal dailyCycles = simulation.getCycleCount() != null ?
                        simulation.getCycleCount() : new BigDecimal("0.5");
                BigDecimal remainingLifespan = batteryDegradationService.estimateRemainingLifespan(
                        modelId, finalSoh, totalCycles, dailyCycles);
                simulation.setEstimatedRemainingLifespanYears(remainingLifespan);

            } catch (Exception e) {
                log.warn("计算电池衰减失败: {}", e.getMessage());
                simulation.setSohStart(initialSoh);
                simulation.setSohEnd(initialSoh.subtract(new BigDecimal("0.0001")));
                simulation.setSohDegradation(new BigDecimal("0.0001"));
                simulation.setTotalDegradationCost(BigDecimal.ZERO);
                simulation.setNetRevenue(simulation.getTotalRevenue());
            }
        } else {
            BigDecimal dailyDegradation = new BigDecimal("0.00005");
            BigDecimal annualDegradation = dailyDegradation.multiply(BigDecimal.valueOf(365));
            BigDecimal finalSoh = initialSoh.subtract(dailyDegradation);
            BigDecimal degradationCost = annualDegradation.multiply(simulation.getBatteryCapacity())
                    .multiply(DEFAULT_BATTERY_COST_PER_KWH);

            simulation.setSohStart(initialSoh);
            simulation.setSohEnd(finalSoh);
            simulation.setSohDegradation(dailyDegradation);
            simulation.setTotalDegradationCost(degradationCost.setScale(4, RoundingMode.HALF_UP));
            simulation.setNetRevenue(simulation.getTotalRevenue().subtract(degradationCost)
                    .setScale(4, RoundingMode.HALF_UP));
            simulation.setEstimatedRemainingCycles(6000);
            simulation.setEstimatedRemainingLifespanYears(new BigDecimal("15.0"));
        }

        BigDecimal currentSoh = simulation.getSohStart();
        BigDecimal dailyDegradationPerHour = simulation.getSohDegradation()
                .divide(BigDecimal.valueOf(HOURS_PER_DAY), 10, RoundingMode.HALF_UP);

        for (SimulationHourData hourData : hourDataList) {
            currentSoh = currentSoh.subtract(dailyDegradationPerHour);
            hourData.setSoh(currentSoh.setScale(4, RoundingMode.HALF_UP));
            BigDecimal degradationCostPerHour = simulation.getTotalDegradationCost()
                    .divide(BigDecimal.valueOf(HOURS_PER_DAY), 8, RoundingMode.HALF_UP);
            hourData.setDegradationCost(degradationCostPerHour);
            if (hourData.getNetProfit() != null) {
                hourData.setNetProfit(hourData.getNetProfit().subtract(degradationCostPerHour));
            }
        }
    }

    private void calculateEconomicMetrics(Simulation simulation, List<SimulationHourData> hourDataList) {
        BigDecimal baselineMaxDemand = calculateBaselineDemand(hourDataList);
        BigDecimal peakReduction = baselineMaxDemand.subtract(simulation.getMaxDemand());
        simulation.setDemandPeakReduction(peakReduction.setScale(2, RoundingMode.HALF_UP));
    }

    public SimulationReportVO generateReport(Simulation simulation, List<SimulationHourData> hourDataList) {
        SimulationReportVO report = new SimulationReportVO();

        report.setRevenueCurve(generateRevenueCurve(simulation, hourDataList));
        report.setSocCurve(generateSocCurve(simulation, hourDataList));
        report.setDegradationReport(generateDegradationReport(simulation, hourDataList));
        report.setEconomicAnalysis(generateEconomicAnalysis(simulation, hourDataList));

        return report;
    }

    private SimulationReportVO.RevenueCurveVO generateRevenueCurve(Simulation simulation,
                                                                   List<SimulationHourData> hourDataList) {
        SimulationReportVO.RevenueCurveVO curve = new SimulationReportVO.RevenueCurveVO();
        curve.setTimeLabels(hourDataList.stream()
                .map(h -> h.getStartTime().toString())
                .collect(Collectors.toList()));
        curve.setCumulativeRevenue(hourDataList.stream()
                .map(SimulationHourData::getCumulativeRevenue)
                .collect(Collectors.toList()));
        curve.setHourlyRevenue(hourDataList.stream()
                .map(SimulationHourData::getRevenue)
                .collect(Collectors.toList()));
        curve.setHourlyArbitrage(hourDataList.stream()
                .map(SimulationHourData::getArbitrageRevenue)
                .collect(Collectors.toList()));
        curve.setHourlyDemandSaving(hourDataList.stream()
                .map(SimulationHourData::getDemandSaving)
                .collect(Collectors.toList()));
        curve.setTotalRevenue(simulation.getTotalRevenue());

        Optional<SimulationHourData> peakHour = hourDataList.stream()
                .max(Comparator.comparing(SimulationHourData::getRevenue));
        Optional<SimulationHourData> valleyHour = hourDataList.stream()
                .min(Comparator.comparing(SimulationHourData::getRevenue));

        peakHour.ifPresent(h -> {
            curve.setPeakRevenue(h.getRevenue());
            curve.setPeakHour(h.getStartTime().toString());
        });
        valleyHour.ifPresent(h -> {
            curve.setValleyRevenue(h.getRevenue());
            curve.setValleyHour(h.getStartTime().toString());
        });

        return curve;
    }

    private SimulationReportVO.SocCurveVO generateSocCurve(Simulation simulation,
                                                           List<SimulationHourData> hourDataList) {
        SimulationReportVO.SocCurveVO curve = new SimulationReportVO.SocCurveVO();
        curve.setTimeLabels(hourDataList.stream()
                .map(h -> h.getStartTime().toString())
                .collect(Collectors.toList()));
        curve.setSocValues(hourDataList.stream()
                .map(SimulationHourData::getExpectedSoc)
                .collect(Collectors.toList()));
        curve.setBatteryPower(hourDataList.stream()
                .map(SimulationHourData::getBatteryPower)
                .collect(Collectors.toList()));
        curve.setMinSoc(simulation.getMinSoc());
        curve.setMaxSoc(simulation.getMaxSoc());

        List<BigDecimal> socValues = hourDataList.stream()
                .map(SimulationHourData::getExpectedSoc)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        BigDecimal avgSoc = socValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(socValues.size()), 2, RoundingMode.HALF_UP);
        curve.setAvgSoc(avgSoc);

        BigDecimal variance = socValues.stream()
                .map(s -> s.subtract(avgSoc).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(socValues.size()), 4, RoundingMode.HALF_UP);
        curve.setSocVariance(variance);

        long chargeCount = hourDataList.stream()
                .filter(h -> "CHARGE".equals(h.getActionType()))
                .count();
        long dischargeCount = hourDataList.stream()
                .filter(h -> "DISCHARGE".equals(h.getActionType()))
                .count();
        curve.setChargeCount((int) chargeCount);
        curve.setDischargeCount((int) dischargeCount);

        List<BigDecimal> chargeRates = hourDataList.stream()
                .filter(h -> "CHARGE".equals(h.getActionType()))
                .map(h -> h.getBatteryPower().abs().divide(simulation.getBatteryPower(), 4, RoundingMode.HALF_UP))
                .collect(Collectors.toList());
        List<BigDecimal> dischargeRates = hourDataList.stream()
                .filter(h -> "DISCHARGE".equals(h.getActionType()))
                .map(h -> h.getBatteryPower().divide(simulation.getBatteryPower(), 4, RoundingMode.HALF_UP))
                .collect(Collectors.toList());
        curve.setChargeRates(chargeRates);
        curve.setDischargeRates(dischargeRates);

        return curve;
    }

    private SimulationReportVO.BatteryDegradationReportVO generateDegradationReport(
            Simulation simulation, List<SimulationHourData> hourDataList) {
        SimulationReportVO.BatteryDegradationReportVO report = new SimulationReportVO.BatteryDegradationReportVO();
        report.setInitialSoh(simulation.getSohStart());
        report.setFinalSoh(simulation.getSohEnd());
        report.setSohDegradation(simulation.getSohDegradation());
        report.setCycleCount(simulation.getCycleCount());
        report.setAvgDepthOfDischarge(simulation.getAvgDepthOfDischarge());
        report.setEquivalentFullCycles(simulation.getCycleCount());
        report.setEstimatedRemainingCycles(simulation.getEstimatedRemainingCycles());
        report.setEstimatedRemainingLifespanYears(simulation.getEstimatedRemainingLifespanYears());
        report.setDegradationCost(simulation.getTotalDegradationCost());

        BigDecimal annualDegradation = simulation.getSohDegradation().multiply(BigDecimal.valueOf(365));
        report.setDegradationRate(annualDegradation.multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP));

        BigDecimal costPerKwh = simulation.getBatteryCapacity().compareTo(BigDecimal.ZERO) > 0 ?
                simulation.getTotalDegradationCost().divide(simulation.getBatteryCapacity(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        report.setCostPerKwh(costPerKwh);

        report.setTimeLabels(hourDataList.stream()
                .map(h -> h.getStartTime().toString())
                .collect(Collectors.toList()));
        report.setSohCurve(hourDataList.stream()
                .map(SimulationHourData::getSoh)
                .collect(Collectors.toList()));

        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        factors.put("循环次数", simulation.getCycleCount());
        factors.put("平均放电深度", simulation.getAvgDepthOfDischarge());
        factors.put("充放电效率", simulation.getRoundTripEfficiency());
        factors.put("平均SOC", report.getInitialSoh().add(report.getFinalSoh())
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
        report.setDegradationFactors(factors);

        return report;
    }

    private SimulationReportVO.EconomicAnalysisVO generateEconomicAnalysis(
            Simulation simulation, List<SimulationHourData> hourDataList) {
        SimulationReportVO.EconomicAnalysisVO analysis = new SimulationReportVO.EconomicAnalysisVO();
        analysis.setTotalRevenue(simulation.getTotalRevenue());
        analysis.setTotalCost(simulation.getTotalDegradationCost());
        analysis.setNetProfit(simulation.getNetRevenue());

        BigDecimal investmentCost = simulation.getBatteryCapacity().multiply(DEFAULT_BATTERY_COST_PER_KWH);
        analysis.setInvestmentCost(investmentCost);

        BigDecimal annualProfit = simulation.getNetRevenue().multiply(BigDecimal.valueOf(365));
        BigDecimal paybackYears = annualProfit.compareTo(BigDecimal.ZERO) > 0 ?
                investmentCost.divide(annualProfit, 2, RoundingMode.HALF_UP) :
                new BigDecimal("999.99");
        analysis.setPaybackPeriodYears(paybackYears);

        BigDecimal roi = investmentCost.compareTo(BigDecimal.ZERO) > 0 ?
                annualProfit.divide(investmentCost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        analysis.setRoi(roi);
        analysis.setIrr(roi);

        Map<String, BigDecimal> revenueBreakdown = new LinkedHashMap<>();
        revenueBreakdown.put("套利收益", simulation.getTotalArbitrageRevenue());
        revenueBreakdown.put("需量节省", simulation.getTotalDemandSaving());
        analysis.setRevenueBreakdown(revenueBreakdown);

        Map<String, BigDecimal> costBreakdown = new LinkedHashMap<>();
        costBreakdown.put("电池衰减成本", simulation.getTotalDegradationCost());
        analysis.setCostBreakdown(costBreakdown);

        BigDecimal totalDischarge = simulation.getTotalDischargeEnergy();
        BigDecimal lcos = totalDischarge.compareTo(BigDecimal.ZERO) > 0 ?
                simulation.getTotalDegradationCost().divide(totalDischarge, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        analysis.setLevelizedCostOfStorage(lcos);

        analysis.setRoundTripEfficiency(simulation.getRoundTripEfficiency());
        analysis.setSelfConsumptionRate(simulation.getSelfConsumptionRate());
        analysis.setSelfSufficiencyRate(simulation.getSelfSufficiencyRate());

        return analysis;
    }

    public SimulationReportVO.StrategyComparisonVO generateStrategyComparison(
            List<SimulationResultVO> results) {
        SimulationReportVO.StrategyComparisonVO comparison = new SimulationReportVO.StrategyComparisonVO();

        comparison.setStrategyNames(results.stream()
                .map(SimulationResultVO::getStrategyName)
                .collect(Collectors.toList()));
        comparison.setTotalRevenues(results.stream()
                .map(SimulationResultVO::getTotalRevenue)
                .collect(Collectors.toList()));
        comparison.setNetRevenues(results.stream()
                .map(SimulationResultVO::getNetRevenue)
                .collect(Collectors.toList()));
        comparison.setDegradations(results.stream()
                .map(SimulationResultVO::getSohDegradation)
                .collect(Collectors.toList()));
        comparison.setCycleCounts(results.stream()
                .map(SimulationResultVO::getCycleCount)
                .collect(Collectors.toList()));
        comparison.setPeakDemands(results.stream()
                .map(SimulationResultVO::getMaxDemand)
                .collect(Collectors.toList()));
        comparison.setDemandSavings(results.stream()
                .map(SimulationResultVO::getTotalDemandSaving)
                .collect(Collectors.toList()));

        Map<String, Integer> ranking = new LinkedHashMap<>();
        List<SimulationResultVO> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> b.getNetRevenue().compareTo(a.getNetRevenue()));
        IntStream.range(0, sorted.size()).forEach(i ->
                ranking.put(sorted.get(i).getStrategyName(), i + 1));
        comparison.setRanking(ranking);

        if (!sorted.isEmpty()) {
            SimulationResultVO best = sorted.get(0);
            comparison.setRecommendedStrategy(best.getStrategyName());
            comparison.setRecommendationReason(
                    String.format("推荐策略'%s'，净收益最高(%.2f元)，同时电池衰减(%.6f)控制在合理范围",
                            best.getStrategyName(), best.getNetRevenue(), best.getSohDegradation()));
        }

        return comparison;
    }
}
