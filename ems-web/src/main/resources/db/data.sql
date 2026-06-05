INSERT INTO device_type (code, name, description) VALUES
('meter', '智能电表', '用电负荷采集设备'),
('pv', '光伏逆变器', '光伏发电设备'),
('bms', '电池管理系统', '电池状态监测'),
('pcs', '储能变流器', '储能充放电控制'),
('weather', '气象站', '环境数据采集')
ON CONFLICT (code) DO NOTHING;

INSERT INTO device (device_sn, device_type_id, name, protocol, host, port, slave_id, location, sampling_interval, config) VALUES
('MTR-001', 1, '总进线电表', 'modbus-tcp', '192.168.1.10', 502, 1, '配电房', 5000, '{"baud_rate": 9600, "parity": "none"}'),
('MTR-002', 1, '车间1电表', 'modbus-tcp', '192.168.1.11', 502, 2, '车间1', 5000, '{"baud_rate": 9600, "parity": "none"}'),
('PV-001', 2, '屋顶光伏逆变器', 'modbus-tcp', '192.168.1.20', 502, 3, '屋顶', 10000, '{"capacity": 500}'),
('BMS-001', 3, '储能柜BMS', 'modbus-tcp', '192.168.1.30', 502, 4, '储能室', 3000, '{"battery_count": 20}'),
('PCS-001', 4, '储能变流器', 'modbus-tcp', '192.168.1.31', 502, 5, '储能室', 3000, '{"rated_power": 250}')
ON CONFLICT (device_sn) DO NOTHING;

INSERT INTO time_of_use_price (period_type, price, start_time, end_time, effective_date, description) VALUES
('peak', 1.1500, '08:00:00', '11:00:00', '2024-01-01', '峰时电价'),
('peak', 1.1500, '18:00:00', '23:00:00', '2024-01-01', '峰时电价'),
('flat', 0.7500, '06:00:00', '08:00:00', '2024-01-01', '平时电价'),
('flat', 0.7500, '11:00:00', '18:00:00', '2024-01-01', '平时电价'),
('valley', 0.3500, '23:00:00', '06:00:00', '2024-01-01', '谷时电价')
ON CONFLICT DO NOTHING;

-- ==================== 电池与系统建模模块默认数据 ====================

-- 电池参数配置
INSERT INTO battery_config (
    device_sn, battery_name, rated_capacity, rated_power,
    charge_efficiency, discharge_efficiency, round_trip_efficiency,
    min_soc, max_soc, optimal_soc_min, optimal_soc_max,
    nominal_voltage, max_charge_current, max_discharge_current,
    max_charge_power, max_discharge_power,
    min_temperature, max_temperature, optimal_temp_min, optimal_temp_max,
    initial_soh, current_soh, cycle_count,
    battery_type, manufacturer, installation_date, warranty_period_months,
    enabled, description
) VALUES (
    'BMS-001', '储能电池组1号', 500.00, 250.00,
    0.9500, 0.9500, 0.9025,
    10.00, 90.00, 30.00, 70.00,
    600.00, 200.00, 200.00,
    250.00, 250.00,
    -10.00, 60.00, 20.00, 35.00,
    1.0000, 0.9850, 120,
    'LFP', '宁德时代', '2024-01-15', 60,
    TRUE, '磷酸铁锂电池组，额定容量500kWh'
), (
    'BMS-002', '储能电池组2号', 500.00, 250.00,
    0.9500, 0.9500, 0.9025,
    10.00, 90.00, 30.00, 70.00,
    600.00, 200.00, 200.00,
    250.00, 250.00,
    -10.00, 60.00, 20.00, 35.00,
    1.0000, 0.9900, 85,
    'LFP', '宁德时代', '2024-01-15', 60,
    TRUE, '磷酸铁锂电池组，额定容量500kWh'
)
ON CONFLICT (device_sn) DO NOTHING;

-- 电池衰减模型 - 磷酸铁锂线性衰减
INSERT INTO battery_degradation_model (
    model_name, model_type, battery_type,
    degradation_rate_per_cycle, decay_constant,
    end_of_life_soh, warranty_cycle_count, warranty_soh,
    calendar_aging_rate_per_year, temperature_factor, soc_factor,
    charge_rate_factor, discharge_rate_factor, depth_of_discharge_factor,
    max_cycle_count, estimated_lifespan_years,
    default_model, enabled, description
) VALUES (
    'LFP标准线性衰减模型', 'LINEAR', 'LFP',
    0.000020, NULL,
    0.8000, 6000, 0.8000,
    0.01500000, 0.00100000, 0.00050000,
    0.00100000, 0.00100000, 0.00100000,
    10000, 10.00,
    TRUE, TRUE,
    '磷酸铁锂电池标准线性衰减模型，每循环衰减约0.002%，寿命终止SOH=80%'
), (
    'LFP经验衰减模型', 'EMPIRICAL', 'LFP',
    NULL, NULL,
    0.8000, 6000, 0.8000,
    0.01500000, 0.00100000, 0.00050000,
    0.00100000, 0.00100000, 0.00100000,
    10000, 10.00,
    FALSE, TRUE,
    '磷酸铁锂电池经验衰减模型，基于实测数据点插值计算'
), (
    'NMC标准衰减模型', 'EXPONENTIAL', 'NMC',
    NULL, 0.000015,
    0.7000, 3000, 0.7000,
    0.02000000, 0.00150000, 0.00080000,
    0.00150000, 0.00150000, 0.00150000,
    6000, 8.00,
    FALSE, TRUE,
    '三元锂电池标准指数衰减模型，寿命终止SOH=70%'
)
ON CONFLICT DO NOTHING;

-- LFP经验衰减模型数据点
INSERT INTO battery_degradation_point (
    degradation_model_id, cycle_count, soh, capacity_retention,
    temperature, depth_of_discharge, charge_rate, discharge_rate, remarks
) VALUES
(2, 0, 1.0000, 1.0000, 25.00, 80.00, 0.50, 0.50, '初始状态'),
(2, 500, 0.9800, 0.9800, 25.00, 80.00, 0.50, 0.50, '500次循环'),
(2, 1000, 0.9600, 0.9600, 25.00, 80.00, 0.50, 0.50, '1000次循环'),
(2, 2000, 0.9200, 0.9200, 25.00, 80.00, 0.50, 0.50, '2000次循环'),
(2, 3000, 0.8800, 0.8800, 25.00, 80.00, 0.50, 0.50, '3000次循环'),
(2, 4000, 0.8400, 0.8400, 25.00, 80.00, 0.50, 0.50, '4000次循环'),
(2, 5000, 0.8000, 0.8000, 25.00, 80.00, 0.50, 0.50, '5000次循环（寿命终止）')
ON CONFLICT DO NOTHING;

-- 变压器需量管理配置
INSERT INTO transformer_demand_config (
    transformer_code, transformer_name,
    rated_capacity, rated_voltage, rated_current,
    demand_threshold, demand_warning_threshold, demand_limit,
    assessment_cycle_minutes, demand_billing_method,
    demand_price, capacity_price,
    max_demand_current, max_demand_previous,
    demand_control_enabled, control_strategy,
    discharge_priority, load_shedding_priority, pv_self_use_priority,
    min_soc_protection, warning_notification_enabled, notification_threshold_percent,
    peak_shaving_enabled, peak_shaving_threshold,
    energy_management_enabled,
    location, installation_date, manufacturer,
    enabled, description
) VALUES (
    'TR-001', '1号主变压器',
    1000.00, 10.00, 57.74,
    800.00, 720.00, 900.00,
    15, 'DEMAND',
    35.0000, 23.0000,
    650.00, 720.00,
    TRUE, 'PRIORITY_BASED',
    80.00, 50.00, 70.00,
    20.00, TRUE, 80.00,
    TRUE, 850.00,
    TRUE,
    '配电房主变压器位', '2023-06-01', '特变电工',
    TRUE, '1000kVA主变压器，需量控制目标800kW'
), (
    'TR-002', '2号变压器',
    500.00, 10.00, 28.87,
    400.00, 360.00, 450.00,
    15, 'DEMAND',
    35.0000, 23.0000,
    320.00, 380.00,
    TRUE, 'PRIORITY_BASED',
    80.00, 50.00, 70.00,
    20.00, TRUE, 80.00,
    TRUE, 425.00,
    TRUE,
    '配电房2号变压器位', '2023-06-01', '特变电工',
    TRUE, '500kVA变压器，需量控制目标400kW'
)
ON CONFLICT (transformer_code) DO NOTHING;
