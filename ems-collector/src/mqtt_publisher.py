import json
import time
import logging
from typing import Dict, Any, Optional, Callable
from threading import Thread
import paho.mqtt.client as mqtt

logger = logging.getLogger(__name__)


class MqttPublisher:
    def __init__(self, broker: str, port: int = 1883,
                 username: str = '', password: str = '',
                 client_id: str = 'ems-collector',
                 cache_manager=None,
                 command_callback: Optional[Callable] = None):
        self.broker = broker
        self.port = port
        self.username = username
        self.password = password
        self.client_id = client_id
        self.cache_manager = cache_manager
        self.command_callback = command_callback

        self.client: Optional[mqtt.Client] = None
        self._connected = False
        self._last_connect_attempt = 0
        self._reconnect_delay = 1
        self._command_topic = 'ems/device/command'

    def connect(self) -> bool:
        try:
            self.client = mqtt.Client(
                client_id=self.client_id,
                clean_session=True,
                protocol=mqtt.MQTTv311
            )

            if self.username:
                self.client.username_pw_set(self.username, self.password)

            self.client.on_connect = self._on_connect
            self.client.on_disconnect = self._on_disconnect
            self.client.on_message = self._on_message

            self.client.connect(
                host=self.broker,
                port=self.port,
                keepalive=60
            )

            self.client.loop_start()

            if self.cache_manager:
                self.cache_manager.set_publish_callback(self._retry_publish)

            return True
        except Exception as e:
            logger.error(f"Failed to connect to MQTT broker {self.broker}:{self.port}: {e}")
            self._connected = False
            return False

    def subscribe(self, topic: str, qos: int = 1) -> bool:
        if not self.is_connected():
            logger.warning(f"Cannot subscribe to {topic}: not connected")
            return False

        try:
            self.client.subscribe(topic, qos=qos)
            logger.info(f"Subscribed to topic: {topic}")
            return True
        except Exception as e:
            logger.error(f"Failed to subscribe to {topic}: {e}")
            return False

    def _retry_publish(self, topic: str, payload: Dict[str, Any]) -> bool:
        if not self.is_connected():
            raise Exception("MQTT not connected")
        return self.publish(topic, payload, qos=1)

    def disconnect(self):
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()
            self._connected = False

    def is_connected(self) -> bool:
        return self._connected and self.client is not None

    def set_command_topic(self, topic: str):
        self._command_topic = topic
        if self._connected:
            self.subscribe(topic, qos=1)

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self._connected = True
            self._reconnect_delay = 1
            logger.info(f"Connected to MQTT broker at {self.broker}:{self.port}")

            if self._command_topic:
                try:
                    self.client.subscribe(self._command_topic, qos=1)
                    logger.info(f"Subscribed to command topic: {self._command_topic}")
                except Exception as e:
                    logger.error(f"Failed to subscribe to command topic: {e}")
        else:
            logger.error(f"MQTT connection failed with code {rc}")
            self._connected = False

    def _on_disconnect(self, client, userdata, rc):
        self._connected = False
        logger.warning(f"Disconnected from MQTT broker, code: {rc}")

        if rc != 0:
            self._reconnect()

    def _reconnect(self):
        now = time.time()
        if now - self._last_connect_attempt < self._reconnect_delay:
            return

        self._last_connect_attempt = now
        logger.info(f"Attempting to reconnect to MQTT broker (delay: {self._reconnect_delay}s)")

        try:
            if self.client:
                self.client.reconnect()
        except Exception as e:
            logger.error(f"Reconnect failed: {e}")
            self._reconnect_delay = min(self._reconnect_delay * 2, 60)

    def _on_message(self, client, userdata, msg):
        try:
            logger.debug(f"Received MQTT message on topic: {msg.topic}")

            if msg.topic == self._command_topic and self.command_callback:
                payload = json.loads(msg.payload.decode('utf-8'))
                self.command_callback(msg.topic, payload)
        except Exception as e:
            logger.error(f"Error processing MQTT message: {e}")

    def publish(self, topic: str, payload: Dict[str, Any], qos: int = 1) -> bool:
        if not self.is_connected():
            if self.cache_manager:
                self.cache_manager.cache_data(topic, payload)
                logger.warning(f"MQTT not connected, cached data for topic: {topic}")
            return False

        try:
            payload_json = json.dumps(payload, ensure_ascii=False)
            result = self.client.publish(topic, payload_json, qos=qos)

            if result.rc != mqtt.MQTT_ERR_SUCCESS:
                logger.warning(f"Failed to publish to {topic}, RC: {result.rc}")
                if self.cache_manager:
                    self.cache_manager.cache_data(topic, payload)
                return False

            logger.debug(f"Published to {topic}: {payload_json[:100]}...")
            return True
        except Exception as e:
            logger.error(f"Error publishing to {topic}: {e}")
            if self.cache_manager:
                self.cache_manager.cache_data(topic, payload)
            return False

    def publish_with_retry(self, topic: str, payload: Dict[str, Any],
                           max_retries: int = 3, qos: int = 1) -> bool:
        for attempt in range(max_retries):
            if self.publish(topic, payload, qos):
                return True
            if attempt < max_retries - 1:
                time.sleep(0.1 * (attempt + 1))

        logger.error(f"Failed to publish to {topic} after {max_retries} attempts")
        return False
