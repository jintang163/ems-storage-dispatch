import os
import sys
import time
import logging
import signal
from typing import Dict, Any
from dotenv import load_dotenv

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src.config import load_config, AppConfig
from src.mqtt_publisher import MqttPublisher
from src.cache_manager import CacheManager
from src.device_collector import DeviceCollector

load_dotenv()

LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')
LOG_FILE = os.getenv('LOG_FILE', './logs/collector.log')

os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE, encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)

logger = logging.getLogger(__name__)


class CollectorService:
    def __init__(self, config: AppConfig):
        self.config = config
        self.collectors: Dict[str, DeviceCollector] = {}
        self.running = False

        self.cache_manager = CacheManager(
            cache_path=config.cache.path,
            max_file_size=config.cache.max_file_size,
            retry_interval=config.cache.retry_interval,
            enabled=config.cache.enabled
        )

        self.mqtt_publisher = MqttPublisher(
            broker=config.mqtt.broker,
            port=config.mqtt.port,
            username=config.mqtt.username,
            password=config.mqtt.password,
            client_id=config.mqtt.client_id,
            cache_manager=self.cache_manager,
            command_callback=self._handle_command
        )

        self._initialize_collectors()

    def _initialize_collectors(self):
        for device_config in self.config.devices:
            if not device_config.enabled:
                logger.info(f"Skipping disabled device: {device_config.device_sn}")
                continue

            collector = DeviceCollector(
                device_config=device_config,
                mqtt_publisher=self.mqtt_publisher
            )
            self.collectors[device_config.device_sn] = collector
            logger.info(f"Initialized collector for device: {device_config.device_sn}")

    def _handle_command(self, topic: str, payload: Dict[str, Any]):
        try:
            device_sn = payload.get('deviceSn')
            command = payload.get('command')
            params = payload.get('params', {})

            if not device_sn or not command:
                logger.warning(f"Invalid command payload: {payload}")
                return

            collector = self.collectors.get(device_sn)
            if not collector:
                logger.warning(f"No collector found for device: {device_sn}")
                return

            logger.info(f"Received command '{command}' for device: {device_sn}")
            collector.apply_command(command, params)
        except Exception as e:
            logger.error(f"Error handling command: {e}")

    def start(self):
        if self.running:
            logger.warning("Collector service is already running")
            return

        self.running = True
        logger.info("Starting collector service...")

        self.cache_manager.start()

        self.mqtt_publisher.connect()
        self.mqtt_publisher.subscribe(self.config.mqtt.command_topic)

        for collector in self.collectors.values():
            collector.start()

        logger.info("Collector service started successfully")

    def stop(self):
        if not self.running:
            return

        self.running = False
        logger.info("Stopping collector service...")

        for collector in self.collectors.values():
            try:
                collector.stop()
            except Exception as e:
                logger.error(f"Error stopping collector: {e}")

        try:
            self.cache_manager.stop()
        except Exception as e:
            logger.error(f"Error stopping cache manager: {e}")

        try:
            self.mqtt_publisher.disconnect()
        except Exception as e:
            logger.error(f"Error disconnecting MQTT: {e}")

        logger.info("Collector service stopped")

    def get_status(self) -> Dict[str, Any]:
        return {
            'running': self.running,
            'collectors': {
                sn: collector.get_status()
                for sn, collector in self.collectors.items()
            },
            'mqtt_connected': self.mqtt_publisher.is_connected(),
            'cache_enabled': self.cache_manager.enabled
        }

    def _signal_handler(self, signum, frame):
        logger.info(f"Received signal {signum}, shutting down...")
        self.stop()
        sys.exit(0)


def main():
    try:
        config_path = os.getenv('CONFIG_PATH', 'config/devices.yaml')
        config = load_config(config_path)

        service = CollectorService(config)

        signal.signal(signal.SIGINT, service._signal_handler)
        signal.signal(signal.SIGTERM, service._signal_handler)

        service.start()

        while service.running:
            try:
                time.sleep(5)
                status = service.get_status()
                running_count = sum(
                    1 for c in status['collectors'].values()
                    if c['running']
                )
                logger.debug(
                    f"Status: {running_count}/{len(status['collectors'])} "
                    f"collectors running, MQTT: {status['mqtt_connected']}"
                )
            except KeyboardInterrupt:
                break
            except Exception as e:
                logger.error(f"Error in main loop: {e}")
                time.sleep(1)

    except Exception as e:
        logger.error(f"Fatal error in main: {e}", exc_info=True)
        sys.exit(1)


if __name__ == '__main__':
    main()