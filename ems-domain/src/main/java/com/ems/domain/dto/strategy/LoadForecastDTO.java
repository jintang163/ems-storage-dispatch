package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class LoadForecastDTO {

    private Long id;

    private LocalDate forecastDate;

    private Integer hourIndex;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal forecastLoad;

    private BigDecimal forecastPv;

    private BigDecimal forecastGrid;

    private BigDecimal actualLoad;

    private BigDecimal actualPv;

    private BigDecimal loadDeviation;

    private BigDecimal deviationPercentage;

    private String forecastType;

    private String forecastSource;

    private String forecastModel;

    private BigDecimal confidenceLevel;

    private Boolean isPeakHour = false;

    private String transformerCode;

    private String remark;
}
