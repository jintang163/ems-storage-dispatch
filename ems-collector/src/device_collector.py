import time
import logging
from typing import Dict, Any
from threading import Thread, Event
from dataclasses import dataclass

from .config import DeviceConfig
from .modbus_client import ModbusClient
from .mqtt_publisher import MqttPublisher

logger = logging.getLogger(__name__)


@dataclass
class CollectorStatus:
    running: bool = False
    last_collect_time: float = 0
    collect_count: int = 0
    error_count: int = 0
    last_error: str = ''


class DeviceCollector:
    def __init__(self, device_config: DeviceConfig,
                 mqtt_publisher: MqttPublisher):
        self.config = device_config
        self.mqtt_publisher = mqtt_publisher

        self.modbus_client: ModbusClient | None = None
        self.collector_thread: Thread | None = None
        self.stop_event = Event()
        self.status = CollectorStatus()

        self.topic_map = {
            'meter': f"ems/device/meter/{device_config.device_sn}/data",
            'pv': f"ems/device/pv/{device_config.device_sn}/data",
            'bms': f"ems/device/bms/{device_config.device_sn}/data",
            'pcs': f"ems/device/pcs/{device_config.device_sn}/data",
        }
        self.status_topic = f"ems/device/{device_config.device_sn}/status"

        self.modbus_client = ModbusClient(
            host=device_config.host,
            port=device_config.port
        )

    def _get_topic(self) -> str:
        return self.topic_map.get(self.config.device_type, '')

    def start(self):
        if not self.config.enabled:
            logger.info(f"Device {self.config.device_sn} is disabled, skipping")
            return

        if self.collector_thread and self.collector_thread.is_alive():
            logger.warning(f"Collector for {self.config.device_sn} is already running")
            return

        self.stop_event.clear()
        self.status.running = True
        self.collector_thread = Thread(
            target=self._collect_loop,
            name=f"collector-{self.config.device_sn}",
            daemon=True
        )
        self.collector_thread.start()

        self._publish_status('online')
        logger.info(f"Started collector for device: {self.config.device_sn}")

    def stop(self):
        self.stop_event.set()
        self.status.running = False

        if self.collector_thread:
            self.collector_thread.join(timeout=5)
            self.collector_thread = None

        if self.modbus_client:
            self.modbus_client.disconnect()

        self._publish_status('offline')
        logger.info(f"Stopped collector for device: {self.config.device_sn}")

    def _collect_loop(self):
        interval = self.config.sampling_interval / 1000.0

        while not self.stop_event.is_set():
            try:
                data = self._collect_data()
                if data:
                    self._publish_data(data)
                    self.status.collect_count += 1
                    self.status.last_collect_time = time.time()
            except Exception as e:
                self.status.error_count += 1
                self.status.last_error = str(e)
                logger.error(f"Error collecting data from {self.config.device_sn}: {e}")

            self.stop_event.wait(interval)

    def _collect_data(self) -> Dict[str, Any] | None:
        if not self.modbus_client:
            return None

        data = {
            'deviceSn': self.config.device_sn,
            'location': self.config.location,
            'timestamp': int(time.time() * 1000)
        }

        try:
            for point in self.config.points:
                if not self.modbus_client.is_connected():
                    if not self.modbus_client.connect():
                        logger.warning(f"Cannot connect to {self.config.device_sn}")
                        return None

                registers = self.modbus_client.read_holding_registers(
                    address=point.address,
                    count=point.count,
                    slave_id=self.config.slave_id
                )

                if registers is None:
                    logger.warning(f"Failed to read {point.name} from {self.config.device_sn}")
                    data[point.name] = None
                    continue

                value = self.modbus_client.convert_registers(registers, point.data_type)
                if value is not None and point.scale != 1:
                    value = round(value * point.scale, 4)

                data[point.name] = value

            return data
        except Exception as e:
            logger.error(f"Modbus collection error for {self.config.device_sn}: {e}")
            return None

    def _publish_data(self, data: Dict[str, Any]):
        topic = self._get_topic()
        if not topic:
            logger.warning(f"No topic defined for device type: {self.config.device_type}")
            return

        self.mqtt_publisher.publish(topic, data)

    def _publish_status(self, status: str):
        payload = {
            'deviceSn': self.config.device_sn,
            'status': status,
            'timestamp': int(time.time() * 1000)
        }
        self.mqtt_publisher.publish(self.status_topic, payload)

    def apply_command(self, command: str, params: Dict[str, Any]):
        if not self.modbus_client:
            logger.warning(f"No Modbus client available for {self.config.device_sn}")
            return
        try:
            if command == 'POWER_CONTROL' and 'targetPower' in params:
                self._write_power_control(params['targetPower'])
            elif command == 'START_CHARGE':
                self._write_start_charge(params.get('power', 0))
            elif command == 'START_DISCHARGE':
                self._write_start_discharge(params.get('power', 0))
            elif command == 'STOP':
                self._write_stop()
        except Exception as e:
            logger.error(f"Failed to apply command to {self.config.device_sn}: {e}")

    def _write_power_control(self, target_power: float):
        if not self.modbus_client or not self.modbus_client.is_connected():
            return
        register_value = int(abs(target_power) * 10)
        if target_power < 0:
            register_value |= 0x8000
        self.modbus_client.write_register(
            address=100,
            value=register_value,
            slave_id=self.config.slave_id
        )

    def _write_start_charge(self, power: float):
        if not self.modbus_client or not self.modbus_client.is_connected():
            return
        self.modbus_client.write_register(
            address=101,
            value=1,
            slave_id=self.config.slave_id
        )
        if power > 0:
            self.modbus_client.write_register(
                address=102,
                value=int(power * 10),
                slave_id=self.config.slave_id
            )

    def _write_start_discharge(self, power: float):
        if not self.modbus_client or not self.modbus_client.is_connected():
            return
        self.modbus_client.write_register(
            address=103,
            value=1,
            slave_id=self.config.slave_id
        )
        if power > 0:
            self.modbus_client.write_register(
                address=104,
                value=int(power * 10),
                slave_id=self.config.slave_id
            )

    def _write_stop(self):
        if not self.modbus_client or not self.modbus_client.is_connected():
            return
        self.modbus_client.write_register(
            address=101,
            value=0,
            slave_id=self.config.slave_id
        )
        self.modbus_client.write_register(
            address=103,
            value=0,
            slave_id=self.config.slave_id
        )

    def get_status(self) -> Dict[str, Any]:
        return {
            'device_sn': self.config.device_sn,
            'device_type': self.config.device_type,
            'running': self.status.running,
            'last_collect_time': self.status.last_collect_time,
            'collect_count': self.status.collect_count,
            'error_count': self.status.error_count,
            'last_error': self.status.last_error,
            'connected': self.modbus_client.is_connected() if self.modbus_client else False
        }
