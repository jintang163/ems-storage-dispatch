package com.ems.domain.dto.strategy;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ManualForceChargeDischargeDTO {

    @NotBlank(message = "策略编码不能为空")
    private String strategyCode;

    @NotBlank(message = "操作类型不能为空")
    private String actionType;

    @NotNull(message = "目标功率不能为空")
    @DecimalMin(value = "0", message = "目标功率不能小于0")
    @DecimalMax(value = "10000", message = "目标功率不能大于10000")
    private BigDecimal targetPower;

    @NotNull(message = "持续时间不能为空")
    @DecimalMin(value = "60", message = "持续时间不能小于60秒")
    @DecimalMax(value = "86400", message = "持续时间不能大于86400秒")
    private Integer durationSeconds;

    private String operator;

    private String remark;

    @NotNull(message = "安全确认不能为空")
    private Boolean safetyConfirmed;

    private String safetyConfirmNote;
}
