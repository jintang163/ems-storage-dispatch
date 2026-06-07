package com.ems.domain.vo.simulation;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class SimulationReportVO {

    private RevenueCurveVO revenueCurve;

    private SocCurveVO socCurve;

    private BatteryDegradationReportVO degradationReport;

    private EconomicAnalysisVO economicAnalysis;

    private StrategyComparisonVO strategyComparison;

    @Data
    public static class RevenueCurveVO {
        private List<String> timeLabels;
        private List<BigDecimal> cumulativeRevenue;
        private List<BigDecimal> hourlyRevenue;
        private List<BigDecimal> hourlyArbitrage;
        private List<BigDecimal> hourlyDemandSaving;
        private BigDecimal totalRevenue;
        private BigDecimal peakRevenue;
        private BigDecimal valleyRevenue;
        private String peakHour;
        private String valleyHour;
    }

    @Data
    public static class SocCurveVO {
        private List<String> timeLabels;
        private List<BigDecimal> socValues;
        private List<BigDecimal> batteryPower;
        private BigDecimal minSoc;
        private BigDecimal maxSoc;
        private BigDecimal avgSoc;
        private BigDecimal socVariance;
        private Integer chargeCount;
        private Integer dischargeCount;
        private List<BigDecimal> chargeRates;
        private List<BigDecimal> dischargeRates;
    }

    @Data
    public static class BatteryDegradationReportVO {
        private BigDecimal initialSoh;
        private BigDecimal finalSoh;
        private BigDecimal sohDegradation;
        private BigDecimal degradationRate;
        private BigDecimal cycleCount;
        private BigDecimal avgDepthOfDischarge;
        private BigDecimal equivalentFullCycles;
        private Integer estimatedRemainingCycles;
        private BigDecimal estimatedRemainingLifespanYears;
        private List<String> timeLabels;
        private List<BigDecimal> sohCurve;
        private Map<String, BigDecimal> degradationFactors;
        private BigDecimal degradationCost;
        private BigDecimal costPerKwh;
    }

    @Data
    public static class EconomicAnalysisVO {
        private BigDecimal totalRevenue;
        private BigDecimal totalCost;
        private BigDecimal netProfit;
        private BigDecimal investmentCost;
        private BigDecimal paybackPeriodYears;
        private BigDecimal roi;
        private BigDecimal irr;
        private Map<String, BigDecimal> revenueBreakdown;
        private Map<String, BigDecimal> costBreakdown;
        private BigDecimal levelizedCostOfStorage;
        private BigDecimal roundTripEfficiency;
        private BigDecimal selfConsumptionRate;
        private BigDecimal selfSufficiencyRate;
    }

    @Data
    public static class StrategyComparisonVO {
        private List<String> strategyNames;
        private List<BigDecimal> totalRevenues;
        private List<BigDecimal> netRevenues;
        private List<BigDecimal> degradations;
        private List<BigDecimal> cycleCounts;
        private List<BigDecimal> peakDemands;
        private List<BigDecimal> demandSavings;
        private Map<String, Integer> ranking;
        private String recommendedStrategy;
        private String recommendationReason;
    }
}
