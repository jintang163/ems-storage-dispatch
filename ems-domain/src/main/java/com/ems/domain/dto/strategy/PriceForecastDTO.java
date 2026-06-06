package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class PriceForecastDTO {

    private Long id;

    private LocalDate forecastDate;

    private Integer hourIndex;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal forecastPrice;

    private BigDecimal actualPrice;

    private BigDecimal priceDeviation;

    private BigDecimal deviationPercentage;

    private String periodType;

    private String forecastSource;

    private String forecastModel;

    private BigDecimal confidenceLevel;

    private Boolean isPeak = false;

    private Boolean isValley = false;

    private String remark;
}
