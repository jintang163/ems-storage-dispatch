# EMS系统数据链路字段映射核对表

## 字段命名规范

- Python/配置文件: snake_case (下划线命名)
- Java DTO: camelCase (驼峰命名)，通过 `@JSONField(name = "snake_case")` 映射
- InfluxDB: camelCase (驼峰命名)
- 前端TypeScript: camelCase (驼峰命名)
- MySQL数据库: snake_case (下划线命名)

---

## 1. 电表数据 (Meter)

| Python/配置字段 | Java DTO字段 | InfluxDB字段 | 前端字段 | 单位 | 说明 |
|------------------|-------------|--------------|----------|------|------|
| deviceSn | deviceSn | deviceSn | deviceSn | - | 设备编号 |
| location | location | location | location | - | 安装位置 |
| voltage_a | voltageA | voltageA | voltageA | V | A相电压 |
| voltage_b | voltageB | voltageB | voltageB | V | B相电压 |
| voltage_c | voltageC | voltageC | voltageC | V | C相电压 |
| current_a | currentA | currentA | currentA | A | A相电流 |
| current_b | currentB | currentB | currentB | A | B相电流 |
| current_c | currentC | currentC | currentC | A | C相电流 |
| active_power | activePower | activePower | activePower | kW | 有功功率 |
| reactive_power | reactivePower | reactivePower | reactivePower | kVar | 无功功率 |
| apparent_power | apparentPower | apparentPower | apparentPower | kVA | 视在功率 |
| power_factor | powerFactor | powerFactor | powerFactor | - | 功率因数 |
| frequency | frequency | frequency | frequency | Hz | 频率 |
| total_active_energy | totalActiveEnergy | totalActiveEnergy | totalActiveEnergy | kWh | 总有功电能 |
| total_reactive_energy | totalReactiveEnergy | totalReactiveEnergy | totalReactiveEnergy | kVarh | 总无功电能 |
| import_active_energy | importActiveEnergy | importActiveEnergy | importActiveEnergy | kWh | 正向有功电能 |
| export_active_energy | exportActiveEnergy | exportActiveEnergy | exportActiveEnergy | kWh | 反向有功电能 |
| demand | demand | demand | demand | kW | 需量 |
| thd_voltage_a | thdVoltageA | thdVoltageA | thdVoltageA | % | A相电压谐波畸变率 |
| thd_current_a | thdCurrentA | thdCurrentA | thdCurrentA | % | A相电流谐波畸变率 |
| timestamp | timestamp | timestamp | timestamp | ms | 时间戳 |

---

## 2. 光伏逆变器数据 (PV)

| Python/配置字段 | Java DTO字段 | InfluxDB字段 | 前端字段 | 单位 | 说明 |
|------------------|-------------|--------------|----------|------|------|
| deviceSn | deviceSn | deviceSn | deviceSn | - | 设备编号 |
| location | location | location | location | - | 安装位置 |
| dc_voltage | dcVoltage | dcVoltage | dcVoltage | V | 直流输入电压 |
| dc_current | dcCurrent | dcCurrent | dcCurrent | A | 直流输入电流 |
| dc_power | dcPower | dcPower | dcPower | kW | 直流输入功率 |
| ac_voltage_a | acVoltageA | acVoltageA | acVoltageA | V | A相交流输出电压 |
| ac_voltage_b | acVoltageB | acVoltageB | acVoltageB | V | B相交流输出电压 |
| ac_voltage_c | acVoltageC | acVoltageC | acVoltageC | V | C相交流输出电压 |
| ac_current_a | acCurrentA | acCurrentA | acCurrentA | A | A相交流输出电流 |
| ac_current_b | acCurrentB | acCurrentB | acCurrentB | A | B相交流输出电流 |
| ac_current_c | acCurrentC | acCurrentC | acCurrentC | A | C相交流输出电流 |
| ac_power | acPower | acPower | acPower | kW | 交流输出功率(前端显示用outputPower) |
| ac_reactive_power | acReactivePower | acReactivePower | acReactivePower | kVar | 交流无功功率 |
| power_factor | powerFactor | powerFactor | powerFactor | - | 功率因数 |
| frequency | frequency | frequency | frequency | Hz | 电网频率 |
| efficiency | efficiency | efficiency | efficiency | % | 逆变器效率 |
| total_energy | totalEnergy | totalEnergy | totalEnergy | kWh | 累计发电量 |
| daily_energy | dailyEnergy | dailyEnergy | dailyEnergy | kWh | 当日发电量 |
| module_temperature | moduleTemperature | moduleTemperature | moduleTemperature | °C | 组件温度(前端用temperature) |
| ambient_temperature | ambientTemperature | ambientTemperature | ambientTemperature | °C | 环境温度 |
| irradiance | irradiance | irradiance | irradiance | W/m² | 辐照度 |
| operating_status | operatingStatus | operatingStatus | operatingStatus | - | 运行状态：0-停机,1-运行,2-故障 |
| fault_code | faultCode | faultCode | faultCode | - | 故障代码 |
| timestamp | timestamp | timestamp | timestamp | ms | 时间戳 |

---

## 3. BMS电池管理系统数据 (BMS)

| Python/配置字段 | Java DTO字段 | InfluxDB字段 | 前端字段 | 单位 | 说明 |
|------------------|-------------|--------------|----------|------|------|
| deviceSn | deviceSn | deviceSn | deviceSn | - | 设备编号 |
| location | location | location | location | - | 安装位置 |
| soc | soc | soc | soc | % | 荷电状态 |
| soh | soh | soh | soh | % | 健康状态 |
| total_voltage | totalVoltage | totalVoltage | totalVoltage | V | 总电压 |
| total_current | totalCurrent | totalCurrent | totalCurrent | A | 总电流 |
| max_cell_voltage | maxCellVoltage | maxCellVoltage | maxCellVoltage | V | 最高单体电压 |
| min_cell_voltage | minCellVoltage | minCellVoltage | minCellVoltage | V | 最低单体电压 |
| max_cell_voltage_no | maxCellVoltageNo | maxCellVoltageNo | maxCellVoltageNo | - | 最高电压单体编号 |
| min_cell_voltage_no | minCellVoltageNo | minCellVoltageNo | minCellVoltageNo | - | 最低电压单体编号 |
| avg_cell_voltage | avgCellVoltage | avgCellVoltage | avgCellVoltage | V | 平均单体电压 |
| max_temperature | maxTemperature | maxTemperature | maxTemperature | °C | 最高温度 |
| min_temperature | minTemperature | minTemperature | minTemperature | °C | 最低温度 |
| avg_temperature | avgTemperature | avgTemperature | avgTemperature | °C | 平均温度 |
| max_temp_no | maxTempNo | maxTempNo | maxTempNo | - | 最高温度传感器编号 |
| min_temp_no | minTempNo | minTempNo | minTempNo | - | 最低温度传感器编号 |
| charge_current_limit | chargeCurrentLimit | chargeCurrentLimit | chargeCurrentLimit | A | 充电电流限制 |
| discharge_current_limit | dischargeCurrentLimit | dischargeCurrentLimit | dischargeCurrentLimit | A | 放电电流限制 |
| max_charge_power | maxChargePower | maxChargePower | maxChargePower | kW | 最大充电功率 |
| max_discharge_power | maxDischargePower | maxDischargePower | maxDischargePower | kW | 最大放电功率 |
| cycle_count | cycleCount | cycleCount | cycleCount | 次 | 循环次数 |
| capacity | capacity | capacity | capacity | Ah | 标称容量 |
| remaining_capacity | remainingCapacity | remainingCapacity | remainingCapacity | Ah | 剩余容量 |
| design_capacity | designCapacity | designCapacity | designCapacity | Ah | 设计容量 |
| bms_status | bmsStatus | bmsStatus | bmsStatus | - | BMS运行状态 |
| charge_enable | chargeEnable | chargeEnable | chargeEnable | - | 充电允许标志 |
| discharge_enable | dischargeEnable | dischargeEnable | dischargeEnable | - | 放电允许标志 |
| heating_enable | heatingEnable | heatingEnable | heatingEnable | - | 加热允许标志 |
| fault_code | faultCode | faultCode | faultCode | - | 故障代码 |
| warning_code | warningCode | warningCode | warningCode | - | 告警代码 |
| protection_code | protectionCode | protectionCode | protectionCode | - | 保护代码 |
| cell_count | cellCount | cellCount | cellCount | 节 | 单体数量 |
| temp_sensor_count | tempSensorCount | tempSensorCount | tempSensorCount | 个 | 温度传感器数量 |
| timestamp | timestamp | timestamp | timestamp | ms | 时间戳 |

---

## 4. PCS储能变流器数据 (PCS)

| Python/配置字段 | Java DTO字段 | InfluxDB字段 | 前端字段 | 单位 | 说明 |
|------------------|-------------|--------------|----------|------|------|
| deviceSn | deviceSn | deviceSn | deviceSn | - | 设备编号 |
| location | location | location | location | - | 安装位置 |
| dc_voltage | dcVoltage | dcVoltage | dcVoltage | V | 直流侧电压 |
| dc_current | dcCurrent | dcCurrent | dcCurrent | A | 直流侧电流 |
| dc_power | dcPower | dcPower | dcPower | kW | 直流侧功率 |
| ac_voltage_a | acVoltageA | acVoltageA | acVoltageA | V | A相交流侧电压 |
| ac_voltage_b | acVoltageB | acVoltageB | acVoltageB | V | B相交流侧电压 |
| ac_voltage_c | acVoltageC | acVoltageC | acVoltageC | V | C相交流侧电压 |
| ac_current_a | acCurrentA | acCurrentA | acCurrentA | A | A相交流侧电流 |
| ac_current_b | acCurrentB | acCurrentB | acCurrentB | A | B相交流侧电流 |
| ac_current_c | acCurrentC | acCurrentC | acCurrentC | A | C相交流侧电流 |
| active_power | activePower | activePower | activePower | kW | 有功功率（正放电负充电） |
| reactive_power | reactivePower | reactivePower | reactivePower | kVar | 无功功率 |
| apparent_power | apparentPower | apparentPower | apparentPower | kVA | 视在功率 |
| power_factor | powerFactor | powerFactor | powerFactor | - | 功率因数 |
| frequency | frequency | frequency | frequency | Hz | 电网频率 |
| efficiency | efficiency | efficiency | efficiency | % | 变流器效率 |
| total_charge_energy | totalChargeEnergy | totalChargeEnergy | totalChargeEnergy | kWh | 累计充电电量 |
| total_discharge_energy | totalDischargeEnergy | totalDischargeEnergy | totalDischargeEnergy | kWh | 累计放电电量 |
| daily_charge_energy | dailyChargeEnergy | dailyChargeEnergy | dailyChargeEnergy | kWh | 当日充电电量 |
| daily_discharge_energy | dailyDischargeEnergy | dailyDischargeEnergy | dailyDischargeEnergy | kWh | 当日放电电量 |
| grid_voltage | gridVoltage | gridVoltage | gridVoltage | V | 电网电压 |
| grid_frequency | gridFrequency | gridFrequency | gridFrequency | Hz | 电网频率 |
| inverter_temperature | inverterTemperature | inverterTemperature | inverterTemperature | °C | 逆变器温度 |
| heat_sink_temperature | heatSinkTemperature | heatSinkTemperature | heatSinkTemperature | °C | 散热器温度 |
| running_status | runningStatus | runningStatus | runningStatus | - | 运行状态：0-停机,1-运行,2-故障,3-告警(前端用status) |
| work_mode | workMode | workMode | workMode | - | 工作模式：0-待机,1-充电,2-放电,3-恒压,4-恒流 |
| control_mode | controlMode | controlMode | controlMode | - | 控制模式：0-本地,1-远程 |
| power_setpoint | powerSetpoint | powerSetpoint | powerSetpoint | kW | 有功功率设定值 |
| reactive_power_setpoint | reactivePowerSetpoint | reactivePowerSetpoint | reactivePowerSetpoint | kVar | 无功功率设定值 |
| grid_connect_status | gridConnectStatus | gridConnectStatus | gridConnectStatus | - | 并网状态：false-离网,true-并网 |
| fault_code | faultCode | faultCode | faultCode | - | 故障代码 |
| warning_code | warningCode | warningCode | warningCode | - | 告警代码 |
| dc_max_voltage | dcMaxVoltage | dcMaxVoltage | dcMaxVoltage | V | 直流侧最大允许电压 |
| dc_min_voltage | dcMinVoltage | dcMinVoltage | dcMinVoltage | V | 直流侧最小允许电压 |
| ac_max_current | acMaxCurrent | acMaxCurrent | acMaxCurrent | A | 交流侧最大允许电流 |
| timestamp | timestamp | timestamp | timestamp | ms | 时间戳 |

---

## 5. 设备状态数据 (DeviceStatus)

| Python/配置字段 | Java DTO字段 | 前端字段 | 说明 |
|------------------|-------------|----------|------|
| deviceSn | deviceSn | deviceSn | 设备编号 |
| status | status | status | 设备状态：online-在线, offline-离线 |
| timestamp | timestamp | timestamp | 时间戳(ms) |

---

## API路径映射

| 前端API调用 | 后端Controller路径 | 说明 |
|------------|-------------------|------|
| GET /devices/list | GET /api/devices/list | 获取设备列表 |
| GET /devices/{id} | GET /api/devices/{id} | 获取设备详情 |
| POST /devices | POST /api/devices | 创建设备 |
| PUT /devices/{id} | PUT /api/devices/{id} | 更新设备 |
| DELETE /devices/{id} | DELETE /api/devices/{id} | 删除设备 |
| GET /devices/sn/{deviceSn} | GET /api/devices/sn/{deviceSn} | 根据编号获取设备 |
| GET /prices | GET /api/prices | 获取电价列表 |
| GET /prices/{id} | GET /api/prices/{id} | 获取电价详情 |
| POST /prices | POST /api/prices | 创建电价 |
| PUT /prices/{id} | PUT /api/prices/{id} | 更新电价 |
| DELETE /prices/{id} | DELETE /api/prices/{id} | 删除电价 |
| GET /prices/current | GET /api/prices/current | 获取当前电价 |
| GET /data/realtime/{type}/{sn} | GET /api/data/realtime/{type}/{sn} | 获取实时数据 |
| POST /data/{type}/query | POST /api/data/{type}/query | 查询历史数据 |
| GET /data/{type}/{sn}/latest | GET /api/data/{type}/{sn}/latest | 获取最新数据 |
| POST /commands/charge | POST /api/commands/charge | 开始充电 |
| POST /commands/discharge | POST /api/commands/discharge | 开始放电 |
| POST /commands/stop | POST /api/commands/stop | 停止运行 |
| POST /commands/custom | POST /api/commands/custom | 发送自定义命令 |

---

## MQTT主题规范

```
# 数据上报主题
ems/device/meter/{deviceSn}/data    # 电表数据
ems/device/pv/{deviceSn}/data       # 光伏数据
ems/device/bms/{deviceSn}/data      # BMS数据
ems/device/pcs/{deviceSn}/data      # PCS数据
ems/device/{deviceSn}/status        # 设备状态

# 命令下发主题
ems/device/command                  # 控制命令
```

---

## 字段核对检查清单

- [x] 所有Python配置字段使用snake_case命名
- [x] 所有Java DTO字段使用camelCase命名，并添加@JSONField注解映射snake_case
- [x] 所有InfluxDB @Column注解使用camelCase命名
- [x] 所有前端TypeScript接口使用camelCase命名
- [x] 前端Dashboard显示字段与后端返回字段一致
- [x] API路径前后端一致
- [x] 历史数据查询字段名与实际字段一致
- [x] MQTT消息体字段与Java DTO字段映射正确

## 特殊字段说明

1. **光伏输出功率**: Python端字段名 `ac_power`，Java端 `acPower`，前端显示用 `acPower`（之前误用 `outputPower`，已修复）
2. **光伏温度**: Python端字段名 `module_temperature`，Java端 `moduleTemperature`，前端用 `moduleTemperature`（之前误用 `temperature`，已修复）
3. **PCS运行状态**: Python端字段名 `running_status`，Java端 `runningStatus`，前端用 `runningStatus`（之前误用 `status`，已修复）
4. **PCS有功功率**: Python端字段名 `active_power`，Java端 `activePower`，正值表示放电，负值表示充电

---

## 最新更新记录

- 2026-06-05: 修复所有字段映射，统一使用@JSONField注解
- 2026-06-05: 修复前端Dashboard字段名（outputPower→acPower, temperature→moduleTemperature, status→runningStatus）
- 2026-06-05: 修复API路径映射，统一前后端路径
- 2026-06-05: 修复Vite代理配置，移除错误的rewrite规则
