-- 设备类型表
CREATE TABLE IF NOT EXISTS device_type (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 设备表
CREATE TABLE IF NOT EXISTS device (
    id BIGSERIAL PRIMARY KEY,
    device_sn VARCHAR(100) NOT NULL UNIQUE,
    device_type_id BIGINT NOT NULL REFERENCES device_type(id),
    name VARCHAR(200) NOT NULL,
    protocol VARCHAR(50) NOT NULL DEFAULT 'modbus',
    host VARCHAR(100),
    port INT,
    slave_id INT,
    location VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    sampling_interval INT NOT NULL DEFAULT 5000,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB,
    description VARCHAR(500),
    last_online_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_type_id (device_type_id),
    INDEX idx_device_status (status),
    INDEX idx_device_sn (device_sn)
);

-- 测点表
CREATE TABLE IF NOT EXISTS measurement_point (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    point_code VARCHAR(100) NOT NULL,
    point_name VARCHAR(200) NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    unit VARCHAR(20),
    register_address INT,
    register_count INT DEFAULT 1,
    scale_factor DECIMAL(10,4) DEFAULT 1,
    offset_value DECIMAL(10,4) DEFAULT 0,
    alarm_high DECIMAL(18,4),
    alarm_low DECIMAL(18,4),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, point_code),
    INDEX idx_mp_device_id (device_id)
);

-- 分时电价表
CREATE TABLE IF NOT EXISTS time_of_use_price (
    id BIGSERIAL PRIMARY KEY,
    period_type VARCHAR(20) NOT NULL,
    price DECIMAL(10,4) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tou_period (period_type),
    INDEX idx_tou_date (effective_date, expiry_date)
);

-- 告警规则表
CREATE TABLE IF NOT EXISTS alarm_rule (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT REFERENCES device(id) ON DELETE CASCADE,
    point_id BIGINT REFERENCES measurement_point(id) ON DELETE CASCADE,
    rule_name VARCHAR(200) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    threshold DECIMAL(18,4),
    operator VARCHAR(10) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'warning',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notification_email VARCHAR(500),
    notification_phone VARCHAR(500),
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录表
CREATE TABLE IF NOT EXISTS alarm_record (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    point_id BIGINT,
    rule_id BIGINT REFERENCES alarm_rule(id) ON DELETE SET NULL,
    alarm_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    point_value DECIMAL(18,4),
    alarm_time TIMESTAMP NOT NULL,
    acknowledge_time TIMESTAMP,
    clear_time TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    acknowledged_by VARCHAR(100),
    cleared_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alarm_device (device_id),
    INDEX idx_alarm_status (status),
    INDEX idx_alarm_time (alarm_time)
);

-- 调度命令表
CREATE TABLE IF NOT EXISTS dispatch_command (
    id BIGSERIAL PRIMARY KEY,
    command_type VARCHAR(50) NOT NULL,
    device_id BIGINT REFERENCES device(id),
    target_power DECIMAL(18,4),
    duration INT,
    priority INT DEFAULT 5,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    sent_time TIMESTAMP,
    execute_time TIMESTAMP,
    result_message VARCHAR(500),
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cmd_status (status),
    INDEX idx_cmd_created (created_at)
);

-- ==================== 电池与系统建模模块 ====================

-- 电池参数配置表
CREATE TABLE IF NOT EXISTS battery_config (
    id BIGSERIAL PRIMARY KEY,
    device_sn VARCHAR(50) NOT NULL,
    battery_name VARCHAR(100) NOT NULL,
    rated_capacity DECIMAL(10,2) NOT NULL,
    rated_power DECIMAL(10,2) NOT NULL,
    charge_efficiency DECIMAL(5,4) NOT NULL,
    discharge_efficiency DECIMAL(5,4) NOT NULL,
    round_trip_efficiency DECIMAL(5,4),
    min_soc DECIMAL(5,2) NOT NULL,
    max_soc DECIMAL(5,2) NOT NULL,
    optimal_soc_min DECIMAL(5,2),
    optimal_soc_max DECIMAL(5,2),
    nominal_voltage DECIMAL(10,2),
    max_charge_current DECIMAL(10,2),
    max_discharge_current DECIMAL(10,2),
    max_charge_power DECIMAL(10,2),
    max_discharge_power DECIMAL(10,2),
    min_temperature DECIMAL(5,2),
    max_temperature DECIMAL(5,2),
    optimal_temp_min DECIMAL(5,2),
    optimal_temp_max DECIMAL(5,2),
    initial_soh DECIMAL(5,4) NOT NULL,
    current_soh DECIMAL(5,4),
    cycle_count INT,
    battery_type VARCHAR(50),
    manufacturer VARCHAR(100),
    installation_date DATE,
    warranty_period_months INT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_sn),
    INDEX idx_battery_device_sn (device_sn),
    INDEX idx_battery_enabled (enabled)
);

-- 电池衰减模型表
CREATE TABLE IF NOT EXISTS battery_degradation_model (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    battery_type VARCHAR(50),
    degradation_rate_per_cycle DECIMAL(10,8),
    decay_constant DECIMAL(10,8),
    end_of_life_soh DECIMAL(5,4) NOT NULL,
    warranty_cycle_count INT,
    warranty_soh DECIMAL(5,4),
    calendar_aging_rate_per_year DECIMAL(10,8),
    temperature_factor DECIMAL(10,8),
    soc_factor DECIMAL(10,8),
    charge_rate_factor DECIMAL(10,8),
    discharge_rate_factor DECIMAL(10,8),
    depth_of_discharge_factor DECIMAL(10,8),
    max_cycle_count INT,
    estimated_lifespan_years DECIMAL(5,2),
    default_model BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_degradation_model_type (model_type),
    INDEX idx_degradation_enabled (enabled)
);

-- 电池衰减数据点表
CREATE TABLE IF NOT EXISTS battery_degradation_point (
    id BIGSERIAL PRIMARY KEY,
    degradation_model_id BIGINT NOT NULL REFERENCES battery_degradation_model(id) ON DELETE CASCADE,
    cycle_count INT NOT NULL,
    soh DECIMAL(5,4) NOT NULL,
    capacity_retention DECIMAL(5,4),
    internal_resistance_ratio DECIMAL(5,4),
    temperature DECIMAL(5,2),
    depth_of_discharge DECIMAL(5,2),
    charge_rate DECIMAL(5,2),
    discharge_rate DECIMAL(5,2),
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_degradation_model_id (degradation_model_id),
    INDEX idx_cycle_count (cycle_count)
);

-- 变压器需量管理表
CREATE TABLE IF NOT EXISTS transformer_demand_config (
    id BIGSERIAL PRIMARY KEY,
    transformer_code VARCHAR(50) NOT NULL,
    transformer_name VARCHAR(100) NOT NULL,
    rated_capacity DECIMAL(10,2) NOT NULL,
    rated_voltage DECIMAL(10,2),
    rated_current DECIMAL(10,2),
    demand_threshold DECIMAL(10,2) NOT NULL,
    demand_warning_threshold DECIMAL(10,2),
    demand_limit DECIMAL(10,2),
    assessment_cycle_minutes INT NOT NULL,
    demand_billing_method VARCHAR(50),
    demand_price DECIMAL(10,4),
    capacity_price DECIMAL(10,4),
    max_demand_current DECIMAL(10,2),
    max_demand_previous DECIMAL(10,2),
    demand_control_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    control_strategy VARCHAR(50),
    discharge_priority DECIMAL(5,2),
    load_shedding_priority DECIMAL(5,2),
    pv_self_use_priority DECIMAL(5,2),
    min_soc_protection DECIMAL(5,2),
    warning_notification_enabled BOOLEAN DEFAULT TRUE,
    notification_threshold_percent DECIMAL(5,2),
    peak_shaving_enabled BOOLEAN DEFAULT TRUE,
    peak_shaving_threshold DECIMAL(10,2),
    energy_management_enabled BOOLEAN DEFAULT TRUE,
    location VARCHAR(100),
    installation_date DATE,
    manufacturer VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(transformer_code),
    INDEX idx_transformer_code (transformer_code),
    INDEX idx_demand_enabled (enabled)
);
