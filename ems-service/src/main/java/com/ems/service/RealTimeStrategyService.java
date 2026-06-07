package com.ems.service;

import com.ems.domain.dto.strategy.ManualForceChargeDischargeDTO;
import com.ems.domain.dto.strategy.ManualStandbyDTO;
import com.ems.domain.dto.strategy.ModeSwitchDTO;
import com.ems.domain.dto.strategy.RealTimeControlRequest;
import com.ems.domain.dto.strategy.StrategyExecutionLogDTO;
import com.ems.domain.dto.strategy.StrategyParamAdjustDTO;
import com.ems.domain.vo.strategy.StrategyResultVO;
import com.ems.domain.vo.strategy.StrategyStatisticsVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 实时策略控制服务接口
 * 提供实时充放电控制、需量管理和策略执行功能
 *
 * 核心功能：
 * 1. 实时需量控制：当实时负荷接近需量阈值时，优先放电抑制需量超标
 * 2. 动态调整：根据实时数据动态调整充放电功率
 * 3. 紧急响应：处理突发的负荷变化和异常情况
 * 4. 执行记录：记录每次策略执行的详细信息
 *
 * 实时控制策略：
 * 1. 需量预警机制：
 *    - 预测需量 > 阈值×80%：黄色预警，准备放电
 *    - 预测需量 > 阈值×90%：橙色预警，开始放电
 *    - 预测需量 > 阈值×100%：红色告警，全力放电
 *
 * 2. 控制优先级：
 *    - 优先级1：需量控制（避免需量超标罚款）
 *    - 优先级2：电池保护（遵守SOC和温度约束）
 *    - 优先级3：计划执行（按照调度计划运行）
 *    - 优先级4：套利优化（实时电价机会）
 *
 * 3. 电池寿命保护：
 *    - 限制充放电倍率，避免大电流冲击
 *    - 限制放电深度，避免深循环
 *    - 控制日循环次数，减少累计损耗
 *
 * @author EMS Team
 * @since 1.0.0
 */
public interface RealTimeStrategyService {

    StrategyResultVO executeRealTimeControl(RealTimeControlRequest request);

    StrategyResultVO executeDemandControl(RealTimeControlRequest request);

    StrategyResultVO executePeakValleyArbitrage(RealTimeControlRequest request);

    StrategyResultVO executePeakShaving(RealTimeControlRequest request);

    StrategyResultVO executeValleyFilling(RealTimeControlRequest request);

    String checkDemandWarning(String strategyCode, BigDecimal currentDemand, BigDecimal predictedDemand);

    BigDecimal calculateRequiredDischargePower(String strategyCode, BigDecimal currentDemand, BigDecimal predictedDemand);

    BigDecimal calculateRequiredChargePower(String strategyCode, String transformerCode,
                                            BigDecimal currentDemand, BigDecimal currentSoc);

    List<StrategyExecutionLogDTO> listExecutionLogs(String strategyCode, LocalDateTime startTime, LocalDateTime endTime);

    List<StrategyExecutionLogDTO> listRecentExecutions(String strategyCode, int hours);

    StrategyStatisticsVO getExecutionStatistics(String strategyCode, LocalDate startDate, LocalDate endDate);

    Map<String, Object> getRealTimeStatus(String strategyCode, String batterySn, String transformerCode);

    Map<String, BigDecimal> getTotalBenefits(String strategyCode, LocalDate startDate, LocalDate endDate);

    Map<String, Object> getControlActionStatistics(String strategyCode, LocalDate startDate, LocalDate endDate);

    BigDecimal calculateBatteryDegradationCost(String strategyCode, BigDecimal chargeRate, BigDecimal dischargeRate,
                                                BigDecimal depthOfDischarge, BigDecimal temperature);

    BigDecimal calculateDemandChargeSaving(String strategyCode, BigDecimal originalDemand, BigDecimal controlledDemand);

    StrategyResultVO executeMultiObjectiveOptimization(RealTimeControlRequest request);

    void logExecution(StrategyResultVO result, RealTimeControlRequest request);

    StrategyResultVO executeManualForceChargeDischarge(ManualForceChargeDischargeDTO request);

    StrategyResultVO executeManualStandby(ManualStandbyDTO request);

    StrategyResultVO adjustStrategyParameters(StrategyParamAdjustDTO request);

    Map<String, Object> switchControlMode(ModeSwitchDTO request);

    Map<String, Object> getControlModeStatus(String strategyCode);

    void cancelManualControl(String strategyCode, String operator, String remark);

    Map<String, String> validateManualControlSafety(ManualForceChargeDischargeDTO request);

    Map<String, String> validateModeSwitchSafety(ModeSwitchDTO request);
}
