"""EMS 储能优化求解服务 - FastAPI 主程序

提供的API接口：
1. POST /api/optimizer/rolling-optimize - 日内滚动优化
2. POST /api/optimizer/real-time-adjust - 实时调整
3. GET  /api/optimizer/health - 健康检查
4. GET  /api/optimizer/status - 服务状态
5. GET  /api/optimizer/history/optimization - 优化历史
6. GET  /api/optimizer/history/adjustment - 调整历史
7. GET  /api/optimizer/statistics/adjustment - 调整统计
"""

import os
import sys
import time
import logging
import signal
from typing import Dict, Any, Optional, List
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src.config import load_config, AppConfig
from src.models import (
    RollingOptimizationRequest, OptimizationResult,
    RealTimeAdjustRequest, RealTimeAdjustResult
)
from src.rolling_optimizer import RollingOptimizationService
from src.real_time_adjuster import RealTimeAdjustService

load_dotenv()

config = load_config()

LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')
LOG_FILE = os.getenv('LOG_FILE', './logs/optimizer.log')

os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE, encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)

logger = logging.getLogger(__name__)

rolling_optimizer: Optional[RollingOptimizationService] = None
real_time_adjuster: Optional[RealTimeAdjustService] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    global rolling_optimizer, real_time_adjuster

    logger.info("启动优化求解服务...")

    rolling_optimizer = RollingOptimizationService(config.optimizer)
    real_time_adjuster = RealTimeAdjustService(config.optimizer)

    logger.info("优化求解服务启动成功")

    yield

    logger.info("关闭优化求解服务...")
    logger.info("优化求解服务已关闭")


app = FastAPI(
    title="EMS 储能优化求解服务",
    description="基于线性规划的储能充放电优化求解服务，支持日内滚动优化和实时调整",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/optimizer/health", summary="健康检查")
async def health_check() -> Dict[str, Any]:
    """服务健康检查接口"""
    return {
        "status": "healthy",
        "service": "ems-optimizer",
        "version": "1.0.0",
        "timestamp": time.time()
    }


@app.get("/api/optimizer/status", summary="服务状态")
async def get_status() -> Dict[str, Any]:
    """获取服务运行状态"""
    return {
        "status": "running",
        "optimizer_config": {
            "battery_capacity": config.optimizer.battery_capacity,
            "max_charge_rate": config.optimizer.max_charge_rate,
            "max_discharge_rate": config.optimizer.max_discharge_rate,
            "min_soc": config.optimizer.min_soc,
            "max_soc": config.optimizer.max_soc,
            "rolling_interval_minutes": config.optimizer.rolling_interval_minutes
        },
        "optimization_history_count": len(rolling_optimizer.optimization_history) if rolling_optimizer else 0,
        "adjustment_history_count": len(real_time_adjuster.adjustment_history) if real_time_adjuster else 0
    }


@app.post(
    "/api/optimizer/rolling-optimize",
    summary="日内滚动优化",
    response_model=OptimizationResult
)
async def rolling_optimize(request: RollingOptimizationRequest) -> OptimizationResult:
    """
    执行日内滚动优化

    基于最新的负荷、光伏、电价预测数据，使用线性规划算法
    重新优化剩余时段的充放电计划。

    **核心功能：**
    - 支持每15分钟/每小时滚动优化
    - 仅优化start_hour及以后的时段，保持历史时段不变
    - 考虑SOC约束、功率约束、效率约束
    - 多目标优化：套利收益 + 寿命保护 + 需量控制

    **功率符号约定：**
    - 正值：充电 (kW)
    - 负值：放电 (kW)

    Args:
        request: 滚动优化请求，包含预测数据和策略配置

    Returns:
        优化结果，包含24小时充放电计划
    """
    if not rolling_optimizer:
        raise HTTPException(status_code=503, detail="优化服务未初始化")

    logger.info(
        f"收到滚动优化请求 - 策略: {request.strategy_code}, "
        f"日期: {request.plan_date}, 开始时段: {request.start_hour}"
    )

    result = rolling_optimizer.optimize(request)

    if not result.success:
        logger.error(f"滚动优化失败: {result.message}")
        raise HTTPException(status_code=400, detail=result.message)

    return result


@app.post(
    "/api/optimizer/real-time-adjust",
    summary="实时调整",
    response_model=RealTimeAdjustResult
)
async def real_time_adjust(request: RealTimeAdjustRequest) -> RealTimeAdjustResult:
    """
    执行实时调整

    根据SOC偏差或负荷突变，动态修正充放电功率指令。

    **调整触发条件：**
    1. SOC偏差超过阈值（默认±5%）
    2. 负荷突变超过阈值（默认±20%）

    **调整策略：**
    - SOC偏差修正：根据偏差程度调整25%-100%的功率
    - 负荷突变响应：根据突变幅度比例调整功率

    **优先级：**
    SOC修正 > 负荷调整 > 计划执行

    **功率符号约定：**
    - 正值：放电 (kW) - 注意与滚动优化相反！
    - 负值：充电 (kW)

    Args:
        request: 实时调整请求，包含当前运行数据

    Returns:
        调整结果，包含调整后的功率和调整原因
    """
    if not real_time_adjuster:
        raise HTTPException(status_code=503, detail="调整服务未初始化")

    logger.info(
        f"收到实时调整请求 - 策略: {request.strategy_code}, "
        f"SOC偏差: {request.current_soc - request.expected_soc:+.1f}%, "
        f"计划功率: {request.planned_power:.2f}kW"
    )

    result = real_time_adjuster.adjust(request)

    if not result.success:
        logger.error(f"实时调整失败: {result.message}")
        raise HTTPException(status_code=400, detail=result.message)

    return result


@app.get("/api/optimizer/history/optimization", summary="优化历史")
async def get_optimization_history(
    strategy_code: Optional[str] = Query(None, description="策略编码"),
    limit: int = Query(100, description="返回记录数", ge=1, le=1000)
) -> Dict[str, Any]:
    """获取滚动优化历史记录"""
    if not rolling_optimizer:
        raise HTTPException(status_code=503, detail="优化服务未初始化")

    history = rolling_optimizer.get_optimization_history(strategy_code, limit)

    return {
        "count": len(history),
        "strategy_code": strategy_code,
        "history": history
    }


@app.get("/api/optimizer/history/adjustment", summary="调整历史")
async def get_adjustment_history(
    strategy_code: Optional[str] = Query(None, description="策略编码"),
    adjustment_type: Optional[str] = Query(None, description="调整类型: SOC_CORRECTION/LOAD_ADJUST/NONE"),
    limit: int = Query(100, description="返回记录数", ge=1, le=1000)
) -> Dict[str, Any]:
    """获取实时调整历史记录"""
    if not real_time_adjuster:
        raise HTTPException(status_code=503, detail="调整服务未初始化")

    history = real_time_adjuster.get_adjustment_history(
        strategy_code, adjustment_type, limit
    )

    return {
        "count": len(history),
        "strategy_code": strategy_code,
        "adjustment_type": adjustment_type,
        "history": history
    }


@app.get("/api/optimizer/statistics/adjustment", summary="调整统计")
async def get_adjustment_statistics(
    strategy_code: Optional[str] = Query(None, description="策略编码")
) -> Dict[str, Any]:
    """获取实时调整统计信息"""
    if not real_time_adjuster:
        raise HTTPException(status_code=503, detail="调整服务未初始化")

    stats = real_time_adjuster.get_adjustment_statistics(strategy_code)

    return {
        "strategy_code": strategy_code,
        "statistics": stats
    }


@app.post("/api/optimizer/rolling-optimize/15min", summary="15分钟粒度滚动优化")
async def rolling_optimize_15min(
    request: Dict[str, Any]
) -> Dict[str, Any]:
    """
    15分钟粒度的滚动优化接口

    Args:
        request: 包含 price_forecast(96点), load_forecast(96点),
                pv_forecast(96点), initial_soc, strategy_config, start_interval

    Returns:
        包含 powers(96点功率) 和 socs(96点SOC) 的结果
    """
    if not rolling_optimizer:
        raise HTTPException(status_code=503, detail="优化服务未初始化")

    try:
        from src.models import StrategyConfigDTO

        price_forecast = request.get('price_forecast', [])
        load_forecast = request.get('load_forecast', [])
        pv_forecast = request.get('pv_forecast', [])
        initial_soc = request.get('initial_soc', 50.0)
        start_interval = request.get('start_interval', 0)
        strategy_config_dict = request.get('strategy_config', {})

        strategy_config = StrategyConfigDTO(**strategy_config_dict)

        powers, socs = rolling_optimizer.optimize_15min(
            price_forecast=price_forecast,
            load_forecast=load_forecast,
            pv_forecast=pv_forecast,
            initial_soc=initial_soc,
            strategy_config=strategy_config,
            start_interval=start_interval
        )

        return {
            "success": True,
            "powers": powers,
            "socs": socs,
            "interval_minutes": 15
        }

    except Exception as e:
        logger.error(f"15分钟滚动优化失败: {e}", exc_info=True)
        raise HTTPException(status_code=400, detail=str(e))


if __name__ == "__main__":
    import uvicorn

    host = os.getenv('SERVER_HOST', '0.0.0.0')
    port = int(os.getenv('SERVER_PORT', '8001'))

    logger.info(f"启动优化求解服务 - {host}:{port}")

    uvicorn.run(
        "src.main:app",
        host=host,
        port=port,
        reload=False,
        log_level=LOG_LEVEL.lower()
    )
