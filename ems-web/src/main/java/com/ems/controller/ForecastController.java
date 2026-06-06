package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.strategy.LoadForecastDTO;
import com.ems.domain.dto.strategy.PriceForecastDTO;
import com.ems.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 预测数据控制器
 * 提供电价预测和负荷预测的REST API接口
 *
 * 核心功能：
 * 1. 电价预测生成和查询：支持AI预测和分时电价表两种方式
 * 2. 负荷预测生成和查询：考虑负荷特性、光伏出力、工作日/周末等因素
 * 3. 预测准确率评估：对比实际值与预测值，计算偏差
 * 4. 套利机会识别：自动识别峰谷价差较大的时段
 * 5. 削峰机会识别：自动识别负荷高峰时段
 *
 * 电价预测方法：
 * - AI_FORECAST: 基于LSTM神经网络的预测模型
 * - TOU_BASED: 基于分时电价表的预测
 * - HISTORICAL: 基于历史平均的预测
 *
 * 负荷预测方法：
 * - SIMILAR_DAY: 基于类似日的预测
 * - AI_FORECAST: 基于机器学习的预测
 * - TREND: 基于趋势外推的预测
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/strategy/forecast")
@RequiredArgsConstructor
@CrossOrigin
public class ForecastController {

    private final ForecastService forecastService;

    @PostMapping("/price/generate")
    public Result<List<PriceForecastDTO>> generatePriceForecast(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "AI_FORECAST") String source) {
        return Result.success(forecastService.generatePriceForecast(date, source));
    }

    @PostMapping("/price/generate-tou")
    public Result<List<PriceForecastDTO>> generatePriceForecastByTou(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.generatePriceForecastByTou(date));
    }

    @PostMapping("/load/generate")
    public Result<List<LoadForecastDTO>> generateLoadForecast(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.generateLoadForecast(date, transformerCode));
    }

    @GetMapping("/price/{date}")
    public Result<List<PriceForecastDTO>> getPriceForecast(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.getPriceForecast(date));
    }

    @GetMapping("/price/{date}/source/{source}")
    public Result<List<PriceForecastDTO>> getPriceForecastBySource(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String source) {
        return Result.success(forecastService.getPriceForecastBySource(date, source));
    }

    @GetMapping("/load/{date}")
    public Result<List<LoadForecastDTO>> getLoadForecast(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.getLoadForecast(date, transformerCode));
    }

    @GetMapping("/price/{date}/hour/{hour}")
    public Result<PriceForecastDTO> getPriceForecastAtHour(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable int hour) {
        return Result.success(forecastService.getPriceForecastAtHour(date, hour));
    }

    @GetMapping("/load/{date}/hour/{hour}")
    public Result<LoadForecastDTO> getLoadForecastAtHour(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable int hour,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.getLoadForecastAtHour(date, hour, transformerCode));
    }

    @GetMapping("/price/{date}/peak-hours")
    public Result<List<PriceForecastDTO>> getPeakHours(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.getPeakHours(date));
    }

    @GetMapping("/price/{date}/valley-hours")
    public Result<List<PriceForecastDTO>> getValleyHours(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.getValleyHours(date));
    }

    @GetMapping("/price/{date}/max")
    public Result<BigDecimal> getMaxPrice(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.getMaxPrice(date));
    }

    @GetMapping("/price/{date}/min")
    public Result<BigDecimal> getMinPrice(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.getMinPrice(date));
    }

    @GetMapping("/price/{date}/avg")
    public Result<BigDecimal> getAvgPrice(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.getAvgPrice(date));
    }

    @GetMapping("/load/{date}/max")
    public Result<BigDecimal> getMaxLoad(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.getMaxLoad(date, transformerCode));
    }

    @GetMapping("/load/{date}/min")
    public Result<BigDecimal> getMinLoad(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.getMinLoad(date, transformerCode));
    }

    @GetMapping("/load/{date}/avg")
    public Result<BigDecimal> getAvgLoad(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.getAvgLoad(date, transformerCode));
    }

    @PostMapping("/price/{date}/update-accuracy")
    public Result<Void> updatePriceForecastAccuracy(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        forecastService.updatePriceForecastAccuracy(date);
        return Result.success();
    }

    @PostMapping("/load/{date}/update-accuracy")
    public Result<Void> updateLoadForecastAccuracy(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        forecastService.updateLoadForecastAccuracy(date, transformerCode);
        return Result.success();
    }

    @GetMapping("/price/statistics")
    public Result<Map<String, Object>> getPriceForecastStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(forecastService.getPriceForecastStatistics(startDate, endDate));
    }

    @GetMapping("/load/statistics")
    public Result<Map<String, Object>> getLoadForecastStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.getLoadForecastStatistics(startDate, endDate, transformerCode));
    }

    @DeleteMapping("/price/{date}")
    public Result<Void> deletePriceForecast(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        forecastService.deletePriceForecast(date);
        return Result.success();
    }

    @DeleteMapping("/load/{date}")
    public Result<Void> deleteLoadForecast(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        forecastService.deleteLoadForecast(date, transformerCode);
        return Result.success();
    }

    @GetMapping("/price/{date}/exists")
    public Result<Boolean> hasPriceForecast(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.hasPriceForecast(date));
    }

    @GetMapping("/load/{date}/exists")
    public Result<Boolean> hasLoadForecast(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.hasLoadForecast(date, transformerCode));
    }

    @GetMapping("/price/{date}/spread")
    public Result<BigDecimal> calculatePriceSpread(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.calculatePriceSpread(date));
    }

    @GetMapping("/arbitrage-opportunities/{date}")
    public Result<List<Map<String, Object>>> identifyArbitrageOpportunities(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(forecastService.identifyArbitrageOpportunities(date));
    }

    @GetMapping("/peak-shaving-opportunities/{date}")
    public Result<List<Map<String, Object>>> identifyPeakShavingOpportunities(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String transformerCode) {
        return Result.success(forecastService.identifyPeakShavingOpportunities(date, transformerCode));
    }
}
