"""数据模型定义"""
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from datetime import datetime, time


class PriceForecastDTO(BaseModel):
    """电价预测数据"""
    hour_index: int = Field(..., description="时段索引(0-23)")
    forecast_price: float = Field(..., description="预测电价(元/kWh)")
    period_type: Optional[str] = Field(None, description="时段类型: PEAK/VALLEY/FLAT")


class LoadForecastDTO(BaseModel):
    """负荷预测数据"""
    hour_index: int = Field(..., description="时段索引(0-23)")
    forecast_load: float = Field(..., description="预测负荷(kW)")
    forecast_pv: Optional[float] = Field(0.0, description="预测光伏出力(kW)")


class StrategyConfigDTO(BaseModel):
    """策略配置"""
    id: Optional[int] = None
    strategy_code: str = Field(..., description="策略编码")
    strategy_name: Optional[str] = None
    strategy_type: Optional[str] = None

    arbitrage_weight: float = 0.5
    lifespan_weight: float = 0.3
    demand_weight: float = 0.2

    max_charge_rate: float = 0.5
    max_discharge_rate: float = 0.5
    min_soc: float = 20.0
    max_soc: float = 90.0
    max_daily_cycles: float = 1.0
    max_depth_of_discharge: float = 70.0
    demand_threshold_ratio: float = 0.9

    demand_control_enabled: bool = True
    rolling_optimization_enabled: bool = True
    rolling_interval_minutes: int = 15
    look_ahead_hours: int = 24

    battery_sn: Optional[str] = None
    transformer_code: Optional[str] = None


class DispatchPlanHourDTO(BaseModel):
    """调度计划时段数据"""
    hour_index: int = Field(..., description="时段索引(0-23)")
    start_time: Optional[time] = None
    end_time: Optional[time] = None
    power: float = Field(..., description="充放电功率(kW)，正值充电，负值放电")
    energy: Optional[float] = None
    expected_soc: float = Field(..., description="预期SOC(%)")
    price: Optional[float] = None
    forecast_load: Optional[float] = None
    forecast_pv: Optional[float] = None
    expected_demand: Optional[float] = None
    action_type: Optional[str] = None
    revenue: Optional[float] = None
    degradation_cost: Optional[float] = None
    demand_saving: Optional[float] = None
    charge_rate: Optional[float] = None
    objective_score: Optional[float] = None


class RollingOptimizationRequest(BaseModel):
    """滚动优化请求"""
    strategy_code: str = Field(..., description="策略编码")
    plan_date: str = Field(..., description="计划日期(YYYY-MM-DD)")
    start_hour: int = Field(..., description="开始时段(0-23)")
    initial_soc: float = Field(..., description="初始SOC(%)")
    current_load: Optional[float] = None
    current_pv: Optional[float] = None
    current_price: Optional[float] = None
    price_forecast: List[PriceForecastDTO] = Field(..., description="电价预测数据")
    load_forecast: List[LoadForecastDTO] = Field(..., description="负荷预测数据")
    strategy_config: StrategyConfigDTO = Field(..., description="策略配置")


class RealTimeAdjustRequest(BaseModel):
    """实时调整请求"""
    strategy_code: str = Field(..., description="策略编码")
    battery_sn: Optional[str] = None
    current_soc: float = Field(..., description="当前SOC(%)")
    expected_soc: float = Field(..., description="预期SOC(%)")
    current_load: float = Field(..., description="当前负荷(kW)")
    forecast_load: float = Field(..., description="预测负荷(kW)")
    current_pv: Optional[float] = 0.0
    current_price: Optional[float] = None
    planned_power: float = Field(..., description="计划充放电功率(kW)")
    soc_deviation_threshold: Optional[float] = 5.0
    load_sudden_change_threshold: Optional[float] = 20.0
    strategy_config: StrategyConfigDTO = Field(..., description="策略配置")


class OptimizationResult(BaseModel):
    """优化结果"""
    success: bool = True
    message: str = "优化完成"
    plan_hours: List[DispatchPlanHourDTO] = Field(default_factory=list)
    expected_revenue: Optional[float] = None
    expected_degradation: Optional[float] = None
    expected_demand_saving: Optional[float] = None
    total_objective_score: Optional[float] = None
    arbitrage_score: Optional[float] = None
    lifespan_score: Optional[float] = None
    demand_score: Optional[float] = None


class RealTimeAdjustResult(BaseModel):
    """实时调整结果"""
    success: bool = True
    message: str = "调整完成"
    adjusted_power: float = Field(..., description="调整后的充放电功率(kW)，正值放电，负值充电")
    adjustment_reason: str = Field(..., description="调整原因")
    adjustment_type: str = Field(..., description="调整类型: SOC_CORRECTION/LOAD_ADJUST/NONE")
    original_power: float = Field(..., description="原始计划功率(kW)")
    expected_soc: Optional[float] = None
    urgency_level: Optional[str] = None
