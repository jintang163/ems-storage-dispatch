package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "dispatch_plan_hour", indexes = {
        @Index(name = "idx_plan_hour_plan", columnList = "plan_id"),
        @Index(name = "idx_plan_hour_start", columnList = "start_time")
})
public class DispatchPlanHour extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "hour_index", nullable = false)
    private Integer hourIndex;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "period_type", length = 20)
    private String periodType;

    @Column(name = "price", precision = 10, scale = 4)
    private BigDecimal price;

    @Column(name = "power", precision = 10, scale = 2, nullable = false)
    private BigDecimal power;

    @Column(name = "energy", precision = 10, scale = 4)
    private BigDecimal energy;

    @Column(name = "expected_soc", precision = 5, scale = 2)
    private BigDecimal expectedSoc;

    @Column(name = "charge_rate", precision = 5, scale = 2)
    private BigDecimal chargeRate;

    @Column(name = "depth_of_discharge", precision = 5, scale = 2)
    private BigDecimal depthOfDischarge;

    @Column(name = "action_type", length = 20)
    private String actionType;

    @Column(name = "forecast_load", precision = 10, scale = 2)
    private BigDecimal forecastLoad;

    @Column(name = "forecast_pv", precision = 10, scale = 2)
    private BigDecimal forecastPv;

    @Column(name = "expected_demand", precision = 10, scale = 2)
    private BigDecimal expectedDemand;

    @Column(name = "demand_control_required")
    private Boolean demandControlRequired = false;

    @Column(name = "revenue", precision = 12, scale = 4)
    private BigDecimal revenue;

    @Column(name = "degradation_cost", precision = 12, scale = 4)
    private BigDecimal degradationCost;

    @Column(name = "demand_saving", precision = 12, scale = 4)
    private BigDecimal demandSaving;

    @Column(name = "objective_score", precision = 10, scale = 4)
    private BigDecimal objectiveScore;

    @Column(length = 500)
    private String remark;
}
