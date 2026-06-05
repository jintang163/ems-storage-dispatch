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
