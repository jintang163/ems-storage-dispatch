# 能源管理系统（EMS）储能优化调度模块

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端展示层                              │
│  Vue 3 + ECharts | 实时监控 | 设备管理 | 电价配置 | 历史查询  │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP/REST
┌────────────────────────────▼────────────────────────────────────┐
│                     后端服务层 (Spring Boot 3)                 │
│  ┌──────────┬──────────┬──────────┬──────────┬─────────────┐  │
│  │ REST API │ MQTT 客户端│ 业务服务 │ 数据持久化 │ 优化调度引擎 │  │
│  └──────────┴──────────┴──────────┴──────────┴─────────────┘  │
└─────────┬────────────────────────────┬─────────────────────────┘
          │ MQTT                      │ JDBC/InfluxDB
┌─────────▼──────────┐   ┌───────────▼───────────┐   ┌──────────────┐
│  MQTT Broker    │   │  PostgreSQL        │   │   InfluxDB    │
│  (EMQX)           │   │  (设备/配置/告警) │   │  (时序数据)  │
└─────────▲──────────┘   └───────────────────┘   └──────────────┘
          │ MQTT
┌─────────┴───────────────────────────────────────────────────────┐
│                    数据采集层 (Python)                              │
│  ┌────────┬────────┬────────┬────────┐                      │
│  │ Modbus │ 光伏逆变器 │ BMS采集│ PCS采集│ 断线缓存补传 │                      │
│  └────────┴────────┴────────┴────────┘                      │
└─────────┬───────────────────────────────────────────────────────┘
          │ Modbus TCP/RTU
┌─────────▼───────────────────────────────────────────────────────┐
│                    设备层                                          │
│  智能电表 | 光伏逆变器 | BMS | PCS | 气象站                  │
└─────────────────────────────────────────────────────────────────┘
```

## 技术栈

- **后端**: Spring Boot 3.2.x + Spring Data JPA + Spring Integration MQTT
- **消息队列**: MQTT (EMQX 5.x)
- **数据采集**: Python 3.11 + pymodbus + paho-mqtt
- **前端**: Vue 3 + Vite + TypeScript + ECharts 5 + Element Plus
- **数据库**: PostgreSQL 15 + InfluxDB 2.x
- **部署**: Docker + Docker Compose

## 项目结构

```
ems-storage-dispatch/
├── ems-common/                 # 公共模块
│   └── src/main/java/com/ems/common/
│       ├── constants/          # 常量定义
│       ├── enums/              # 枚举定义
│       ├── exception/          # 自定义异常
│       ├── result/             # 统一返回结果
│       └── utils/              # 工具类
├── ems-domain/                 # 领域模型
│   └── src/main/java/com/ems/domain/
│       ├── entity/             # JPA实体类
│       ├── tsdb/               # InfluxDB时序模型
│       ├── dto/                # 数据传输对象
│       └── vo/                 # 视图对象
├── ems-influxdb/               # InfluxDB数据访问
│   └── src/main/java/com/ems/influxdb/
│       ├── config/             # InfluxDB配置
│       └── service/            # 时序数据服务
├── ems-mqtt/                   # MQTT消息处理
│   └── src/main/java/com/ems/mqtt/
│       ├── config/             # MQTT配置
│       ├── handler/            # 消息处理器
│       └── service/            # MQTT发布服务
├── ems-service/                # 业务服务层
│   └── src/main/java/com/ems/
│       ├── repository/         # JPA Repository
│       └── service/            # 业务服务实现
├── ems-web/                    # Web层
│   └── src/main/
│       ├── java/com/ems/
│       │   ├── controller/     # REST API控制器
│       │   ├── config/         # Web配置
│       │   ├── exception/      # 全局异常处理
│       │   └── EmsApplication.java
│       └── resources/
│           ├── application.yml
│           ├── application-docker.yml
│           └── db/             # 数据库脚本
├── ems-collector/              # Python数据采集服务
│   ├── src/
│   │   ├── main.py             # 主程序
│   │   ├── config.py           # 配置加载
│   │   ├── modbus_client.py    # Modbus客户端
│   │   ├── device_collector.py # 设备采集器
│   │   ├── mqtt_publisher.py   # MQTT发布者
│   │   ├── cache_manager.py    # 断线缓存管理
│   │   └── data_simulator.py   # 数据模拟器
│   ├── config/
│   │   └── devices.yaml        # 设备配置
│   ├── requirements.txt
│   ├── .env.example
│   └── Dockerfile.collector
├── ems-webapp/                 # 前端Vue3应用
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── router/             # 路由配置
│   │   ├── stores/             # Pinia状态管理
│   │   ├── api/                # API接口
│   │   ├── styles/             # 全局样式
│   │   ├── App.vue
│   │   └── main.ts
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── nginx.conf
│   └── Dockerfile.frontend
├── docker-compose.yml          # Docker Compose编排
├── Dockerfile.backend          # 后端Dockerfile
├── pom.xml                     # Maven父POM
└── README.md
```

## 模块划分

### 后端模块
- `ems-common`: 公共工具类、常量定义、异常处理、统一返回结果
- `ems-domain`: 实体类、DTO、VO、时序数据模型
- `ems-influxdb`: InfluxDB时序数据写入与查询服务
- `ems-mqtt`: MQTT消息订阅与发布、消息路由处理
- `ems-service`: 业务逻辑层、数据访问Repository
- `ems-web`: REST API控制层、全局异常处理、CORS配置

### Python采集模块
- `ems-collector`: Modbus设备数据采集服务，支持真实设备和模拟数据

### 前端模块
- `ems-webapp`: Vue 3前端应用，包含实时监控、设备管理、电价配置、历史数据查询

## 功能模块

### 一、数据采集与接入 ✅
1. 园区/工厂实时用电负荷采集（电表/MQTT/Modbus）
2. 分时电价数据管理（手动配置/电力公司接口）
3. 光伏等新能源出力实时数据采集
4. 电池管理系统（BMS）数据采集：SOC、SOH、电压、电流、温度
5. 储能变流器（PCS）数据采集：充放电功率、运行状态、告警
6. 数据断线缓存与补传机制（JSONL本地文件 + 自动重试）

### 二、储能优化调度（后续开发）
- 基于分时电价的充放电策略优化
- 考虑光伏出力预测的调度算法
- 用户侧负荷响应策略

### 三、运行监控与告警（后续开发）
- 设备在线状态监控
- 越限告警与通知
- 告警历史记录

### 四、统计分析与报表（后续开发）
- 用电量统计分析
- 充放电收益分析
- 自定义报表导出

## MQTT主题规范

```
ems/device/meter/{deviceSn}/data    # 电表数据
ems/device/pv/{deviceSn}/data       # 光伏数据
ems/device/bms/{deviceSn}/data      # BMS数据
ems/device/pcs/{deviceSn}/data      # PCS数据
ems/device/{deviceSn}/status        # 设备状态
ems/device/command                  # 控制命令（下发）
```

## InfluxDB时序数据模型

- **meter**: 电表数据（activePower, reactivePower, powerFactor, frequency等）
- **pv**: 光伏数据（outputPower, dailyEnergy, totalEnergy等）
- **bms**: BMS数据（soc, soh, totalVoltage, totalCurrent, temperatures等）
- **pcs**: PCS数据（activePower, reactivePower, workMode, status等）

## 快速开始

### 方式一：Docker Compose 一键部署

```bash
# 克隆项目
git clone <repository-url>
cd ems-storage-dispatch

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f ems-backend
```

**访问地址：**
- 前端页面: http://localhost:3000
- 后端API: http://localhost:8080
- InfluxDB管理: http://localhost:8086 (admin/admin123456)
- EMQX管理: http://localhost:18083 (admin/public)

### 方式二：本地开发运行

#### 1. 启动基础服务（PostgreSQL, InfluxDB, EMQX）

```bash
docker-compose up -d postgres influxdb emqx
```

#### 2. 启动后端服务

```bash
# 编译项目
mvn clean package -DskipTests

# 运行后端
cd ems-web
mvn spring-boot:run
```

#### 3. 启动Python采集服务

```bash
cd ems-collector

# 安装依赖
pip install -r requirements.txt

# 复制环境变量配置
cp .env.example .env

# 运行采集服务（默认模拟模式，无真实设备也可运行）
python -m src.main
```

#### 4. 启动前端服务

```bash
cd ems-webapp

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

**访问地址：** http://localhost:3000

## REST API 接口

### 设备管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/device/list | 设备列表（分页） |
| GET | /api/device/{id} | 获取设备详情 |
| GET | /api/device/sn/{deviceSn} | 根据编号获取设备 |
| POST | /api/device | 新增设备 |
| PUT | /api/device | 更新设备 |
| DELETE | /api/device/{id} | 删除设备 |

### 电价配置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/price/list | 电价列表 |
| GET | /api/price/{id} | 获取电价详情 |
| GET | /api/price/current | 获取当前电价 |
| POST | /api/price | 新增电价 |
| PUT | /api/price | 更新电价 |
| DELETE | /api/price/{id} | 删除电价 |

### 数据查询

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/data/realtime/{deviceType}/{deviceSn} | 获取实时数据 |
| GET | /api/data/history | 查询历史数据 |
| GET | /api/data/latest/{deviceType}/{deviceSn} | 获取最新N条数据 |

### 控制命令

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/command/send | 发送控制命令 |
| POST | /api/command/charge | 开始充电 |
| POST | /api/command/discharge | 开始放电 |
| POST | /api/command/stop | 停止充放电 |

## 数据模拟器

Python采集服务内置数据模拟器，在没有真实设备的情况下也可以生成仿真数据进行测试：

```yaml
# ems-collector/config/devices.yaml
simulation:
  enabled: true    # 开启模拟模式
  mode: random     # random / fixed / profile
```

模拟数据特点：
- 用电负荷：工作日/周末模式，早晚高峰特征
- 光伏出力：遵循日照曲线，白天有输出，夜间为0
- 电池SOC：根据充放电指令动态变化
- PCS功率：响应控制命令，支持正（放电）负（充电）值

## 断线缓存与补传

系统实现了双重断线缓存机制：

### Python采集端缓存
- MQTT断线时自动将数据写入本地JSONL文件
- 自动文件轮转（默认100MB）
- 重连后自动重试发送缓存数据
- 发送成功后自动删除已发送数据

### 配置参数
```yaml
cache:
  enabled: true
  path: ./data/cache
  max_file_size: 104857600  # 100MB
  retry_interval: 60         # 重试间隔（秒）
```

## 数据库表结构

### PostgreSQL关系表
- `device_type`: 设备类型表
- `device`: 设备信息表
- `measurement_point`: 测点配置表
- `time_of_use_price`: 分时电价表
- `alarm_rule`: 告警规则表
- `alarm_record`: 告警记录表
- `dispatch_command`: 调度命令表

### InfluxDB时序数据
- Tags: deviceSn, location
- Fields: 各设备类型的测量值
- Timestamp: 数据采集时间

## 配置说明

### 后端配置 (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ems
    username: ems
    password: ems123456
  influxdb:
    url: http://localhost:8086
    token: ems-token-123456
    org: ems
    bucket: ems
  mqtt:
    url: tcp://localhost:1883
    client-id: ems-backend
```

### Python采集配置 (devices.yaml)
```yaml
mqtt:
  broker: localhost
  port: 1883

simulation:
  enabled: true

devices:
  - device_sn: METER-001
    device_type: meter
    protocol: MODBUS_TCP
    host: 127.0.0.1
    port: 502
    slave_id: 1
    sampling_interval: 5000
    points:
      - name: activePower
        address: 0
        count: 2
        data_type: float32
        scale: 0.01
```

## 告警规则（后续开发）

- SOC过低告警（<20%）
- SOC过高告警（>95%）
- 温度过高告警（>60°C）
- PCS故障告警
- 设备离线告警

## 注意事项

1. 生产环境请修改默认密码
2. Modbus采集需要确保网络可达和端口开放
3. InfluxDB数据保留策略可根据实际需求调整
4. 建议使用Nginx或API网关做前端反向代理
5. 数据备份策略：PostgreSQL定期备份，InfluxDB使用快照备份

## 后续开发计划

- [ ] 储能优化调度算法模块
- [ ] 光伏出力预测模块
- [ ] 告警检测与通知模块（邮件/短信/钉钉）
- [ ] 用户管理与权限控制
- [ ] 更丰富的统计报表
- [ ] 移动APP/小程序
- [ ] 多园区/多站点支持

## 许可证

MIT License
