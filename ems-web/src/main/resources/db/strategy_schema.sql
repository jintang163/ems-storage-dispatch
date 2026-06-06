-- ==================== 日内与实时策略模块 ====================

-- 策略配置表
CREATE TABLE IF NOT EXISTS strategy_config (
    id BIGSERIAL PRIMARY KEY,
    strategy_name VARCHAR(100) NOT NULL,
    strategy_type VARCHAR(50) NOT NULL,
    strategy_code VARCHAR(50) NOT NULL UNIQUE,
    arbitrage_weight DECIMAL(5,2) DEFAULT 0.50,
    lifespan_weight DECIMAL(5,2) DEFAULT 0.30,
    demand_weight DECIMAL(5,2) DEFAULT 0.20,
    max_charge_rate DECIMAL(5,2) DEFAULT 0.50,
    max_discharge_rate DECIMAL(5,2) DEFAULT 0.50,
    min_soc DECIMAL(5,2) DEFAULT 20.00,
    max_soc DECIMAL(5,2) DEFAULT 90.00,
    max_daily_cycles DECIMAL(5,2) DEFAULT 1.00,
    max_depth_of_discharge DECIMAL(5,2) DEFAULT 70.00,
    demand_threshold_ratio DECIMAL(5,2) DEFAULT 0.90,
    price_forecast_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    peak_valley_arbitrage_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    peak_shaving_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    valley_filling_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    demand_control_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    battery_sn VARCHAR(50),
    transformer_code VARCHAR(50),
    schedule_interval_minutes INT NOT NULL DEFAULT 60,
    rolling_optimization_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    rolling_interval_minutes INT DEFAULT 15,
    look_ahead_hours INT DEFAULT 24,
    priority INT DEFAULT 5,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_strategy BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_strategy_type (strategy_type),
    INDEX idx_strategy_enabled (enabled)
);

-- 调度计划表
CREATE TABLE IF NOT EXISTS dispatch_plan (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    strategy_code VARCHAR(50) NOT NULL,
    plan_date DATE NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    battery_sn VARCHAR(50),
    transformer_code VARCHAR(50),
    initial_soc DECIMAL(5,2),
    expected_revenue DECIMAL(12,4),
    expected_degradation DECIMAL(10,8),
    expected_demand_saving DECIMAL(12,4),
    total_objective_score DECIMAL(10,4),
    arbitrage_score DECIMAL(10,4),
    lifespan_score DECIMAL(10,4),
    demand_score DECIMAL(10,4),
    generated_at TIMESTAMP,
    executed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_by VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_date (plan_date),
    INDEX idx_plan_status (status),
    INDEX idx_plan_strategy (strategy_id)
);

-- 调度计划时段表
CREATE TABLE IF NOT EXISTS dispatch_plan_hour (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES dispatch_plan(id) ON DELETE CASCADE,
    hour_index INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    period_type VARCHAR(20),
    price DECIMAL(10,4),
    power DECIMAL(10,2) NOT NULL,
    energy DECIMAL(10,4),
    expected_soc DECIMAL(5,2),
    charge_rate DECIMAL(5,2),
    depth_of_discharge DECIMAL(5,2),
    action_type VARCHAR(20),
    forecast_load DECIMAL(10,2),
    forecast_pv DECIMAL(10,2),
    expected_demand DECIMAL(10,2),
    demand_control_required BOOLEAN DEFAULT FALSE,
    revenue DECIMAL(12,4),
    degradation_cost DECIMAL(12,4),
    demand_saving DECIMAL(12,4),
    objective_score DECIMAL(10,4),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_hour_plan (plan_id),
    INDEX idx_plan_hour_start (start_time)
);

-- 策略执行日志表
CREATE TABLE IF NOT EXISTS strategy_execution_log (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    strategy_code VARCHAR(50) NOT NULL,
    execution_time TIMESTAMP NOT NULL,
    execution_type VARCHAR(50),
    current_soc DECIMAL(5,2),
    current_load DECIMAL(10,2),
    current_pv DECIMAL(10,2),
    current_demand DECIMAL(10,2),
    predicted_demand DECIMAL(10,2),
    demand_threshold DECIMAL(10,2),
    current_price DECIMAL(10,4),
    period_type VARCHAR(20),
    action_taken VARCHAR(50),
    target_power DECIMAL(10,2),
    actual_power DECIMAL(10,2),
    revenue DECIMAL(12,4),
    degradation_cost DECIMAL(12,4),
    demand_saving DECIMAL(12,4),
    battery_temperature DECIMAL(5,2),
    battery_health DECIMAL(5,4),
    status VARCHAR(20) NOT NULL DEFAULT 'success',
    error_message VARCHAR(500),
    remark VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exec_strategy (strategy_id),
    INDEX idx_exec_time (execution_time),
    INDEX idx_exec_status (status)
);

-- 电价预测表
CREATE TABLE IF NOT EXISTS price_forecast (
    id BIGSERIAL PRIMARY KEY,
    forecast_date DATE NOT NULL,
    hour_index INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    forecast_price DECIMAL(10,4) NOT NULL,
    actual_price DECIMAL(10,4),
    price_deviation DECIMAL(10,4),
    deviation_percentage DECIMAL(10,4),
    period_type VARCHAR(20),
    forecast_source VARCHAR(50),
    forecast_model VARCHAR(50),
    confidence_level DECIMAL(5,2),
    is_peak BOOLEAN NOT NULL DEFAULT FALSE,
    is_valley BOOLEAN NOT NULL DEFAULT FALSE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_forecast_date (forecast_date),
    INDEX idx_forecast_source (forecast_source)
);

-- 负荷预测表
CREATE TABLE IF NOT EXISTS load_forecast (
    id BIGSERIAL PRIMARY KEY,
    forecast_date DATE NOT NULL,
    hour_index INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    forecast_load DECIMAL(10,2) NOT NULL,
    forecast_pv DECIMAL(10,2),
    forecast_grid DECIMAL(10,2),
    actual_load DECIMAL(10,2),
    actual_pv DECIMAL(10,2),
    load_deviation DECIMAL(10,2),
    deviation_percentage DECIMAL(10,4),
    forecast_type VARCHAR(20),
    forecast_source VARCHAR(50),
    forecast_model VARCHAR(50),
    confidence_level DECIMAL(5,2),
    is_peak_hour BOOLEAN NOT NULL DEFAULT FALSE,
    transformer_code VARCHAR(50),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_load_forecast_date (forecast_date),
    INDEX idx_load_forecast_type (forecast_type)
);

-- ==================== 初始化数据 ====================

-- 插入默认策略配置
INSERT INTO strategy_config (
    strategy_name, strategy_type, strategy_code,
    arbitrage_weight, lifespan_weight, demand_weight,
    max_charge_rate, max_discharge_rate, min_soc, max_soc,
    max_daily_cycles, max_depth_of_discharge, demand_threshold_ratio,
    price_forecast_enabled, peak_valley_arbitrage_enabled,
    peak_shaving_enabled, valley_filling_enabled, demand_control_enabled,
    schedule_interval_minutes, rolling_optimization_enabled,
    rolling_interval_minutes, look_ahead_hours, priority,
    enabled, default_strategy, description
) VALUES (
    '综合优化策略', 'MULTI_OBJECTIVE', 'INTEGRATED_OPTIMIZATION',
    0.50, 0.30, 0.20,
    0.50, 0.50, 20.00, 90.00,
    1.00, 70.00, 0.90,
    TRUE, TRUE, TRUE, TRUE, TRUE,
    60, TRUE, 15, 24, 5,
    TRUE, TRUE, '默认综合优化策略，平衡套利收益、电池寿命和需量控制三个目标'
), (
    '收益优先策略', 'ARBITRAGE_FOCUSED', 'PROFIT_FIRST',
    0.70, 0.15, 0.15,
    0.80, 0.80, 15.00, 95.00,
    1.50, 80.00, 0.95,
    TRUE, TRUE, TRUE, TRUE, TRUE,
    60, TRUE, 15, 24, 3,
    TRUE, FALSE, '收益优先策略，最大化峰谷套利收益，适度放宽寿命约束'
), (
    '寿命优先策略', 'LIFESPAN_FOCUSED', 'LIFESPAN_FIRST',
    0.20, 0.70, 0.10,
    0.30, 0.30, 30.00, 80.00,
    0.50, 50.00, 0.85,
    TRUE, TRUE, TRUE, TRUE, TRUE,
    60, TRUE, 15, 24, 3,
    TRUE, FALSE, '寿命优先策略，最小化电池衰减，延长电池使用寿命'
), (
    '需量控制优先策略', 'DEMAND_FOCUSED', 'DEMAND_FIRST',
    0.15, 0.15, 0.70,
    0.60, 0.60, 25.00, 90.00,
    1.00, 70.00, 0.80,
    TRUE, TRUE, TRUE, TRUE, TRUE,
    15, TRUE, 5, 12, 10,
    TRUE, FALSE, '需量控制优先策略，严格控制最大需量，降低需量电费'
);
