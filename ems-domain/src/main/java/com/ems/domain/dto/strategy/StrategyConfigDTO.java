package com.ems.domain.dto.strategy;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StrategyConfigDTO {

    private Long id;

    @NotBlank(message = "策略名称不能为空")
    private String strategyName;

    @NotBlank(message = "策略类型不能为空")
    private String strategyType;

    @NotBlank(message = "策略编码不能为空")
    private String strategyCode;

    @DecimalMin(value = "0", message = "套利权重不能小于0")
    @DecimalMax(value = "1", message = "套利权重不能大于1")
    private BigDecimal arbitrageWeight = new BigDecimal("0.50");

    @DecimalMin(value = "0", message = "寿命权重不能小于0")
    @DecimalMax(value = "1", message = "寿命权重不能大于1")
    private BigDecimal lifespanWeight = new BigDecimal("0.30");

    @DecimalMin(value = "0", message = "需量权重不能小于0")
    @DecimalMax(value = "1", message = "需量权重不能大于1")
    private BigDecimal demandWeight = new BigDecimal("0.20");

    @DecimalMin(value = "0", message = "最大充电倍率不能小于0")
    @DecimalMax(value = "2", message = "最大充电倍率不能大于2")
    private BigDecimal maxChargeRate = new BigDecimal("0.50");

    @DecimalMin(value = "0", message = "最大放电倍率不能小于0")
    @DecimalMax(value = "2", message = "最大放电倍率不能大于2")
    private BigDecimal maxDischargeRate = new BigDecimal("0.50");

    @DecimalMin(value = "0", message = "最小SOC不能小于0")
    @DecimalMax(value = "100", message = "最小SOC不能大于100")
    private BigDecimal minSoc = new BigDecimal("20.00");

    @DecimalMin(value = "0", message = "最大SOC不能小于0")
    @DecimalMax(value = "100", message = "最大SOC不能大于100")
    private BigDecimal maxSoc = new BigDecimal("90.00");

    @DecimalMin(value = "0", message = "最大日循环次数不能小于0")
    @DecimalMax(value = "10", message = "最大日循环次数不能大于10")
    private BigDecimal maxDailyCycles = new BigDecimal("1.00");

    @DecimalMin(value = "0", message = "最大放电深度不能小于0")
    @DecimalMax(value = "100", message = "最大放电深度不能大于100")
    private BigDecimal maxDepthOfDischarge = new BigDecimal("70.00");

    @DecimalMin(value = "0.5", message = "需量阈值比例不能小于0.5")
    @DecimalMax(value = "1.5", message = "需量阈值比例不能大于1.5")
    private BigDecimal demandThresholdRatio = new BigDecimal("0.90");

    private Boolean priceForecastEnabled = true;

    private Boolean peakValleyArbitrageEnabled = true;

    private Boolean peakShavingEnabled = true;

    private Boolean valleyFillingEnabled = true;

    private Boolean demandControlEnabled = true;

    private String batterySn;

    private String transformerCode;

    @NotNull(message = "调度间隔不能为空")
    private Integer scheduleIntervalMinutes = 60;

    private Boolean rollingOptimizationEnabled = true;

    private Integer rollingIntervalMinutes = 15;

    private Integer lookAheadHours = 24;

    private Integer priority = 5;

    private Boolean enabled = true;

    private Boolean defaultStrategy = false;

    private String description;
}
