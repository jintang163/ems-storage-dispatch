-- ==================== 策略仿真模块 ====================

-- 仿真任务表
CREATE TABLE IF NOT EXISTS simulation (
    id BIGSERIAL PRIMARY KEY,
    simulation_name VARCHAR(200) NOT NULL,
    simulation_date DATE NOT NULL,
    strategy_code VARCHAR(50) NOT NULL,
    strategy_name VARCHAR(100),
    strategy_type VARCHAR(50),
    battery_sn VARCHAR(50),
    transformer_code VARCHAR(50),
    initial_soc DECIMAL(5,2),
    battery_capacity DECIMAL(10,2) NOT NULL,
    battery_power DECIMAL(10,2) NOT NULL,
    charge_efficiency DECIMAL(5,4),
    discharge_efficiency DECIMAL(5,4),
    min_soc DECIMAL(5,2),
    max_soc DECIMAL(5,2),
    demand_threshold DECIMAL(10,2),
    demand_price DECIMAL(10,4),
    degradation_model_id BIGINT,
    data_source VARCHAR(50),
    data_start_date DATE,
    data_end_date DATE,
    total_revenue DECIMAL(15,4),
    total_arbitrage_revenue DECIMAL(15,4),
    total_demand_saving DECIMAL(15,4),
    total_degradation_cost DECIMAL(15,4),
    net_revenue DECIMAL(15,4),
    total_charge_energy DECIMAL(12,4),
    total_discharge_energy DECIMAL(12,4),
    cycle_count DECIMAL(10,4),
    avg_depth_of_discharge DECIMAL(5,2),
    max_demand DECIMAL(10,2),
    min_demand DECIMAL(10,2),
    avg_demand DECIMAL(10,2),
    demand_peak_reduction DECIMAL(10,2),
    soh_start DECIMAL(5,4),
    soh_end DECIMAL(5,4),
    soh_degradation DECIMAL(10,8),
    estimated_remaining_cycles INT,
    estimated_remaining_lifespan_years DECIMAL(5,2),
    self_consumption_rate DECIMAL(5,2),
    self_sufficiency_rate DECIMAL(5,2),
    round_trip_efficiency DECIMAL(5,2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message VARCHAR(1000),
    remark VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_simulation_strategy (strategy_code),
    INDEX idx_simulation_status (status),
    INDEX idx_simulation_date (simulation_date)
);

-- 仿真时段数据表
CREATE TABLE IF NOT EXISTS simulation_hour_data (
    id BIGSERIAL PRIMARY KEY,
    simulation_id BIGINT NOT NULL REFERENCES simulation(id) ON DELETE CASCADE,
    hour_index INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    period_type VARCHAR(20),
    price DECIMAL(10,4),
    load_power DECIMAL(10,2),
    pv_power DECIMAL(10,2),
    grid_power DECIMAL(10,2),
    battery_power DECIMAL(10,2),
    battery_energy DECIMAL(10,4),
    expected_soc DECIMAL(5,2),
    charge_rate DECIMAL(5,2),
    depth_of_discharge DECIMAL(5,2),
    action_type VARCHAR(20),
    demand DECIMAL(10,2),
    demand_control_required BOOLEAN DEFAULT FALSE,
    revenue DECIMAL(12,4),
    arbitrage_revenue DECIMAL(12,4),
    demand_saving DECIMAL(12,4),
    degradation_cost DECIMAL(12,4),
    net_profit DECIMAL(12,4),
    cumulative_revenue DECIMAL(15,4),
    soh DECIMAL(5,4),
    battery_temperature DECIMAL(5,2),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_simulation_hour_simulation (simulation_id),
    INDEX idx_simulation_hour_start (start_time)
);

-- ==================== 初始化数据 ====================

-- 插入示例仿真配置
INSERT INTO simulation (
    simulation_name, simulation_date, strategy_code, strategy_name, strategy_type,
    battery_capacity, battery_power, charge_efficiency, discharge_efficiency,
    min_soc, max_soc, initial_soc, demand_threshold, demand_price,
    data_source, status, remark
) VALUES (
    '示例-纯套利策略仿真', CURRENT_DATE, 'PURE_ARBITRAGE', '纯套利策略', 'PURE_ARBITRAGE',
    500.00, 250.00, 0.9500, 0.9500,
    20.00, 90.00, 50.00, 800.00, 30.00,
    'SAMPLE', 'PENDING', '纯套利策略示例：低谷充电，高峰放电，最大化峰谷价差收益'
), (
    '示例-削峰填谷策略仿真', CURRENT_DATE, 'PEAK_VALLEY', '削峰填谷策略', 'PEAK_VALLEY',
    500.00, 250.00, 0.9500, 0.9500,
    20.00, 90.00, 50.00, 800.00, 30.00,
    'SAMPLE', 'PENDING', '削峰填谷策略示例：平滑负荷曲线，降低电网压力'
), (
    '示例-需量优先策略仿真', CURRENT_DATE, 'DEMAND_FIRST', '需量优先策略', 'DEMAND_FIRST',
    500.00, 250.00, 0.9500, 0.9500,
    20.00, 90.00, 50.00, 700.00, 30.00,
    'SAMPLE', 'PENDING', '需量优先策略示例：严格控制最大需量，降低需量电费'
);
