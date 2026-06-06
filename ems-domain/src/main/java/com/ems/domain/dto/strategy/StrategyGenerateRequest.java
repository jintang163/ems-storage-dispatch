package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StrategyGenerateRequest {

    private Long strategyId;

    private String strategyCode;

    private LocalDate planDate;

    private String planType;

    private BigDecimal initialSoc;

    private String batterySn;

    private String transformerCode;

    private Boolean usePriceForecast = true;

    private Boolean useLoadForecast = true;

    private String createdBy;

    private String remark;
}
