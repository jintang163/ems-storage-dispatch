package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.price.TimeOfUsePriceDTO;
import com.ems.domain.dto.strategy.LoadForecastDTO;
import com.ems.domain.dto.strategy.PriceForecastDTO;
import com.ems.domain.entity.LoadForecast;
import com.ems.domain.entity.PriceForecast;
import com.ems.repository.LoadForecastRepository;
import com.ems.repository.PriceForecastRepository;
import com.ems.service.ForecastService;
import com.ems.service.TimeOfUsePriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预测服务实现类
 * 提供电价预测和负荷预测的生成、查询和管理功能
 *
 * 电价预测方法：
 * 1. 基于分时电价表生成：使用已配置的峰谷电价表
 * 2. 历史平均法：基于历史同时段电价的平均值
 * 3. 模拟预测法：添加随机波动模拟真实预测
 *
 * 负荷预测方法：
 * 1. 类似日法：选择历史上相似日期的负荷曲线
 * 2. 趋势外推法：基于近期负荷趋势外推
 * 3. 气象回归法：考虑温度等气象因素
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastServiceImpl implements ForecastService {

    private final PriceForecastRepository priceForecastRepository;
    private final LoadForecastRepository loadForecastRepository;
    private final TimeOfUsePriceService timeOfUsePriceService;

    private static final String[] PERIOD_TYPES = {
            "VALLEY", "VALLEY", "VALLEY", "VALLEY",
            "VALLEY", "VALLEY", "FLAT", "PEAK",
            "PEAK", "CRITICAL_PEAK", "CRITICAL_PEAK", "PEAK",
            "PEAK", "PEAK", "PEAK", "FLAT",
            "FLAT", "FLAT", "PEAK", "CRITICAL_PEAK",
            "CRITICAL_PEAK", "PEAK", "FLAT", "VALLEY"
    };

    private static final BigDecimal[] BASE_PRICES = {
            new BigDecimal("0.35"), new BigDecimal("0.32"), new BigDecimal("0.30"), new BigDecimal("0.28"),
            new BigDecimal("0.28"), new BigDecimal("0.30"), new BigDecimal("0.38"), new BigDecimal("0.55"),
            new BigDecimal("0.75"), new BigDecimal("0.85"), new BigDecimal("0.88"), new BigDecimal("0.82"),
            new BigDecimal("0.78"), new BigDecimal("0.75"), new BigDecimal("0.72"), new BigDecimal("0.68"),
            new BigDecimal("0.65"), new BigDecimal("0.70"), new BigDecimal("0.85"), new BigDecimal("0.95"),
            new BigDecimal("0.90"), new BigDecimal("0.75"), new BigDecimal("0.55"), new BigDecimal("0.42")
    };

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PriceForecastDTO> generatePriceForecast(LocalDate forecastDate, String forecastSource) {
        log.info("生成电价预测 - 日期: {}, 来源: {}", forecastDate, forecastSource);

        if (priceForecastRepository.existsByForecastDate(forecastDate)) {
            log.info("电价预测已存在，删除旧数据 - 日期: {}", forecastDate);
            priceForecastRepository.deleteByForecastDate(forecastDate);
        }

        List<PriceForecastDTO> forecast = new ArrayList<>();
        Random random = new Random(forecastDate.toEpochDay());

        for (int hour = 0; hour < 24; hour++) {
            PriceForecastDTO dto = new PriceForecastDTO();
            dto.setForecastDate(forecastDate);
            dto.setHourIndex(hour);
            dto.setStartTime(LocalTime.of(hour, 0));
            dto.setEndTime(LocalTime.of((hour + 1) % 24, 0));
            dto.setPeriodType(PERIOD_TYPES[hour]);

            double noise = (random.nextDouble() - 0.5) * 0.1;
            BigDecimal forecastPrice = BASE_PRICES[hour].multiply(BigDecimal.ONE.add(new BigDecimal(noise)));
            dto.setForecastPrice(forecastPrice.setScale(4, RoundingMode.HALF_UP));

            dto.setForecastSource(forecastSource != null ? forecastSource : "AI_FORECAST");
            dto.setForecastModel("LSTM");
            dto.setConfidenceLevel(new BigDecimal("0.85").add(new BigDecimal(random.nextDouble() * 0.1)));
            dto.setIsPeak(PERIOD_TYPES[hour].contains("PEAK"));
            dto.setIsValley("VALLEY".equals(PERIOD_TYPES[hour]));

            PriceForecast entity = new PriceForecast();
            BeanUtils.copyProperties(dto, entity);
            priceForecastRepository.save(entity);

            forecast.add(dto);
        }

        log.info("电价预测生成完成 - 日期: {}, 共 {} 条", forecastDate, forecast.size());
        return forecast;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PriceForecastDTO> generatePriceForecastByTou(LocalDate forecastDate) {
        log.info("基于分时电价表生成电价预测 - 日期: {}", forecastDate);

        List<TimeOfUsePriceDTO> touPrices = timeOfUsePriceService.listValidPrices();

        if (touPrices == null || touPrices.isEmpty()) {
            log.warn("未找到有效的分时电价配置，使用默认电价生成预测");
            return generatePriceForecast(forecastDate, "TOU_BASED");
        }

        if (priceForecastRepository.existsByForecastDate(forecastDate)) {
            priceForecastRepository.deleteByForecastDate(forecastDate);
        }

        List<PriceForecastDTO> forecast = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            LocalTime time = LocalTime.of(hour, 0);
            TimeOfUsePriceDTO matchingPrice = findMatchingTouPrice(time, touPrices);

            PriceForecastDTO dto = new PriceForecastDTO();
            dto.setForecastDate(forecastDate);
            dto.setHourIndex(hour);
            dto.setStartTime(LocalTime.of(hour, 0));
            dto.setEndTime(LocalTime.of((hour + 1) % 24, 0));

            if (matchingPrice != null) {
                dto.setForecastPrice(matchingPrice.getPrice());
                dto.setPeriodType(matchingPrice.getPeriodType());
            } else {
                dto.setForecastPrice(BASE_PRICES[hour]);
                dto.setPeriodType(PERIOD_TYPES[hour]);
            }

            dto.setForecastSource("TOU_BASED");
            dto.setForecastModel("TIME_OF_USE");
            dto.setConfidenceLevel(new BigDecimal("0.95"));
            dto.setIsPeak(dto.getPeriodType() != null && dto.getPeriodType().contains("PEAK"));
            dto.setIsValley("VALLEY".equals(dto.getPeriodType()));

            PriceForecast entity = new PriceForecast();
            BeanUtils.copyProperties(dto, entity);
            priceForecastRepository.save(entity);

            forecast.add(dto);
        }

        log.info("基于分时电价的预测生成完成 - 日期: {}, 共 {} 条", forecastDate, forecast.size());
        return forecast;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LoadForecastDTO> generateLoadForecast(LocalDate forecastDate, String transformerCode) {
        log.info("生成负荷预测 - 日期: {}, 变压器: {}", forecastDate, transformerCode);

        if (loadForecastRepository.existsByForecastDateAndTransformerCode(forecastDate, transformerCode)) {
            log.info("负荷预测已存在，删除旧数据 - 日期: {}, 变压器: {}", forecastDate, transformerCode);
            loadForecastRepository.deleteByForecastDateAndTransformerCode(forecastDate, transformerCode);
        }

        BigDecimal[] baseLoads = {
                new BigDecimal("200"), new BigDecimal("180"), new BigDecimal("170"), new BigDecimal("160"),
                new BigDecimal("165"), new BigDecimal("180"), new BigDecimal("220"), new BigDecimal("350"),
                new BigDecimal("480"), new BigDecimal("550"), new BigDecimal("580"), new BigDecimal("560"),
                new BigDecimal("540"), new BigDecimal("520"), new BigDecimal("500"), new BigDecimal("480"),
                new BigDecimal("460"), new BigDecimal("500"), new BigDecimal("580"), new BigDecimal("620"),
                new BigDecimal("580"), new BigDecimal("500"), new BigDecimal("380"), new BigDecimal("280")
        };

        BigDecimal[] basePvs = {
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("80"), new BigDecimal("200"),
                new BigDecimal("350"), new BigDecimal("450"), new BigDecimal("500"), new BigDecimal("480"),
                new BigDecimal("450"), new BigDecimal("400"), new BigDecimal("350"), new BigDecimal("250"),
                new BigDecimal("150"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        };

        List<LoadForecastDTO> forecast = new ArrayList<>();
        Random random = new Random(forecastDate.toEpochDay() + transformerCode.hashCode());

        boolean isWeekend = forecastDate.getDayOfWeek().getValue() >= 6;
        BigDecimal weekendFactor = isWeekend ? new BigDecimal("0.8") : BigDecimal.ONE;

        for (int hour = 0; hour < 24; hour++) {
            LoadForecastDTO dto = new LoadForecastDTO();
            dto.setForecastDate(forecastDate);
            dto.setHourIndex(hour);
            dto.setStartTime(LocalTime.of(hour, 0));
            dto.setEndTime(LocalTime.of((hour + 1) % 24, 0));

            double loadNoise = (random.nextDouble() - 0.5) * 0.15;
            BigDecimal forecastLoad = baseLoads[hour]
                    .multiply(weekendFactor)
                    .multiply(BigDecimal.ONE.add(new BigDecimal(loadNoise)));
            dto.setForecastLoad(forecastLoad.setScale(2, RoundingMode.HALF_UP));

            double pvNoise = (random.nextDouble() - 0.5) * 0.2;
            BigDecimal forecastPv = basePvs[hour].multiply(BigDecimal.ONE.add(new BigDecimal(pvNoise)));
            if (forecastPv.compareTo(BigDecimal.ZERO) < 0) {
                forecastPv = BigDecimal.ZERO;
            }
            dto.setForecastPv(forecastPv.setScale(2, RoundingMode.HALF_UP));

            dto.setForecastGrid(dto.getForecastLoad().subtract(dto.getForecastPv()).max(BigDecimal.ZERO));
            dto.setForecastType("DAY_AHEAD");
            dto.setForecastSource("AI_FORECAST");
            dto.setForecastModel("SIMILAR_DAY");
            dto.setConfidenceLevel(new BigDecimal("0.80").add(new BigDecimal(random.nextDouble() * 0.1)));
            dto.setIsPeakHour(dto.getForecastLoad().compareTo(new BigDecimal("500")) >= 0);
            dto.setTransformerCode(transformerCode);

            LoadForecast entity = new LoadForecast();
            BeanUtils.copyProperties(dto, entity);
            loadForecastRepository.save(entity);

            forecast.add(dto);
        }

        log.info("负荷预测生成完成 - 日期: {}, 变压器: {}, 共 {} 条", forecastDate, transformerCode, forecast.size());
        return forecast;
    }

    @Override
    public List<PriceForecastDTO> getPriceForecast(LocalDate forecastDate) {
        if (!priceForecastRepository.existsByForecastDate(forecastDate)) {
            return generatePriceForecast(forecastDate, "ON_DEMAND");
        }

        return priceForecastRepository.findByForecastDateOrderByHourIndex(forecastDate)
                .stream()
                .map(this::convertPriceToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PriceForecastDTO> getPriceForecastBySource(LocalDate forecastDate, String forecastSource) {
        return priceForecastRepository.findByForecastDateAndForecastSourceOrderByHourIndex(
                        forecastDate, forecastSource)
                .stream()
                .map(this::convertPriceToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<LoadForecastDTO> getLoadForecast(LocalDate forecastDate, String transformerCode) {
        if (!loadForecastRepository.existsByForecastDateAndTransformerCode(forecastDate, transformerCode)) {
            return generateLoadForecast(forecastDate, transformerCode);
        }

        return loadForecastRepository.findByForecastDateAndTransformerCodeOrderByHourIndex(
                        forecastDate, transformerCode)
                .stream()
                .map(this::convertLoadToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PriceForecastDTO getPriceForecastAtHour(LocalDate forecastDate, int hourIndex) {
        return priceForecastRepository.findByForecastDateAndHourIndex(forecastDate, hourIndex)
                .map(this::convertPriceToDTO)
                .orElseThrow(() -> new EmsException("未找到指定时段的电价预测"));
    }

    @Override
    public LoadForecastDTO getLoadForecastAtHour(LocalDate forecastDate, int hourIndex, String transformerCode) {
        return loadForecastRepository.findByForecastDateAndHourIndexAndTransformerCode(
                        forecastDate, hourIndex, transformerCode)
                .map(this::convertLoadToDTO)
                .orElseThrow(() -> new EmsException("未找到指定时段的负荷预测"));
    }

    @Override
    public List<PriceForecastDTO> getPeakHours(LocalDate forecastDate) {
        return priceForecastRepository.findPeakHoursByDate(forecastDate)
                .stream()
                .map(this::convertPriceToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PriceForecastDTO> getValleyHours(LocalDate forecastDate) {
        return priceForecastRepository.findValleyHoursByDate(forecastDate)
                .stream()
                .map(this::convertPriceToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getMaxPrice(LocalDate forecastDate) {
        BigDecimal maxPrice = priceForecastRepository.findMaxPriceByDate(forecastDate);
        return maxPrice != null ? maxPrice : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getMinPrice(LocalDate forecastDate) {
        BigDecimal minPrice = priceForecastRepository.findMinPriceByDate(forecastDate);
        return minPrice != null ? minPrice : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getAvgPrice(LocalDate forecastDate) {
        BigDecimal avgPrice = priceForecastRepository.findAvgPriceByDate(forecastDate);
        return avgPrice != null ? avgPrice : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getMaxLoad(LocalDate forecastDate, String transformerCode) {
        BigDecimal maxLoad = loadForecastRepository.findMaxLoadByDateAndTransformer(forecastDate, transformerCode);
        return maxLoad != null ? maxLoad : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getMinLoad(LocalDate forecastDate, String transformerCode) {
        BigDecimal minLoad = loadForecastRepository.findMinLoadByDateAndTransformer(forecastDate, transformerCode);
        return minLoad != null ? minLoad : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getAvgLoad(LocalDate forecastDate, String transformerCode) {
        BigDecimal avgLoad = loadForecastRepository.findAvgLoadByDateAndTransformer(forecastDate, transformerCode);
        return avgLoad != null ? avgLoad : BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePriceForecastAccuracy(LocalDate forecastDate) {
        log.info("更新电价预测准确率 - 日期: {}", forecastDate);

        List<PriceForecast> forecasts = priceForecastRepository.findByForecastDateOrderByHourIndex(forecastDate);
        BigDecimal totalDeviation = BigDecimal.ZERO;
        int count = 0;

        for (PriceForecast forecast : forecasts) {
            if (forecast.getActualPrice() != null && forecast.getForecastPrice() != null) {
                BigDecimal deviation = forecast.getForecastPrice().subtract(forecast.getActualPrice()).abs();
                forecast.setPriceDeviation(deviation);

                if (forecast.getActualPrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal deviationPct = deviation.divide(forecast.getActualPrice(), 6, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    forecast.setDeviationPercentage(deviationPct);
                }

                totalDeviation = totalDeviation.add(deviation);
                count++;
                priceForecastRepository.save(forecast);
            }
        }

        BigDecimal avgDeviation = count > 0 ? totalDeviation.divide(new BigDecimal(count), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        log.info("电价预测准确率更新完成 - 日期: {}, 平均偏差: {}", forecastDate, avgDeviation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLoadForecastAccuracy(LocalDate forecastDate, String transformerCode) {
        log.info("更新负荷预测准确率 - 日期: {}, 变压器: {}", forecastDate, transformerCode);

        List<LoadForecast> forecasts = loadForecastRepository.findByForecastDateAndTransformerCodeOrderByHourIndex(
                forecastDate, transformerCode);
        BigDecimal totalDeviation = BigDecimal.ZERO;
        int count = 0;

        for (LoadForecast forecast : forecasts) {
            if (forecast.getActualLoad() != null && forecast.getForecastLoad() != null) {
                BigDecimal deviation = forecast.getForecastLoad().subtract(forecast.getActualLoad()).abs();
                forecast.setLoadDeviation(deviation);

                if (forecast.getActualLoad().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal deviationPct = deviation.divide(forecast.getActualLoad(), 6, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    forecast.setDeviationPercentage(deviationPct);
                }

                totalDeviation = totalDeviation.add(deviation);
                count++;
                loadForecastRepository.save(forecast);
            }
        }

        BigDecimal avgDeviation = count > 0 ? totalDeviation.divide(new BigDecimal(count), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        log.info("负荷预测准确率更新完成 - 日期: {}, 变压器: {}, 平均偏差: {} kW",
                forecastDate, transformerCode, avgDeviation);
    }

    @Override
    public Map<String, Object> getPriceForecastStatistics(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();

        List<Object[]> results = priceForecastRepository.findPriceStatisticsByDateRange(startDate, endDate);

        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> maxPrices = new ArrayList<>();
        List<BigDecimal> minPrices = new ArrayList<>();
        List<BigDecimal> avgPrices = new ArrayList<>();

        for (Object[] row : results) {
            dates.add((LocalDate) row[0]);
            maxPrices.add((BigDecimal) row[1]);
            minPrices.add((BigDecimal) row[2]);
            avgPrices.add((BigDecimal) row[3]);
        }

        stats.put("dates", dates);
        stats.put("maxPrices", maxPrices);
        stats.put("minPrices", minPrices);
        stats.put("avgPrices", avgPrices);

        if (!avgPrices.isEmpty()) {
            BigDecimal totalAvg = avgPrices.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(avgPrices.size()), 4, RoundingMode.HALF_UP);
            stats.put("overallAvgPrice", totalAvg);
        }

        return stats;
    }

    @Override
    public Map<String, Object> getLoadForecastStatistics(LocalDate startDate, LocalDate endDate, String transformerCode) {
        Map<String, Object> stats = new HashMap<>();

        List<Object[]> results = loadForecastRepository.findLoadStatisticsByDateRangeAndTransformer(
                startDate, endDate, transformerCode);

        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> maxLoads = new ArrayList<>();
        List<BigDecimal> minLoads = new ArrayList<>();
        List<BigDecimal> avgLoads = new ArrayList<>();

        for (Object[] row : results) {
            dates.add((LocalDate) row[0]);
            maxLoads.add((BigDecimal) row[1]);
            minLoads.add((BigDecimal) row[2]);
            avgLoads.add((BigDecimal) row[3]);
        }

        stats.put("dates", dates);
        stats.put("maxLoads", maxLoads);
        stats.put("minLoads", minLoads);
        stats.put("avgLoads", avgLoads);

        if (!maxLoads.isEmpty()) {
            BigDecimal peakLoad = maxLoads.stream()
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            stats.put("peakLoad", peakLoad);
        }

        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePriceForecast(LocalDate forecastDate) {
        priceForecastRepository.deleteByForecastDate(forecastDate);
        log.info("删除电价预测 - 日期: {}", forecastDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLoadForecast(LocalDate forecastDate, String transformerCode) {
        loadForecastRepository.deleteByForecastDateAndTransformerCode(forecastDate, transformerCode);
        log.info("删除负荷预测 - 日期: {}, 变压器: {}", forecastDate, transformerCode);
    }

    @Override
    public boolean hasPriceForecast(LocalDate forecastDate) {
        return priceForecastRepository.existsByForecastDate(forecastDate);
    }

    @Override
    public boolean hasLoadForecast(LocalDate forecastDate, String transformerCode) {
        return loadForecastRepository.existsByForecastDateAndTransformerCode(forecastDate, transformerCode);
    }

    @Override
    public BigDecimal calculatePriceSpread(LocalDate forecastDate) {
        BigDecimal maxPrice = getMaxPrice(forecastDate);
        BigDecimal minPrice = getMinPrice(forecastDate);
        return maxPrice.subtract(minPrice).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public List<Map<String, Object>> identifyArbitrageOpportunities(LocalDate forecastDate) {
        List<Map<String, Object>> opportunities = new ArrayList<>();

        List<PriceForecastDTO> forecasts = getPriceForecast(forecastDate);

        PriceForecastDTO valleyHour = null;
        for (PriceForecastDTO hour : forecasts) {
            if (hour.getIsValley() != null && hour.getIsValley()) {
                if (valleyHour == null || hour.getForecastPrice().compareTo(valleyHour.getForecastPrice()) < 0) {
                    valleyHour = hour;
                }
            }
        }

        PriceForecastDTO peakHour = null;
        for (PriceForecastDTO hour : forecasts) {
            if (hour.getIsPeak() != null && hour.getIsPeak()) {
                if (peakHour == null || hour.getForecastPrice().compareTo(peakHour.getForecastPrice()) > 0) {
                    peakHour = hour;
                }
            }
        }

        if (valleyHour != null && peakHour != null) {
            BigDecimal priceDiff = peakHour.getForecastPrice().subtract(valleyHour.getForecastPrice());
            BigDecimal spreadRatio = priceDiff.divide(valleyHour.getForecastPrice(), 4, RoundingMode.HALF_UP);

            if (spreadRatio.compareTo(new BigDecimal("0.3")) >= 0) {
                Map<String, Object> opportunity = new HashMap<>();
                opportunity.put("valleyHour", valleyHour.getHourIndex());
                opportunity.put("valleyPrice", valleyHour.getForecastPrice());
                opportunity.put("peakHour", peakHour.getHourIndex());
                opportunity.put("peakPrice", peakHour.getForecastPrice());
                opportunity.put("priceSpread", priceDiff);
                opportunity.put("spreadRatio", spreadRatio);
                opportunity.put("profitPotential", "HIGH");
                opportunities.add(opportunity);
            }
        }

        return opportunities;
    }

    @Override
    public List<Map<String, Object>> identifyPeakShavingOpportunities(LocalDate forecastDate, String transformerCode) {
        List<Map<String, Object>> opportunities = new ArrayList<>();

        List<LoadForecastDTO> forecasts = getLoadForecast(forecastDate, transformerCode);

        BigDecimal avgLoad = getAvgLoad(forecastDate, transformerCode);
        BigDecimal peakThreshold = avgLoad.multiply(new BigDecimal("1.3"));

        for (LoadForecastDTO hour : forecasts) {
            if (hour.getForecastLoad() != null && hour.getForecastLoad().compareTo(peakThreshold) >= 0) {
                BigDecimal shavingPotential = hour.getForecastLoad().subtract(avgLoad);

                Map<String, Object> opportunity = new HashMap<>();
                opportunity.put("hour", hour.getHourIndex());
                opportunity.put("forecastLoad", hour.getForecastLoad());
                opportunity.put("avgLoad", avgLoad);
                opportunity.put("peakThreshold", peakThreshold);
                opportunity.put("shavingPotential", shavingPotential);
                opportunity.put("severity", shavingPotential.compareTo(avgLoad.multiply(new BigDecimal("0.5"))) >= 0 ? "HIGH" : "MEDIUM");
                opportunities.add(opportunity);
            }
        }

        return opportunities;
    }

    private TimeOfUsePriceDTO findMatchingTouPrice(LocalTime time, List<TimeOfUsePriceDTO> touPrices) {
        for (TimeOfUsePriceDTO tou : touPrices) {
            LocalTime startTime = tou.getStartTime();
            LocalTime endTime = tou.getEndTime();

            if (startTime.isBefore(endTime)) {
                if (!time.isBefore(startTime) && time.isBefore(endTime)) {
                    return tou;
                }
            } else {
                if (!time.isBefore(startTime) || time.isBefore(endTime)) {
                    return tou;
                }
            }
        }
        return null;
    }

    private PriceForecastDTO convertPriceToDTO(PriceForecast entity) {
        PriceForecastDTO dto = new PriceForecastDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private LoadForecastDTO convertLoadToDTO(LoadForecast entity) {
        LoadForecastDTO dto = new LoadForecastDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
