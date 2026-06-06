"""实时调整服务

核心功能：
1. SOC偏差修正：当实际SOC与预期SOC偏差超过阈值时，动态调整充放电功率
2. 负荷突变响应：当实际负荷与预测负荷偏差超过阈值时，动态调整功率
3. 优先级控制：需量控制 > 电池保护 > 计划执行 > 套利优化
"""

import logging
from typing import Optional, Dict, Tuple
from datetime import datetime

from .models import RealTimeAdjustRequest, RealTimeAdjustResult
from .config import OptimizerConfig

logger = logging.getLogger(__name__)


class RealTimeAdjustService:
    """实时调整服务"""

    def __init__(self, config: Optional[OptimizerConfig] = None):
        self.config = config or OptimizerConfig()
        self.adjustment_history: list[Dict] = []

    def adjust(self, request: RealTimeAdjustRequest) -> RealTimeAdjustResult:
        """
        执行实时调整

        调整逻辑：
        1. 计算SOC偏差：ΔSOC = 实际SOC - 预期SOC
        2. 计算负荷突变：ΔLoad = (实际负荷 - 预测负荷) / 预测负荷 × 100%
        3. 根据偏差类型和程度，动态调整充放电功率

        Args:
            request: 实时调整请求

        Returns:
            调整结果
        """
        logger.info(
            f"执行实时调整 - 策略: {request.strategy_code}, "
            f"当前SOC: {request.current_soc}%, 预期SOC: {request.expected_soc}%"
        )

        try:
            strategy_config = request.strategy_config
            soc_deviation = request.current_soc - request.expected_soc
            load_deviation_percent = self._calculate_load_deviation_percent(request)

            logger.debug(
                f"SOC偏差: {soc_deviation:+.2f}%, "
                f"负荷偏差: {load_deviation_percent:+.1f}%"
            )

            adjusted_power = request.planned_power
            adjustment_type = "NONE"
            adjustment_reason = "无需调整"
            urgency_level = "NORMAL"

            if abs(soc_deviation) >= request.soc_deviation_threshold:
                adjusted_power, adjustment_type, adjustment_reason = self._adjust_for_soc_deviation(
                    request, soc_deviation, adjusted_power
                )
                urgency_level = self._determine_urgency_level(
                    abs(soc_deviation), request.soc_deviation_threshold
                )
            elif abs(load_deviation_percent) >= request.load_sudden_change_threshold:
                adjusted_power, adjustment_type, adjustment_reason = self._adjust_for_load_sudden_change(
                    request, load_deviation_percent, adjusted_power
                )
                urgency_level = "MEDIUM"

            adjusted_power = self._apply_power_constraints(
                adjusted_power, request.current_soc, strategy_config
            )

            expected_soc = self._calculate_expected_soc(
                request.current_soc, adjusted_power
            )

            result = RealTimeAdjustResult(
                success=True,
                message=adjustment_reason,
                adjusted_power=round(adjusted_power, 2),
                adjustment_reason=adjustment_reason,
                adjustment_type=adjustment_type,
                original_power=request.planned_power,
                expected_soc=round(expected_soc, 2),
                urgency_level=urgency_level
            )

            self._record_adjustment(request, result, soc_deviation, load_deviation_percent)

            if adjustment_type != "NONE":
                logger.info(
                    f"实时调整完成 - 类型: {adjustment_type}, "
                    f"原功率: {request.planned_power:.2f}kW, "
                    f"调整后: {adjusted_power:.2f}kW, "
                    f"原因: {adjustment_reason}"
                )
            else:
                logger.debug("实时调整检查完成，无需调整")

            return result

        except Exception as e:
            logger.error(f"实时调整失败: {e}", exc_info=True)
            return RealTimeAdjustResult(
                success=False,
                message=f"调整失败: {str(e)}",
                adjusted_power=request.planned_power,
                adjustment_reason=f"调整失败: {str(e)}",
                adjustment_type="NONE",
                original_power=request.planned_power
            )

    def _calculate_load_deviation_percent(self, request: RealTimeAdjustRequest) -> float:
        """计算负荷偏差百分比"""
        if request.forecast_load == 0:
            return 0.0
        return (request.current_load - request.forecast_load) / request.forecast_load * 100.0

    def _adjust_for_soc_deviation(
        self,
        request: RealTimeAdjustRequest,
        soc_deviation: float,
        current_power: float
    ) -> Tuple[float, str, str]:
        """
        针对SOC偏差进行调整

        调整策略：
        - SOC偏高（ΔSOC > 阈值）：增加放电功率 / 减少充电功率
        - SOC偏低（ΔSOC < -阈值）：减少放电功率 / 增加充电功率

        调整幅度：
        - 轻度偏差（< 2×阈值）：调整25%
        - 中度偏差（2-3×阈值）：调整50%
        - 重度偏差（> 3×阈值）：调整100%
        """
        strategy_config = request.strategy_config
        threshold = request.soc_deviation_threshold
        battery_capacity = self.config.battery_capacity

        deviation_ratio = abs(soc_deviation) / threshold

        if deviation_ratio < 2:
            adjust_ratio = 0.25
            severity = "轻度"
        elif deviation_ratio < 3:
            adjust_ratio = 0.5
            severity = "中度"
        else:
            adjust_ratio = 1.0
            severity = "重度"

        max_charge_power = battery_capacity * strategy_config.max_charge_rate
        max_discharge_power = battery_capacity * strategy_config.max_discharge_rate

        if soc_deviation > 0:
            if current_power > 0:
                adjustment = -current_power * adjust_ratio
                adjusted_power = max(0, current_power + adjustment)
                reason = f"{severity}SOC偏高(+{soc_deviation:.1f}%)，减少充电功率{adjust_ratio*100:.0f}%"
            else:
                max_additional_discharge = max_discharge_power + current_power
                adjustment = -max_additional_discharge * adjust_ratio
                adjusted_power = min(0, current_power + adjustment)
                reason = f"{severity}SOC偏高(+{soc_deviation:.1f}%)，增加放电功率{adjust_ratio*100:.0f}%"
        else:
            if current_power < 0:
                adjustment = -current_power * adjust_ratio
                adjusted_power = min(0, current_power + adjustment)
                reason = f"{severity}SOC偏低({soc_deviation:.1f}%)，减少放电功率{adjust_ratio*100:.0f}%"
            else:
                max_additional_charge = max_charge_power - current_power
                adjustment = max_additional_charge * adjust_ratio
                adjusted_power = min(max_charge_power, current_power + adjustment)
                reason = f"{severity}SOC偏低({soc_deviation:.1f}%)，增加充电功率{adjust_ratio*100:.0f}%"

        return adjusted_power, "SOC_CORRECTION", reason

    def _adjust_for_load_sudden_change(
        self,
        request: RealTimeAdjustRequest,
        load_deviation_percent: float,
        current_power: float
    ) -> Tuple[float, str, str]:
        """
        针对负荷突变进行调整

        调整策略：
        - 负荷突增（> 阈值）：增加放电功率 / 减少充电功率，平抑负荷
        - 负荷突降（< -阈值）：减少放电功率 / 增加充电功率

        调整幅度与负荷偏差成正比
        """
        strategy_config = request.strategy_config
        threshold = request.load_sudden_change_threshold
        battery_capacity = self.config.battery_capacity

        deviation_ratio = abs(load_deviation_percent) / threshold
        adjust_ratio = min(1.0, deviation_ratio * 0.3)

        max_charge_power = battery_capacity * strategy_config.max_charge_rate
        max_discharge_power = battery_capacity * strategy_config.max_discharge_rate

        if load_deviation_percent > 0:
            load_delta = request.current_load - request.forecast_load
            if current_power > 0:
                max_reduction = current_power
                adjustment = -min(max_reduction, load_delta) * adjust_ratio
                adjusted_power = current_power + adjustment
                reason = (
                    f"负荷突增(+{load_deviation_percent:.1f}%, +{load_delta:.0f}kW)，"
                    f"减少充电功率{adjust_ratio*100:.0f}%以平抑负荷"
                )
            else:
                max_additional_discharge = max_discharge_power + current_power
                adjustment = -min(max_additional_discharge, load_delta) * adjust_ratio
                adjusted_power = current_power + adjustment
                reason = (
                    f"负荷突增(+{load_deviation_percent:.1f}%, +{load_delta:.0f}kW)，"
                    f"增加放电功率{adjust_ratio*100:.0f}%以平抑负荷"
                )
        else:
            load_delta = request.current_load - request.forecast_load
            if current_power < 0:
                max_reduction = -current_power
                adjustment = min(max_reduction, -load_delta) * adjust_ratio
                adjusted_power = current_power + adjustment
                reason = (
                    f"负荷突降({load_deviation_percent:.1f}%, {load_delta:.0f}kW)，"
                    f"减少放电功率{adjust_ratio*100:.0f}%"
                )
            else:
                max_additional_charge = max_charge_power - current_power
                adjustment = min(max_additional_charge, -load_delta) * adjust_ratio
                adjusted_power = current_power + adjustment
                reason = (
                    f"负荷突降({load_deviation_percent:.1f}%, {load_delta:.0f}kW)，"
                    f"增加充电功率{adjust_ratio*100:.0f}%"
                )

        return adjusted_power, "LOAD_ADJUST", reason

    def _apply_power_constraints(
        self,
        power: float,
        current_soc: float,
        strategy_config
    ) -> float:
        """应用功率约束条件"""
        battery_capacity = self.config.battery_capacity
        max_charge_power = battery_capacity * strategy_config.max_charge_rate
        max_discharge_power = battery_capacity * strategy_config.max_discharge_rate
        min_soc = strategy_config.min_soc
        max_soc = strategy_config.max_soc

        power = max(-max_discharge_power, min(max_charge_power, power))

        energy = abs(power) / 24.0
        charge_efficiency = self.config.charge_efficiency
        discharge_efficiency = self.config.discharge_efficiency

        if power > 0:
            soc_change = energy * charge_efficiency / battery_capacity * 100.0
            if current_soc + soc_change > max_soc:
                max_allowed_energy = (max_soc - current_soc) / 100.0 * battery_capacity
                max_allowed_power = max_allowed_energy * 24.0 / charge_efficiency
                power = min(power, max_allowed_power)
        else:
            soc_change = -energy / (discharge_efficiency * battery_capacity) * 100.0
            if current_soc + soc_change < min_soc:
                max_allowed_energy = (current_soc - min_soc) / 100.0 * battery_capacity * discharge_efficiency
                max_allowed_power = -max_allowed_energy * 24.0
                power = max(power, max_allowed_power)

        return power

    def _calculate_expected_soc(self, current_soc: float, power: float) -> float:
        """计算调整后的预期SOC"""
        battery_capacity = self.config.battery_capacity
        charge_efficiency = self.config.charge_efficiency
        discharge_efficiency = self.config.discharge_efficiency

        energy = abs(power) / 24.0
        if power > 0:
            soc_change = energy * charge_efficiency / battery_capacity * 100.0
        else:
            soc_change = -energy / (discharge_efficiency * battery_capacity) * 100.0

        return max(0.0, min(100.0, current_soc + soc_change))

    def _determine_urgency_level(
        self,
        deviation: float,
        threshold: float
    ) -> str:
        """根据偏差程度确定紧急级别"""
        ratio = deviation / threshold
        if ratio >= 3:
            return "CRITICAL"
        elif ratio >= 2:
            return "HIGH"
        elif ratio >= 1.5:
            return "MEDIUM"
        else:
            return "LOW"

    def _record_adjustment(
        self,
        request: RealTimeAdjustRequest,
        result: RealTimeAdjustResult,
        soc_deviation: float,
        load_deviation_percent: float
    ) -> None:
        """记录调整历史"""
        record = {
            'timestamp': datetime.now().isoformat(),
            'strategy_code': request.strategy_code,
            'current_soc': request.current_soc,
            'expected_soc': request.expected_soc,
            'soc_deviation': soc_deviation,
            'current_load': request.current_load,
            'forecast_load': request.forecast_load,
            'load_deviation_percent': load_deviation_percent,
            'original_power': request.planned_power,
            'adjusted_power': result.adjusted_power,
            'adjustment_type': result.adjustment_type,
            'adjustment_reason': result.adjustment_reason,
            'urgency_level': result.urgency_level,
            'success': result.success
        }

        self.adjustment_history.append(record)

        if len(self.adjustment_history) > 10000:
            self.adjustment_history = self.adjustment_history[-10000:]

    def get_adjustment_history(
        self,
        strategy_code: Optional[str] = None,
        adjustment_type: Optional[str] = None,
        limit: int = 100
    ) -> list[Dict]:
        """获取调整历史记录"""
        history = self.adjustment_history

        if strategy_code:
            history = [
                h for h in history
                if h['strategy_code'] == strategy_code
            ]

        if adjustment_type:
            history = [
                h for h in history
                if h['adjustment_type'] == adjustment_type
            ]

        return history[-limit:]

    def get_adjustment_statistics(
        self,
        strategy_code: Optional[str] = None
    ) -> Dict:
        """获取调整统计信息"""
        history = self.adjustment_history

        if strategy_code:
            history = [
                h for h in history
                if h['strategy_code'] == strategy_code
            ]

        if not history:
            return {
                'total_adjustments': 0,
                'soc_corrections': 0,
                'load_adjustments': 0,
                'no_adjustments': 0,
                'avg_power_adjustment': 0.0,
                'critical_count': 0,
                'high_count': 0,
                'medium_count': 0,
                'low_count': 0,
                'normal_count': 0
            }

        total = len(history)
        soc_corrections = sum(1 for h in history if h['adjustment_type'] == 'SOC_CORRECTION')
        load_adjustments = sum(1 for h in history if h['adjustment_type'] == 'LOAD_ADJUST')
        no_adjustments = sum(1 for h in history if h['adjustment_type'] == 'NONE')

        power_adjustments = [
            abs(h['adjusted_power'] - h['original_power'])
            for h in history
            if h['adjustment_type'] != 'NONE'
        ]
        avg_adjustment = sum(power_adjustments) / len(power_adjustments) if power_adjustments else 0.0

        urgency_counts = {
            'CRITICAL': 0, 'HIGH': 0, 'MEDIUM': 0, 'LOW': 0, 'NORMAL': 0
        }
        for h in history:
            level = h.get('urgency_level', 'NORMAL')
            if level in urgency_counts:
                urgency_counts[level] += 1

        return {
            'total_adjustments': total,
            'soc_corrections': soc_corrections,
            'load_adjustments': load_adjustments,
            'no_adjustments': no_adjustments,
            'avg_power_adjustment': round(avg_adjustment, 2),
            'critical_count': urgency_counts['CRITICAL'],
            'high_count': urgency_counts['HIGH'],
            'medium_count': urgency_counts['MEDIUM'],
            'low_count': urgency_counts['LOW'],
            'normal_count': urgency_counts['NORMAL']
        }
