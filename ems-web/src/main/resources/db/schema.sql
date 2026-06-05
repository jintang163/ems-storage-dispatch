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
