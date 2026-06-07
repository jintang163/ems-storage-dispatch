package com.ems.domain.dto.battery;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BatteryDegradationModelDTO {

    private Long id;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    private String batteryType;

    @DecimalMin(value = "0", message = "每循环衰减率不能小于0")
    private BigDecimal degradationRatePerCycle;

    @DecimalMin(value = "0", message = "衰减常数不能小于0")
    private BigDecimal decayConstant;

    @NotNull(message = "寿命终止SOH不能为空")
    @DecimalMin(value = "0.5", message = "寿命终止SOH不能小于0.5")
    @DecimalMax(value = "0.9", message = "寿命终止SOH不能大于0.9")
    private BigDecimal endOfLifeSoh;

    private Integer warrantyCycleCount;

    private BigDecimal warrantySoh;

    private BigDecimal calendarAgingRatePerYear;

    private BigDecimal temperatureFactor;

    private BigDecimal socFactor;

    private BigDecimal chargeRateFactor;

    private BigDecimal dischargeRateFactor;

    private BigDecimal depthOfDischargeFactor;

    private Integer maxCycleCount;

    private BigDecimal estimatedLifespanYears;

    private Boolean defaultModel = false;

    private Boolean enabled = true;

    private String description;

    private List<BatteryDegradationPointDTO> degradationPoints;
}
