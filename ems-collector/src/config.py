import os
import yaml
from dataclasses import dataclass, field
from typing import List, Dict, Any
from dotenv import load_dotenv

load_dotenv()


@dataclass
class PointConfig:
    name: str
    address: int
    count: int
    data_type: str
    scale: float = 1.0
    unit: str = ''


@dataclass
class DeviceConfig:
    device_sn: str
    device_type: str
    name: str
    protocol: str
    host: str
    port: int
    slave_id: int
    location: str
    sampling_interval: int = 5000
    enabled: bool = True
    points: List[PointConfig] = field(default_factory=list)


@dataclass
class CacheConfig:
    enabled: bool = True
    path: str = './data/cache'
    max_file_size: int = 104857600
    retry_interval: int = 60


@dataclass
class SimulationConfig:
    enabled: bool = True
    mode: str = 'random'


@dataclass
class MqttConfig:
    broker: str = 'localhost'
    port: int = 1883
    username: str = ''
    password: str = ''
    client_id: str = 'ems-collector'
    command_topic: str = 'ems/device/command'


@dataclass
class AppConfig:
    devices: List[DeviceConfig] = field(default_factory=list)
    cache: CacheConfig = field(default_factory=CacheConfig)
    simulation: SimulationConfig = field(default_factory=SimulationConfig)
    mqtt: MqttConfig = field(default_factory=MqttConfig)


def load_config(config_path: str = 'config/devices.yaml') -> AppConfig:
    with open(config_path, 'r', encoding='utf-8') as f:
        data = yaml.safe_load(f)

    devices = []
    for device_data in data.get('devices', []):
        points = [PointConfig(**p) for p in device_data.pop('points', [])]
        device = DeviceConfig(points=points, **device_data)
        devices.append(device)

    cache_config = CacheConfig(**data.get('cache', {}))
    simulation_config = SimulationConfig(**data.get('simulation', {}))

    mqtt_data = data.get('mqtt', {})
    mqtt_config = MqttConfig(
        broker=os.getenv('MQTT_BROKER', mqtt_data.get('broker', 'localhost')),
        port=int(os.getenv('MQTT_PORT', mqtt_data.get('port', 1883))),
        username=os.getenv('MQTT_USERNAME', mqtt_data.get('username', '')),
        password=os.getenv('MQTT_PASSWORD', mqtt_data.get('password', '')),
        client_id=os.getenv('MQTT_CLIENT_ID', mqtt_data.get('client_id', 'ems-collector')),
        command_topic=mqtt_data.get('command_topic', 'ems/device/command')
    )

    return AppConfig(
        devices=devices,
        cache=cache_config,
        simulation=simulation_config,
        mqtt=mqtt_config
    )
