import struct
import logging
from typing import Any, Optional
from pymodbus.client import ModbusTcpClient
from pymodbus.exceptions import ModbusException, ConnectionException

logger = logging.getLogger(__name__)


class ModbusClient:
    def __init__(self, host: str, port: int = 502, timeout: int = 5):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.client: Optional[ModbusTcpClient] = None
        self._connected = False

    def connect(self) -> bool:
        try:
            if self.client is None:
                self.client = ModbusTcpClient(
                    host=self.host,
                    port=self.port,
                    timeout=self.timeout
                )
            self._connected = self.client.connect()
            return self._connected
        except ConnectionException as e:
            logger.error(f"Failed to connect to {self.host}:{self.port}: {e}")
            self._connected = False
            return False

    def disconnect(self):
        if self.client:
            self.client.close()
            self._connected = False

    def is_connected(self) -> bool:
        return self._connected and self.client and self.client.is_socket_open()

    def read_holding_registers(self, address: int, count: int, slave_id: int = 1) -> Optional[list]:
        if not self.is_connected() and not self.connect():
            raise ConnectionError(f"Not connected to {self.host}:{self.port}")

        try:
            result = self.client.read_holding_registers(
                address=address,
                count=count,
                slave=slave_id
            )
            if result.isError():
                logger.error(f"Modbus read error at address {address}: {result}")
                return None
            return result.registers
        except ModbusException as e:
            logger.error(f"Modbus exception at address {address}: {e}")
            self._connected = False
            return None

    def read_input_registers(self, address: int, count: int, slave_id: int = 1) -> Optional[list]:
        if not self.is_connected() and not self.connect():
            raise ConnectionError(f"Not connected to {self.host}:{self.port}")

        try:
            result = self.client.read_input_registers(
                address=address,
                count=count,
                slave=slave_id
            )
            if result.isError():
                logger.error(f"Modbus read error at address {address}: {result}")
                return None
            return result.registers
        except ModbusException as e:
            logger.error(f"Modbus exception at address {address}: {e}")
            self._connected = False
            return None

    def write_register(self, address: int, value: int, slave_id: int = 1) -> bool:
        if not self.is_connected() and not self.connect():
            raise ConnectionError(f"Not connected to {self.host}:{self.port}")

        try:
            result = self.client.write_register(
                address=address,
                value=value,
                slave=slave_id
            )
            return not result.isError()
        except ModbusException as e:
            logger.error(f"Modbus write exception at address {address}: {e}")
            self._connected = False
            return False

    @staticmethod
    def convert_registers(registers: list, data_type: str) -> Any:
        if not registers:
            return None

        try:
            if data_type == 'int16':
                return struct.unpack('>h', struct.pack('>H', registers[0]))[0]
            elif data_type == 'uint16':
                return registers[0]
            elif data_type == 'int32':
                value = (registers[0] << 16) | (registers[1] & 0xFFFF)
                return struct.unpack('>i', struct.pack('>I', value))[0]
            elif data_type == 'uint32':
                return (registers[0] << 16) | (registers[1] & 0xFFFF)
            elif data_type == 'float32':
                raw = struct.pack('>HH', registers[0], registers[1])
                return struct.unpack('>f', raw)[0]
            elif data_type == 'float64':
                raw = struct.pack('>HHHH', registers[0], registers[1], registers[2], registers[3])
                return struct.unpack('>d', raw)[0]
            elif data_type == 'boolean':
                return bool(registers[0])
            elif data_type == 'string':
                return ''.join(chr(b) for reg in registers for b in struct.pack('>H', reg)).rstrip('\x00')
            else:
                logger.warning(f"Unknown data type: {data_type}")
                return registers
        except Exception as e:
            logger.error(f"Error converting registers to {data_type}: {e}")
            return None

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.disconnect()
