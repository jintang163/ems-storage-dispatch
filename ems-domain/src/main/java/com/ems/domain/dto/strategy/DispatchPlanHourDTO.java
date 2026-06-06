package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class DispatchPlanHourDTO {

    private Long id;

    private Long planId;

    private Integer hourIndex;

    private LocalTime startTime;

    private LocalTime endTime;

    private String periodType;

    private BigDecimal price;

    private BigDecimal power;

    private BigDecimal energy;

    private BigDecimal expectedSoc;

    private BigDecimal chargeRate;

    private BigDecimal depthOfDischarge;

    private String actionType;

    private BigDecimal forecastLoad;

    private BigDecimal forecastPv;

    private BigDecimal expectedDemand;

    private Boolean demandControlRequired = false;

    private BigDecimal revenue;

    private BigDecimal degradationCost;

    private BigDecimal demandSaving;

    private BigDecimal objectiveScore;

    private String remark;
}
