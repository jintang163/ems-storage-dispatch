package com.ems.service;

import com.ems.domain.dto.strategy.PythonOptimizationRequest;
import com.ems.domain.dto.strategy.PythonOptimizationResult;
import com.ems.domain.dto.strategy.PythonRealTimeAdjustRequest;
import com.ems.domain.dto.strategy.PythonRealTimeAdjustResult;

import java.util.List;
import java.util.Map;

public interface PythonOptimizerService {

    PythonOptimizationResult rollingOptimize(PythonOptimizationRequest request);

    PythonRealTimeAdjustResult realTimeAdjust(PythonRealTimeAdjustRequest request);

    boolean isHealthy();

    Map<String, Object> getStatus();

    List<Map<String, Object>> getOptimizationHistory(String strategyCode, int limit);

    List<Map<String, Object>> getAdjustmentHistory(String strategyCode, String adjustmentType, int limit);

    Map<String, Object> getAdjustmentStatistics(String strategyCode);

    Map<String, Object> rollingOptimize15Min(Map<String, Object> request);
}
