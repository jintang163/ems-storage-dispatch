package com.ems.domain.dto.price;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TimeOfUsePriceDTO {

    private Long id;

    @NotBlank(message = "时段类型不能为空")
    private String periodType;

    @NotNull(message = "电价不能为空")
    @DecimalMin(value = "0", message = "电价不能小于0")
    private BigDecimal price;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    private LocalDate expiryDate;

    private Boolean enabled = true;

    private String description;
}
