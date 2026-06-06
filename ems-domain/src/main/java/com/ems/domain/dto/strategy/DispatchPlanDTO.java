package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class DispatchPlanDTO {

    private Long id;

    private Long strategyId;

    private String strategyCode;

    private LocalDate planDate;

    private String planType;

    private String batterySn;

    private String transformerCode;

    private BigDecimal initialSoc;

    private BigDecimal expectedRevenue;

    private BigDecimal expectedDegradation;

    private BigDecimal expectedDemandSaving;

    private BigDecimal totalObjectiveScore;

    private BigDecimal arbitrageScore;

    private BigDecimal lifespanScore;

    private BigDecimal demandScore;

    private String status;

    private String createdBy;

    private String remark;

    private List<DispatchPlanHourDTO> planHours;
}
