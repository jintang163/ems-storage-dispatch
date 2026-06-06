"""日内滚动优化调度服务

核心功能：
1. 日内滚动优化：每15分钟/每小时基于最新数据重新优化
2. 支持多时间粒度优化：15分钟、30分钟、1小时
3. 滚动窗口优化：仅优化未来时段，保持历史时段不变
"""

import logging
from typing import List, Dict, Optional, Tuple
from datetime import datetime

from .models import (
    RollingOptimizationRequest, OptimizationResult,
    DispatchPlanHourDTO, PriceForecastDTO, LoadForecastDTO
)
from .linear_programming import LinearProgrammingOptimizer
from .config import OptimizerConfig

logger = logging.getLogger(__name__)


class RollingOptimizationService:
    """滚动优化调度服务"""

    def __init__(self, optimizer_config: Optional[OptimizerConfig] = None):
        self.config = optimizer_config or OptimizerConfig()
        self.lp_optimizer = LinearProgrammingOptimizer(self.config)
        self.optimization_history: List[Dict] = []

    def optimize(self, request: RollingOptimizationRequest) -> OptimizationResult:
        """
        执行滚动优化

        Args:
            request: 滚动优化请求

        Returns:
            优化结果
        """
        logger.info(
            f"执行滚动优化 - 策略: {request.strategy_code}, "
            f"日期: {request.plan_date}, 开始时段: {request.start_hour}"
        )

        try:
            strategy_config = request.strategy_config
            start_hour = request.start_hour

            self._validate_request(request)

            price_forecast = self._prepare_price_forecast(request)
            load_forecast = self._prepare_load_forecast(request)

            plan_hours, scores = self.lp_optimizer.optimize(
                price_forecast=price_forecast,
                load_forecast=load_forecast,
                initial_soc=request.initial_soc,
                strategy_config=strategy_config,
                start_hour=start_hour
            )

            self._enrich_plan_hours(plan_hours, request, start_hour)

            result = OptimizationResult(
                success=True,
                message="滚动优化完成",
                plan_hours=plan_hours,
                expected_revenue=scores.get('total_revenue'),
                expected_degradation=scores.get('total_degradation'),
                expected_demand_saving=scores.get('total_demand_saving'),
                total_objective_score=scores.get('total_score'),
                arbitrage_score=scores.get('arbitrage_score'),
                lifespan_score=scores.get('lifespan_score'),
                demand_score=scores.get('demand_score')
            )

            self._record_optimization(request, result, scores)

            logger.info(
                f"滚动优化完成 - 综合得分: {scores.get('total_score', 0):.4f}, "
                f"预期收益: {scores.get('total_revenue', 0):.2f}元"
            )

            return result

        except Exception as e:
            logger.error(f"滚动优化失败: {e}", exc_info=True)
            return OptimizationResult(
                success=False,
                message=f"优化失败: {str(e)}",
                plan_hours=[]
            )

    def _validate_request(self, request: RollingOptimizationRequest) -> None:
        """验证请求参数"""
        if request.start_hour < 0 or request.start_hour >= 24:
            raise ValueError(f"开始时段必须在0-23之间，当前值: {request.start_hour}")

        if request.initial_soc < 0 or request.initial_soc > 100:
            raise ValueError(f"初始SOC必须在0-100之间，当前值: {request.initial_soc}")

        if not request.price_forecast:
            raise ValueError("电价预测数据不能为空")

        if not request.load_forecast:
            raise ValueError("负荷预测数据不能为空")

        price_hours = {pf.hour_index for pf in request.price_forecast}
        load_hours = {lf.hour_index for lf in request.load_forecast}
        all_hours = set(range(24))

        missing_price = all_hours - price_hours
        if missing_price:
            logger.warning(f"电价预测缺少时段: {sorted(missing_price)}，将使用默认值填充")

        missing_load = all_hours - load_hours
        if missing_load:
            logger.warning(f"负荷预测缺少时段: {sorted(missing_load)}，将使用默认值填充")

    def _prepare_price_forecast(
        self, request: RollingOptimizationRequest
    ) -> List[PriceForecastDTO]:
        """准备电价预测数据，填充缺失时段"""
        price_map = {pf.hour_index: pf for pf in request.price_forecast}
        result = []

        for hour in range(24):
            if hour in price_map:
                result.append(price_map[hour])
            else:
                result.append(PriceForecastDTO(
                    hour_index=hour,
                    forecast_price=0.55,
                    period_type='FLAT'
                ))

        return result

    def _prepare_load_forecast(
        self, request: RollingOptimizationRequest
    ) -> List[LoadForecastDTO]:
        """准备负荷预测数据，填充缺失时段"""
        load_map = {lf.hour_index: lf for lf in request.load_forecast}
        result = []

        for hour in range(24):
            if hour in load_map:
                result.append(load_map[hour])
            else:
                result.append(LoadForecastDTO(
                    hour_index=hour,
                    forecast_load=300.0,
                    forecast_pv=0.0
                ))

        return result

    def _enrich_plan_hours(
        self,
        plan_hours: List[DispatchPlanHourDTO],
        request: RollingOptimizationRequest,
        start_hour: int
    ) -> None:
        """丰富计划时段数据，设置开始和结束时间"""
        from datetime import time

        for hour in plan_hours:
            hour.start_time = time(hour.hour_index, 0)
            hour.end_time = time((hour.hour_index + 1) % 24, 0)

            if hour.hour_index < start_hour:
                if not hour.action_type:
                    hour.action_type = "HOLD"

    def _record_optimization(
        self,
        request: RollingOptimizationRequest,
        result: OptimizationResult,
        scores: Dict[str, float]
    ) -> None:
        """记录优化历史"""
        record = {
            'timestamp': datetime.now().isoformat(),
            'strategy_code': request.strategy_code,
            'plan_date': request.plan_date,
            'start_hour': request.start_hour,
            'initial_soc': request.initial_soc,
            'success': result.success,
            'message': result.message,
            'scores': scores
        }

        self.optimization_history.append(record)

        if len(self.optimization_history) > 1000:
            self.optimization_history = self.optimization_history[-1000:]

    def get_optimization_history(
        self, strategy_code: Optional[str] = None, limit: int = 100
    ) -> List[Dict]:
        """获取优化历史记录"""
        history = self.optimization_history

        if strategy_code:
            history = [
                h for h in history
                if h['strategy_code'] == strategy_code
            ]

        return history[-limit:]

    def optimize_15min(
        self,
        price_forecast: List[float],
        load_forecast: List[float],
        pv_forecast: List[float],
        initial_soc: float,
        strategy_config,
        start_interval: int = 0
    ) -> Tuple[List[float], List[float]]:
        """
        15分钟粒度优化

        Args:
            price_forecast: 96个时点的电价预测（24小时 × 4）
            load_forecast: 96个时点的负荷预测
            pv_forecast: 96个时点的光伏预测
            initial_soc: 初始SOC
            strategy_config: 策略配置
            start_interval: 开始优化的时点索引（0-95）

        Returns:
            元组 (功率列表, SOC列表)
        """
        n_intervals = 96
        intervals_to_optimize = n_intervals - start_interval

        battery_capacity = self.config.battery_capacity
        max_charge_power = battery_capacity * strategy_config.max_charge_rate
        max_discharge_power = battery_capacity * strategy_config.max_discharge_rate
        min_soc = strategy_config.min_soc
        max_soc = strategy_config.max_soc
        charge_efficiency = self.config.charge_efficiency
        discharge_efficiency = self.config.discharge_efficiency

        powers = [0.0] * n_intervals
        socs = [0.0] * n_intervals
        current_soc = initial_soc

        for i in range(n_intervals):
            if i < start_interval:
                socs[i] = current_soc
                continue

            price = price_forecast[i] if i < len(price_forecast) else 0.55
            load = load_forecast[i] if i < len(load_forecast) else 0.0
            pv = pv_forecast[i] if i < len(pv_forecast) else 0.0
            net_load = max(0.0, load - pv)

            avg_price = sum(price_forecast) / len(price_forecast) if price_forecast else 0.55

            if price < avg_price * 0.9 and current_soc < max_soc:
                energy_needed = (max_soc - current_soc) / 100.0 * battery_capacity
                max_power_from_soc = energy_needed * 4
                power = min(max_charge_power, max_power_from_soc)
            elif price > avg_price * 1.1 and current_soc > min_soc:
                energy_available = (current_soc - min_soc) / 100.0 * battery_capacity
                max_power_from_soc = energy_available * 4
                power = -min(max_discharge_power, max_power_from_soc)
            elif net_load > 0 and current_soc > min_soc:
                power = -min(max_discharge_power, net_load,
                             (current_soc - min_soc) / 100.0 * battery_capacity * 4)
            else:
                power = 0.0

            energy = abs(power) / 4.0
            if power > 0:
                soc_change = energy * charge_efficiency / battery_capacity * 100.0
            else:
                soc_change = -energy / (discharge_efficiency * battery_capacity) * 100.0

            current_soc = max(0.0, min(100.0, current_soc + soc_change))

            powers[i] = round(power, 2)
            socs[i] = round(current_soc, 2)

        return powers, socs

    def optimize_hourly(
        self,
        price_forecast: List[float],
        load_forecast: List[float],
        pv_forecast: List[float],
        initial_soc: float,
        strategy_config,
        start_hour: int = 0
    ) -> Tuple[List[float], List[float]]:
        """
        小时粒度优化

        Args:
            price_forecast: 24个小时的电价预测
            load_forecast: 24个小时的负荷预测
            pv_forecast: 24个小时的光伏预测
            initial_soc: 初始SOC
            strategy_config: 策略配置
            start_hour: 开始优化的小时索引（0-23）

        Returns:
            元组 (功率列表, SOC列表)
        """
        price_dto = [
            PriceForecastDTO(hour_index=i, forecast_price=price_forecast[i] if i < len(price_forecast) else 0.55)
            for i in range(24)
        ]
        load_dto = [
            LoadForecastDTO(
                hour_index=i,
                forecast_load=load_forecast[i] if i < len(load_forecast) else 0.0,
                forecast_pv=pv_forecast[i] if i < len(pv_forecast) else 0.0
            )
            for i in range(24)
        ]

        plan_hours, _ = self.lp_optimizer.optimize(
            price_forecast=price_dto,
            load_forecast=load_dto,
            initial_soc=initial_soc,
            strategy_config=strategy_config,
            start_hour=start_hour
        )

        powers = [h.power for h in plan_hours]
        socs = [h.expected_soc for h in plan_hours]

        return powers, socs
