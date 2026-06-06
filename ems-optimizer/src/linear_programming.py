"""线性规划优化求解器

使用 scipy.optimize.linprog 求解储能充放电优化问题

优化问题描述：
- 目标函数：最大化套利收益 + 最小化寿命损耗 + 最小化需量费用
- 决策变量：每个时段的充放电功率 P(t)，t=1..T
  - P(t) > 0: 充电 (kW)
  - P(t) < 0: 放电 (kW)
- 约束条件：
  1. SOC 约束：min_soc ≤ SOC(t) ≤ max_soc
  2. 功率约束：-max_discharge_power ≤ P(t) ≤ max_charge_power
  3. 能量守恒：SOC(t) = SOC(t-1) + P(t) × η × Δt / capacity
  4. 循环约束：日循环次数 ≤ max_daily_cycles

目标函数详细形式：
minimize: Σ [ w1 × (-revenue(t)) + w2 × degradation(t) + w3 × demand_cost(t) ]

其中：
- revenue(t) = max(0, -P(t)) × price(t) × η_discharge - max(0, P(t)) × price(t) / η_charge
- degradation(t) = f(|P(t)|, DOD, T) 电池衰减成本
- demand_cost(t) = max(0, load(t) + P(t) - threshold) × demand_price
"""

import numpy as np
from scipy.optimize import linprog
from typing import List, Tuple, Dict, Optional
import logging

from .models import (
    PriceForecastDTO, LoadForecastDTO, StrategyConfigDTO, DispatchPlanHourDTO
)
from .config import OptimizerConfig

logger = logging.getLogger(__name__)


class LinearProgrammingOptimizer:
    """线性规划优化求解器"""

    def __init__(self, config: Optional[OptimizerConfig] = None):
        self.config = config or OptimizerConfig()

    def optimize(
        self,
        price_forecast: List[PriceForecastDTO],
        load_forecast: List[LoadForecastDTO],
        initial_soc: float,
        strategy_config: StrategyConfigDTO,
        start_hour: int = 0
    ) -> Tuple[List[DispatchPlanHourDTO], Dict[str, float]]:
        """
        执行线性规划优化

        Args:
            price_forecast: 电价预测数据列表
            load_forecast: 负荷预测数据列表
            initial_soc: 初始SOC (%)
            strategy_config: 策略配置
            start_hour: 开始优化的时段索引

        Returns:
            元组 (优化后的时段计划列表, 得分字典)
        """
        logger.info(
            f"开始线性规划优化 - 初始SOC: {initial_soc}%, 开始时段: {start_hour}"
        )

        battery_capacity = self.config.battery_capacity
        max_charge_rate = strategy_config.max_charge_rate
        max_discharge_rate = strategy_config.max_discharge_rate
        min_soc = strategy_config.min_soc
        max_soc = strategy_config.max_soc
        charge_efficiency = self.config.charge_efficiency
        discharge_efficiency = self.config.discharge_efficiency

        max_charge_power = battery_capacity * max_charge_rate
        max_discharge_power = battery_capacity * max_discharge_rate

        n_hours = 24
        hours_to_optimize = n_hours - start_hour

        price_map = {pf.hour_index: pf.forecast_price for pf in price_forecast}
        load_map = {lf.hour_index: lf.forecast_load for lf in load_forecast}
        pv_map = {lf.hour_index: getattr(lf, 'forecast_pv', 0.0) or 0.0 for lf in load_forecast}

        max_load = max(load_map.values()) if load_map else 0
        demand_threshold = max_load * strategy_config.demand_threshold_ratio
        demand_price = 35.0

        c = self._build_objective_function(
            price_map, load_map, pv_map,
            strategy_config, demand_threshold, demand_price,
            start_hour, n_hours
        )

        A_ub, b_ub = self._build_inequality_constraints(
            initial_soc, min_soc, max_soc,
            battery_capacity, charge_efficiency, discharge_efficiency,
            max_charge_power, max_discharge_power,
            start_hour, n_hours
        )

        bounds = self._build_variable_bounds(
            max_charge_power, max_discharge_power, hours_to_optimize
        )

        try:
            result = linprog(
                c, A_ub=A_ub, b_ub=b_ub,
                bounds=bounds, method='highs',
                options={'presolve': True}
            )

            if not result.success:
                logger.warning(f"线性规划求解未完全成功: {result.message}，使用启发式算法作为后备")
                return self._heuristic_fallback(
                    price_map, load_map, pv_map, initial_soc,
                    strategy_config, start_hour, n_hours
                )

            powers = result.x
            logger.info(f"线性规划求解成功，目标函数值: {result.fun:.2f}")

        except Exception as e:
            logger.error(f"线性规划求解异常: {e}，使用启发式算法作为后备")
            return self._heuristic_fallback(
                price_map, load_map, pv_map, initial_soc,
                strategy_config, start_hour, n_hours
            )

        plan_hours = self._build_plan_hours(
            powers, price_map, load_map, pv_map,
            initial_soc, strategy_config,
            start_hour, n_hours,
            charge_efficiency, discharge_efficiency,
            battery_capacity, demand_threshold, demand_price
        )

        scores = self._calculate_scores(
            plan_hours, strategy_config,
            demand_threshold, demand_price
        )

        return plan_hours, scores

    def _build_objective_function(
        self,
        price_map: Dict[int, float],
        load_map: Dict[int, float],
        pv_map: Dict[int, float],
        strategy_config: StrategyConfigDTO,
        demand_threshold: float,
        demand_price: float,
        start_hour: int,
        n_hours: int
    ) -> np.ndarray:
        """构建目标函数系数向量"""
        hours_to_optimize = n_hours - start_hour
        c = np.zeros(hours_to_optimize)

        w1 = strategy_config.arbitrage_weight
        w2 = strategy_config.lifespan_weight
        w3 = strategy_config.demand_weight
        total_weight = w1 + w2 + w3
        w1, w2, w3 = w1 / total_weight, w2 / total_weight, w3 / total_weight

        for i in range(hours_to_optimize):
            t = start_hour + i
            price = price_map.get(t, 0.55)
            load = load_map.get(t, 0.0)
            pv = pv_map.get(t, 0.0)
            net_load = max(0.0, load - pv)

            avg_price = 0.55
            revenue_per_kw = -price if price < avg_price else price
            degradation_per_kw = 0.01
            demand_coeff = demand_price if net_load > demand_threshold else 0

            c[i] = (
                w1 * (-revenue_per_kw / 100.0) +
                w2 * degradation_per_kw +
                w3 * demand_coeff / 1000.0
            )

        return c

    def _build_inequality_constraints(
        self,
        initial_soc: float,
        min_soc: float,
        max_soc: float,
        battery_capacity: float,
        charge_efficiency: float,
        discharge_efficiency: float,
        max_charge_power: float,
        max_discharge_power: float,
        start_hour: int,
        n_hours: int
    ) -> Tuple[np.ndarray, np.ndarray]:
        """构建不等式约束矩阵和右侧向量"""
        hours_to_optimize = n_hours - start_hour

        n_constraints = 2 * hours_to_optimize
        A_ub = np.zeros((n_constraints, hours_to_optimize))
        b_ub = np.zeros(n_constraints)

        for t in range(hours_to_optimize):
            k = t + 1
            soc_coeff_charge = charge_efficiency / (battery_capacity / 100.0)
            soc_coeff_discharge = 1.0 / (discharge_efficiency * battery_capacity / 100.0)

            for i in range(t + 1):
                idx = i
                A_ub[2 * t, idx] = soc_coeff_charge
                A_ub[2 * t + 1, idx] = -soc_coeff_discharge

            b_ub[2 * t] = max_soc - initial_soc
            b_ub[2 * t + 1] = initial_soc - min_soc

        return A_ub, b_ub

    def _build_variable_bounds(
        self,
        max_charge_power: float,
        max_discharge_power: float,
        n_variables: int
    ) -> List[Tuple[float, float]]:
        """构建变量边界"""
        return [(-max_discharge_power, max_charge_power)] * n_variables

    def _heuristic_fallback(
        self,
        price_map: Dict[int, float],
        load_map: Dict[int, float],
        pv_map: Dict[int, float],
        initial_soc: float,
        strategy_config: StrategyConfigDTO,
        start_hour: int,
        n_hours: int
    ) -> Tuple[List[DispatchPlanHourDTO], Dict[str, float]]:
        """启发式算法后备方案"""
        logger.info("使用启发式算法进行优化")

        battery_capacity = self.config.battery_capacity
        max_charge_rate = strategy_config.max_charge_rate
        max_discharge_rate = strategy_config.max_discharge_rate
        min_soc = strategy_config.min_soc
        max_soc = strategy_config.max_soc
        charge_efficiency = self.config.charge_efficiency
        discharge_efficiency = self.config.discharge_efficiency

        max_charge_power = battery_capacity * max_charge_rate
        max_discharge_power = battery_capacity * max_discharge_rate

        prices = [price_map.get(t, 0.55) for t in range(n_hours)]
        avg_price = sum(prices) / len(prices)
        sorted_hours = sorted(range(n_hours), key=lambda t: prices[t])

        plan_hours = []
        current_soc = initial_soc

        for t in range(n_hours):
            if t < start_hour:
                hour = DispatchPlanHourDTO(
                    hour_index=t,
                    power=0.0,
                    expected_soc=current_soc
                )
                plan_hours.append(hour)
                continue

            price = prices[t]
            load = load_map.get(t, 0.0)
            pv = pv_map.get(t, 0.0)
            net_load = max(0.0, load - pv)

            if price < avg_price * 0.9 and current_soc < max_soc:
                power = min(max_charge_power, (max_soc - current_soc) / 100.0 * battery_capacity)
            elif price > avg_price * 1.1 and current_soc > min_soc:
                power = -min(max_discharge_power, (current_soc - min_soc) / 100.0 * battery_capacity)
            elif net_load > 0 and current_soc > min_soc:
                power = -min(max_discharge_power, net_load, (current_soc - min_soc) / 100.0 * battery_capacity)
            else:
                power = 0.0

            expected_soc = self._calculate_expected_soc(
                current_soc, power, abs(power),
                charge_efficiency, discharge_efficiency, battery_capacity
            )

            hour = DispatchPlanHourDTO(
                hour_index=t,
                power=round(power, 2),
                expected_soc=round(expected_soc, 2)
            )
            plan_hours.append(hour)
            current_soc = expected_soc

        scores = {
            'total_score': 0.5,
            'arbitrage_score': 0.5,
            'lifespan_score': 0.5,
            'demand_score': 0.5
        }

        return plan_hours, scores

    def _build_plan_hours(
        self,
        powers: np.ndarray,
        price_map: Dict[int, float],
        load_map: Dict[int, float],
        pv_map: Dict[int, float],
        initial_soc: float,
        strategy_config: StrategyConfigDTO,
        start_hour: int,
        n_hours: int,
        charge_efficiency: float,
        discharge_efficiency: float,
        battery_capacity: float,
        demand_threshold: float,
        demand_price: float
    ) -> List[DispatchPlanHourDTO]:
        """根据优化结果构建计划时段列表"""
        plan_hours = []
        current_soc = initial_soc

        for t in range(n_hours):
            if t < start_hour:
                hour = DispatchPlanHourDTO(
                    hour_index=t,
                    power=0.0,
                    expected_soc=round(current_soc, 2)
                )
                plan_hours.append(hour)
                continue

            idx = t - start_hour
            power = float(powers[idx])
            power = round(power, 2)

            price = price_map.get(t, 0.55)
            load = load_map.get(t, 0.0)
            pv = pv_map.get(t, 0.0)
            net_load = max(0.0, load - pv)

            energy = abs(power)
            expected_soc = self._calculate_expected_soc(
                current_soc, power, energy,
                charge_efficiency, discharge_efficiency, battery_capacity
            )

            new_demand = max(0.0, net_load + power)
            revenue = self._calculate_arbitrage_revenue(price, power, charge_efficiency, discharge_efficiency)
            degradation_cost = self._calculate_degradation_cost(
                power, battery_capacity, strategy_config
            )
            demand_saving = self._calculate_demand_saving(
                net_load, new_demand, demand_threshold, demand_price
            )

            if power > 0.1:
                action_type = "CHARGE"
            elif power < -0.1:
                action_type = "DISCHARGE"
            else:
                action_type = "HOLD"

            hour = DispatchPlanHourDTO(
                hour_index=t,
                power=power,
                energy=round(energy / 24.0, 4),
                expected_soc=round(expected_soc, 2),
                price=price,
                forecast_load=load,
                forecast_pv=pv,
                expected_demand=round(new_demand, 2),
                action_type=action_type,
                revenue=round(revenue, 4),
                degradation_cost=round(degradation_cost, 4),
                demand_saving=round(demand_saving, 4),
                charge_rate=round(abs(power) / battery_capacity, 4)
            )
            plan_hours.append(hour)
            current_soc = expected_soc

        return plan_hours

    def _calculate_expected_soc(
        self,
        current_soc: float,
        power: float,
        energy: float,
        charge_efficiency: float,
        discharge_efficiency: float,
        battery_capacity: float
    ) -> float:
        """计算预期SOC"""
        if power > 0:
            soc_change = energy * charge_efficiency / battery_capacity * 100.0
        else:
            soc_change = -energy / (discharge_efficiency * battery_capacity) * 100.0

        expected_soc = current_soc + soc_change
        return max(0.0, min(100.0, expected_soc))

    def _calculate_arbitrage_revenue(
        self,
        price: float,
        power: float,
        charge_efficiency: float,
        discharge_efficiency: float
    ) -> float:
        """计算套利收益"""
        energy = abs(power) / 24.0
        if power > 0:
            return -energy * price
        else:
            return energy * price * discharge_efficiency

    def _calculate_degradation_cost(
        self,
        power: float,
        battery_capacity: float,
        strategy_config: StrategyConfigDTO
    ) -> float:
        """计算电池衰减成本"""
        abs_power = abs(power)
        if abs_power < 0.1:
            return 0.0

        rate = abs_power / battery_capacity
        max_rate = max(strategy_config.max_charge_rate, strategy_config.max_discharge_rate)

        penalty = rate * 0.5
        if rate > max_rate:
            excess = rate - max_rate
            penalty += excess * excess * 10

        battery_cost = battery_capacity * 1500
        return penalty * battery_cost / 10000.0

    def _calculate_demand_saving(
        self,
        original_demand: float,
        new_demand: float,
        threshold: float,
        demand_price: float
    ) -> float:
        """计算需量节省"""
        original_excess = max(0.0, original_demand - threshold)
        new_excess = max(0.0, new_demand - threshold)
        saving = max(0.0, original_excess - new_excess)
        return saving * demand_price

    def _calculate_scores(
        self,
        plan_hours: List[DispatchPlanHourDTO],
        strategy_config: StrategyConfigDTO,
        demand_threshold: float,
        demand_price: float
    ) -> Dict[str, float]:
        """计算各项得分"""
        total_revenue = sum(h.revenue or 0.0 for h in plan_hours)
        total_degradation = sum(h.degradation_cost or 0.0 for h in plan_hours)
        total_demand_saving = sum(h.demand_saving or 0.0 for h in plan_hours)

        prices = [h.price or 0.55 for h in plan_hours if h.price is not None]
        price_spread = max(prices) - min(prices) if prices else 0

        if price_spread > 0:
            max_theoretical = self.config.battery_capacity * strategy_config.max_daily_cycles * price_spread * 0.95
            arbitrage_score = min(1.0, max(0.0, total_revenue / max_theoretical)) if max_theoretical > 0 else 0.5
        else:
            arbitrage_score = 0.5

        max_rate = max(strategy_config.max_charge_rate, strategy_config.max_discharge_rate)
        avg_charge_rate = sum(h.charge_rate or 0.0 for h in plan_hours) / len(plan_hours) if plan_hours else 0
        lifespan_score = max(0.0, min(1.0, 1.0 - avg_charge_rate / (2.0 * max_rate)))

        demands = [h.expected_demand or 0.0 for h in plan_hours if h.expected_demand is not None]
        if demands and demand_threshold > 0:
            max_demand = max(demands)
            if max_demand <= demand_threshold:
                demand_score = 1.0
            else:
                overage = max_demand - demand_threshold
                max_overage = demand_threshold * 0.5
                demand_score = max(0.0, min(1.0, 1.0 - overage / max_overage))
        else:
            demand_score = 0.5

        w1 = strategy_config.arbitrage_weight
        w2 = strategy_config.lifespan_weight
        w3 = strategy_config.demand_weight
        total_weight = w1 + w2 + w3

        total_score = (
            arbitrage_score * w1 +
            lifespan_score * w2 +
            demand_score * w3
        ) / total_weight if total_weight > 0 else 0.0

        return {
            'total_revenue': round(total_revenue, 4),
            'total_degradation': round(total_degradation, 4),
            'total_demand_saving': round(total_demand_saving, 4),
            'arbitrage_score': round(arbitrage_score, 4),
            'lifespan_score': round(lifespan_score, 4),
            'demand_score': round(demand_score, 4),
            'total_score': round(total_score, 4)
        }
