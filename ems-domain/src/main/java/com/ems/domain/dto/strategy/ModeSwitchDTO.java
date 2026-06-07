package com.ems.domain.dto.strategy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModeSwitchDTO {

    @NotBlank(message = "策略编码不能为空")
    private String strategyCode;

    @NotBlank(message = "目标模式不能为空")
    private String targetMode;

    private String operator;

    private String remark;

    @NotNull(message = "安全确认不能为空")
    private Boolean safetyConfirmed;

    private String safetyConfirmNote;
}
