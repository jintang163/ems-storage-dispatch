package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "price_forecast", indexes = {
        @Index(name = "idx_forecast_date", columnList = "forecast_date"),
        @Index(name = "idx_forecast_source", columnList = "forecast_source")
})
public class PriceForecast extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "hour_index", nullable = false)
    private Integer hourIndex;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "forecast_price", precision = 10, scale = 4, nullable = false)
    private BigDecimal forecastPrice;

    @Column(name = "actual_price", precision = 10, scale = 4)
    private BigDecimal actualPrice;

    @Column(name = "price_deviation", precision = 10, scale = 4)
    private BigDecimal priceDeviation;

    @Column(name = "deviation_percentage", precision = 10, scale = 4)
    private BigDecimal deviationPercentage;

    @Column(name = "period_type", length = 20)
    private String periodType;

    @Column(name = "forecast_source", length = 50)
    private String forecastSource;

    @Column(name = "forecast_model", length = 50)
    private String forecastModel;

    @Column(name = "confidence_level", precision = 5, scale = 2)
    private BigDecimal confidenceLevel;

    @Column(name = "is_peak", nullable = false)
    private Boolean isPeak = false;

    @Column(name = "is_valley", nullable = false)
    private Boolean isValley = false;

    @Column(length = 500)
    private String remark;
}
