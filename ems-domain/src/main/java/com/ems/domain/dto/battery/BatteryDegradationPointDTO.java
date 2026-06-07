package com.ems.domain.dto.battery;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BatteryDegradationPointDTO {

    private Long id;

    @NotNull(message = "循环次数不能为空")
    private Integer cycleCount;

    @NotNull(message = "SOH不能为空")
    @DecimalMin(value = "0", message = "SOH不能小于0")
    @DecimalMax(value = "1", message = "SOH不能大于1")
    private BigDecimal soh;

    private BigDecimal capacityRetention;

    private BigDecimal internalResistanceRatio;

    private BigDecimal temperature;

    private BigDecimal depthOfDischarge;

    private BigDecimal chargeRate;

    private BigDecimal dischargeRate;

    private String remarks;
}
