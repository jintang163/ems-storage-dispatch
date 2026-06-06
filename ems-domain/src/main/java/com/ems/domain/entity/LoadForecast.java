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
@Table(name = "load_forecast", indexes = {
        @Index(name = "idx_load_forecast_date", columnList = "forecast_date"),
        @Index(name = "idx_load_forecast_type", columnList = "forecast_type")
})
public class LoadForecast extends BaseEntity {

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

    @Column(name = "forecast_load", precision = 10, scale = 2, nullable = false)
    private BigDecimal forecastLoad;

    @Column(name = "forecast_pv", precision = 10, scale = 2)
    private BigDecimal forecastPv;

    @Column(name = "forecast_grid", precision = 10, scale = 2)
    private BigDecimal forecastGrid;

    @Column(name = "actual_load", precision = 10, scale = 2)
    private BigDecimal actualLoad;

    @Column(name = "actual_pv", precision = 10, scale = 2)
    private BigDecimal actualPv;

    @Column(name = "load_deviation", precision = 10, scale = 2)
    private BigDecimal loadDeviation;

    @Column(name = "deviation_percentage", precision = 10, scale = 4)
    private BigDecimal deviationPercentage;

    @Column(name = "forecast_type", length = 20)
    private String forecastType;

    @Column(name = "forecast_source", length = 50)
    private String forecastSource;

    @Column(name = "forecast_model", length = 50)
    private String forecastModel;

    @Column(name = "confidence_level", precision = 5, scale = 2)
    private BigDecimal confidenceLevel;

    @Column(name = "is_peak_hour", nullable = false)
    private Boolean isPeakHour = false;

    @Column(name = "transformer_code", length = 50)
    private String transformerCode;

    @Column(length = 500)
    private String remark;
}
