package com.ems.domain.dto.simulation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class SimulationDataPointDTO {

    @NotNull(message = "日期不能为空")
    private LocalDate date;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    private LocalTime endTime;

    private Integer hourIndex;

    @NotNull(message = "数值不能为空")
    @DecimalMin(value = "0", message = "数值不能小于0")
    private BigDecimal value;

    private String periodType;

    private String remark;
}
