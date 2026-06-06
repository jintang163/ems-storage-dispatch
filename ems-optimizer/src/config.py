"""配置管理模块"""
import os
from dataclasses import dataclass, field
from typing import Optional
from dotenv import load_dotenv

load_dotenv()


@dataclass
class OptimizerConfig:
    """优化算法配置"""
    battery_capacity: float = field(
        default_factory=lambda: float(os.getenv('DEFAULT_BATTERY_CAPACITY', '1000'))
    )
    charge_efficiency: float = field(
        default_factory=lambda: float(os.getenv('DEFAULT_CHARGE_EFFICIENCY', '0.95'))
    )
    discharge_efficiency: float = field(
        default_factory=lambda: float(os.getenv('DEFAULT_DISCHARGE_EFFICIENCY', '0.95'))
    )
    max_charge_rate: float = field(
        default_factory=lambda: float(os.getenv('DEFAULT_MAX_CHARGE_RATE', '0.5'))
    )
    max_discharge_rate: float = field(
        default_factory=lambda: float(os.getenv('DEFAULT_MAX_DISCHARGE_RATE', '0.5'))
    )
    min_soc: float = field(
        default_factory=lambda: float(os.getenv('DEFAULT_MIN_SOC', '20'))
    )
    max_soc: float = field(
        default_factory=lambda: float(os.getenv('DEFAULT_MAX_SOC', '90'))
    )
    max_daily_cycles: float = 1.0
    max_depth_of_discharge: float = 70.0
    demand_threshold_ratio: float = 0.9

    arbitrage_weight: float = 0.5
    lifespan_weight: float = 0.3
    demand_weight: float = 0.2

    demand_control_enabled: bool = True
    rolling_interval_minutes: int = 15
    look_ahead_hours: int = 24


@dataclass
class ServerConfig:
    """服务配置"""
    host: str = field(default_factory=lambda: os.getenv('SERVER_HOST', '0.0.0.0'))
    port: int = field(default_factory=lambda: int(os.getenv('SERVER_PORT', '8001')))
    log_level: str = field(default_factory=lambda: os.getenv('LOG_LEVEL', 'INFO'))
    log_file: str = field(default_factory=lambda: os.getenv('LOG_FILE', './logs/optimizer.log'))
    backend_api_url: str = field(
        default_factory=lambda: os.getenv('BACKEND_API_URL', 'http://localhost:8080/api')
    )


@dataclass
class AppConfig:
    """应用配置"""
    server: ServerConfig = field(default_factory=ServerConfig)
    optimizer: OptimizerConfig = field(default_factory=OptimizerConfig)


def load_config() -> AppConfig:
    """加载应用配置"""
    return AppConfig()
