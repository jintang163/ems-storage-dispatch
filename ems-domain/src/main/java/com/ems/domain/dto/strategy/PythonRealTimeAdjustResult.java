package com.ems.domain.dto.strategy;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PythonRealTimeAdjustResult {

    private boolean success;
    private String message;
    private BigDecimal adjustedPower;
    private String adjustmentReason;
    private String adjustmentType;
    private BigDecimal originalPower;
    private BigDecimal expectedSoc;
    private String urgencyLevel;
}
