package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "dispatch_plan", indexes = {
        @Index(name = "idx_plan_date", columnList = "plan_date"),
        @Index(name = "idx_plan_status", columnList = "status"),
        @Index(name = "idx_plan_strategy", columnList = "strategy_id")
})
public class DispatchPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "strategy_code", nullable = false, length = 50)
    private String strategyCode;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType;

    @Column(name = "battery_sn", length = 50)
    private String batterySn;

    @Column(name = "transformer_code", length = 50)
    private String transformerCode;

    @Column(name = "initial_soc", precision = 5, scale = 2)
    private BigDecimal initialSoc;

    @Column(name = "expected_revenue", precision = 12, scale = 4)
    private BigDecimal expectedRevenue;

    @Column(name = "expected_degradation", precision = 10, scale = 8)
    private BigDecimal expectedDegradation;

    @Column(name = "expected_demand_saving", precision = 12, scale = 4)
    private BigDecimal expectedDemandSaving;

    @Column(name = "total_objective_score", precision = 10, scale = 4)
    private BigDecimal totalObjectiveScore;

    @Column(name = "arbitrage_score", precision = 10, scale = 4)
    private BigDecimal arbitrageScore;

    @Column(name = "lifespan_score", precision = 10, scale = 4)
    private BigDecimal lifespanScore;

    @Column(name = "demand_score", precision = 10, scale = 4)
    private BigDecimal demandScore;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(length = 500)
    private String remark;
}
