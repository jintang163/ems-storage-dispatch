package com.ems.domain.dto.strategy;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StrategyParamAdjustDTO {

    @NotBlank(message = "策略编码不能为空")
    private String strategyCode;

    @DecimalMin(value = "0", message = "最小SOC不能小于0")
    @DecimalMax(value = "100", message = "最小SOC不能大于100")
    private BigDecimal minSoc;

    @DecimalMin(value = "0", message = "最大SOC不能小于0")
    @DecimalMax(value = "100", message = "最大SOC不能大于100")
    private BigDecimal maxSoc;

    @DecimalMin(value = "0.5", message = "需量阈值比例不能小于0.5")
    @DecimalMax(value = "1.5", message = "需量阈值比例不能大于1.5")
    private BigDecimal demandThresholdRatio;

    @DecimalMin(value = "0", message = "套利权重不能小于0")
    @DecimalMax(value = "1", message = "套利权重不能大于1")
    private BigDecimal arbitrageWeight;

    @DecimalMin(value = "0", message = "寿命权重不能小于0")
    @DecimalMax(value = "1", message = "寿命权重不能大于1")
    private BigDecimal lifespanWeight;

    @DecimalMin(value = "0", message = "需量权重不能小于0")
    @DecimalMax(value = "1", message = "需量权重不能大于1")
    private BigDecimal demandWeight;

    @DecimalMin(value = "0", message = "最大充电倍率不能小于0")
    @DecimalMax(value = "2", message = "最大充电倍率不能大于2")
    private BigDecimal maxChargeRate;

    @DecimalMin(value = "0", message = "最大放电倍率不能小于0")
    @DecimalMax(value = "2", message = "最大放电倍率不能大于2")
    private BigDecimal maxDischargeRate;

    private String operator;

    private String remark;
}
