package com.ems.domain.vo.simulation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class SimulationHourDataVO {

    private Long id;

    private Integer hourIndex;

    private LocalTime startTime;

    private LocalTime endTime;

    private String periodType;

    private BigDecimal price;

    private BigDecimal loadPower;

    private BigDecimal pvPower;

    private BigDecimal gridPower;

    private BigDecimal batteryPower;

    private BigDecimal batteryEnergy;

    private BigDecimal expectedSoc;

    private BigDecimal chargeRate;

    private BigDecimal depthOfDischarge;

    private String actionType;

    private BigDecimal demand;

    private Boolean demandControlRequired;

    private BigDecimal revenue;

    private BigDecimal arbitrageRevenue;

    private BigDecimal demandSaving;

    private BigDecimal degradationCost;

    private BigDecimal netProfit;

    private BigDecimal cumulativeRevenue;

    private BigDecimal soh;

    private BigDecimal batteryTemperature;

    private String remark;
}
