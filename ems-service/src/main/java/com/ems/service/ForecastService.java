package com.ems.service;

import com.ems.domain.dto.strategy.LoadForecastDTO;
import com.ems.domain.dto.strategy.PriceForecastDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 预测服务接口
 * 提供电价预测和负荷预测的生成、查询和管理功能
 *
 * 电价预测方法：
 * 1. 历史回归法：基于历史电价数据和天气数据建立回归模型
 * 2. 时间序列法：ARIMA、LSTM等时间序列预测模型
 * 3. 机器学习法：基于多因子模型，考虑温度、湿度、工作日/周末等因素
 *
 * 负荷预测方法：
 * 1. 类似日法：选择历史上相似日期的负荷曲线
 * 2. 时间序列法：基于历史负荷数据的趋势预测
 * 3. 气象回归法：考虑温度、湿度、风速等气象因素
 *
 * @author EMS Team
 * @since 1.0.0
 */
public interface ForecastService {

    List<PriceForecastDTO> generatePriceForecast(LocalDate forecastDate, String forecastSource);

    List<PriceForecastDTO> generatePriceForecastByTou(LocalDate forecastDate);

    List<LoadForecastDTO> generateLoadForecast(LocalDate forecastDate, String transformerCode);

    List<PriceForecastDTO> getPriceForecast(LocalDate forecastDate);

    List<PriceForecastDTO> getPriceForecastBySource(LocalDate forecastDate, String forecastSource);

    List<LoadForecastDTO> getLoadForecast(LocalDate forecastDate, String transformerCode);

    PriceForecastDTO getPriceForecastAtHour(LocalDate forecastDate, int hourIndex);

    LoadForecastDTO getLoadForecastAtHour(LocalDate forecastDate, int hourIndex, String transformerCode);

    List<PriceForecastDTO> getPeakHours(LocalDate forecastDate);

    List<PriceForecastDTO> getValleyHours(LocalDate forecastDate);

    BigDecimal getMaxPrice(LocalDate forecastDate);

    BigDecimal getMinPrice(LocalDate forecastDate);

    BigDecimal getAvgPrice(LocalDate forecastDate);

    BigDecimal getMaxLoad(LocalDate forecastDate, String transformerCode);

    BigDecimal getMinLoad(LocalDate forecastDate, String transformerCode);

    BigDecimal getAvgLoad(LocalDate forecastDate, String transformerCode);

    void updatePriceForecastAccuracy(LocalDate forecastDate);

    void updateLoadForecastAccuracy(LocalDate forecastDate, String transformerCode);

    Map<String, Object> getPriceForecastStatistics(LocalDate startDate, LocalDate endDate);

    Map<String, Object> getLoadForecastStatistics(LocalDate startDate, LocalDate endDate, String transformerCode);

    void deletePriceForecast(LocalDate forecastDate);

    void deleteLoadForecast(LocalDate forecastDate, String transformerCode);

    boolean hasPriceForecast(LocalDate forecastDate);

    boolean hasLoadForecast(LocalDate forecastDate, String transformerCode);

    BigDecimal calculatePriceSpread(LocalDate forecastDate);

    List<Map<String, Object>> identifyArbitrageOpportunities(LocalDate forecastDate);

    List<Map<String, Object>> identifyPeakShavingOpportunities(LocalDate forecastDate, String transformerCode);
}
