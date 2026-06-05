import random
import time
import logging
from typing import Dict, Any
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class SimulatedState:
    base_values: Dict[str, float]
    last_update: float


class DataSimulator:
    def __init__(self, device_type: str, location: str = ''):
        self.device_type = device_type
        self.location = location
        self.state = SimulatedState(base_values={}, last_update=time.time())
        self._initialize_base_values()

    def _initialize_base_values(self):
        if self.device_type == 'meter':
            self.state.base_values = {
                'voltage_a': 220.0,
                'voltage_b': 220.0,
                'voltage_c': 220.0,
                'current_a': 50.0,
                'current_b': 48.0,
                'current_c': 52.0,
                'active_power': 35.0,
                'reactive_power': 8.0,
                'apparent_power': 36.5,
                'power_factor': 0.95,
                'frequency': 50.0,
                'total_active_energy': 125600.5,
                'total_reactive_energy': 25600.2,
                'import_active_energy': 120000.0,
                'export_active_energy': 5600.5,
                'demand': 38.0,
                'thd_voltage_a': 2.5,
                'thd_current_a': 3.2
            }
        elif self.device_type == 'pv':
            hour = time.localtime().tm_hour
            is_daytime = 6 <= hour <= 18
            base_irradiance = 800 if is_daytime else 0

            self.state.base_values = {
                'dc_voltage': 650.0 if is_daytime else 100.0,
                'dc_current': 80.0 if is_daytime else 0.1,
                'dc_power': 52.0 if is_daytime else 0.01,
                'ac_voltage_a': 225.0,
                'ac_voltage_b': 224.5,
                'ac_voltage_c': 225.2,
                'ac_current_a': 100.0 if is_daytime else 0.1,
                'ac_current_b': 98.5 if is_daytime else 0.1,
                'ac_current_c': 101.0 if is_daytime else 0.1,
                'ac_power': 50.0 if is_daytime else 0.01,
                'ac_reactive_power': 2.0 if is_daytime else 0.0,
                'power_factor': 0.98,
                'frequency': 50.02,
                'efficiency': 96.5 if is_daytime else 0.0,
                'total_energy': 85600.0,
                'daily_energy': 320.5 if is_daytime else 320.0,
                'module_temperature': 45.0 if is_daytime else 20.0,
                'ambient_temperature': 25.0 if is_daytime else 18.0,
                'irradiance': base_irradiance,
                'operating_status': 1 if is_daytime else 0,
                'fault_code': 0
            }
        elif self.device_type == 'bms':
            self.state.base_values = {
                'soc': 65.0,
                'soh': 92.5,
                'total_voltage': 640.0,
                'total_current': 25.0,
                'max_cell_voltage': 3.65,
                'min_cell_voltage': 3.62,
                'max_cell_voltage_no': 5,
                'min_cell_voltage_no': 12,
                'avg_cell_voltage': 3.63,
                'max_temperature': 28.5,
                'min_temperature': 25.0,
                'avg_temperature': 26.8,
                'max_temp_no': 3,
                'min_temp_no': 8,
                'charge_current_limit': 100.0,
                'discharge_current_limit': 150.0,
                'max_charge_power': 64.0,
                'max_discharge_power': 96.0,
                'cycle_count': 456,
                'capacity': 500.0,
                'remaining_capacity': 325.0,
                'design_capacity': 500.0,
                'bms_status': 1,
                'charge_enable': 1,
                'discharge_enable': 1,
                'heating_enable': 0,
                'fault_code': 0,
                'warning_code': 0,
                'protection_code': 0,
                'cell_count': 176,
                'temp_sensor_count': 16
            }
        elif self.device_type == 'pcs':
            self.state.base_values = {
                'dc_voltage': 640.0,
                'dc_current': 30.0,
                'dc_power': 19.2,
                'ac_voltage_a': 226.0,
                'ac_voltage_b': 225.5,
                'ac_voltage_c': 226.2,
                'ac_current_a': 28.0,
                'ac_current_b': 27.5,
                'ac_current_c': 28.2,
                'active_power': 18.5,
                'reactive_power': 2.0,
                'apparent_power': 18.8,
                'power_factor': 0.98,
                'frequency': 50.01,
                'efficiency': 97.2,
                'total_charge_energy': 45600.0,
                'total_discharge_energy': 42300.0,
                'daily_charge_energy': 156.0,
                'daily_discharge_energy': 180.0,
                'grid_voltage': 228.0,
                'grid_frequency': 50.02,
                'inverter_temperature': 35.0,
                'heat_sink_temperature': 38.5,
                'running_status': 1,
                'work_mode': 2,
                'control_mode': 1,
                'power_setpoint': 20.0,
                'reactive_power_setpoint': 0.0,
                'grid_connect_status': 1,
                'fault_code': 0,
                'warning_code': 0,
                'dc_max_voltage': 800.0,
                'dc_min_voltage': 400.0,
                'ac_max_current': 300.0
            }

    def _vary_value(self, value: float, variance: float = 0.02) -> float:
        if value == 0:
            return 0
        delta = value * random.uniform(-variance, variance)
        return value + delta

    def generate_data(self) -> Dict[str, Any]:
        data = {}
        current_time = time.time()
        time_delta = current_time - self.state.last_update

        for key, base_value in self.state.base_values.items():
            if isinstance(base_value, bool):
                data[key] = base_value
            elif isinstance(base_value, int):
                if key in ['operating_status', 'bms_status', 'running_status',
                           'work_mode', 'control_mode', 'grid_connect_status',
                           'charge_enable', 'discharge_enable', 'heating_enable']:
                    data[key] = base_value
                else:
                    data[key] = int(base_value + random.randint(-2, 2))
            elif isinstance(base_value, float):
                if key.endswith('_energy') and base_value > 0:
                    base_value += 0.01 * time_delta / 3600
                    self.state.base_values[key] = base_value
                variance = 0.02
                if key.startswith('voltage_'):
                    variance = 0.005
                elif key == 'frequency':
                    variance = 0.001
                elif key == 'soc' or key == 'soh':
                    variance = 0.005
                data[key] = round(self._vary_value(base_value, variance), 4)
            else:
                data[key] = base_value

        self.state.last_update = current_time
        data['timestamp'] = int(current_time * 1000)
        return data

    def apply_command(self, command: str, params: Dict[str, Any]):
        if command == 'POWER_CONTROL':
            target_power = params.get('targetPower', 0)
            if self.device_type == 'pcs':
                self.state.base_values['active_power'] = abs(target_power)
                self.state.base_values['power_setpoint'] = abs(target_power)
                if target_power > 0:
                    self.state.base_values['total_current'] = abs(target_power) * 1000 / self.state.base_values.get('total_voltage', 640)
                    self.state.base_values['dc_power'] = abs(target_power)
                else:
                    self.state.base_values['total_current'] = -abs(target_power) * 1000 / self.state.base_values.get('total_voltage', 640)
                    self.state.base_values['dc_power'] = -abs(target_power)
        elif command == 'START_CHARGE':
            power = params.get('power', 50)
            if self.device_type == 'pcs':
                self.state.base_values['active_power'] = abs(power)
                self.state.base_values['power_setpoint'] = abs(power)
            if self.device_type == 'bms':
                self.state.base_values['charge_enable'] = 1
                self.state.base_values['total_current'] = abs(power) * 1000 / self.state.base_values.get('total_voltage', 640)
        elif command == 'START_DISCHARGE':
            power = params.get('power', 50)
            if self.device_type == 'pcs':
                self.state.base_values['active_power'] = -abs(power)
                self.state.base_values['power_setpoint'] = -abs(power)
            if self.device_type == 'bms':
                self.state.base_values['discharge_enable'] = 1
                self.state.base_values['total_current'] = -abs(power) * 1000 / self.state.base_values.get('total_voltage', 640)
        elif command == 'STOP':
            if self.device_type == 'pcs':
                self.state.base_values['active_power'] = 0
                self.state.base_values['power_setpoint'] = 0
                self.state.base_values['running_status'] = 0
            if self.device_type == 'bms':
                self.state.base_values['charge_enable'] = 0
                self.state.base_values['discharge_enable'] = 0
                self.state.base_values['total_current'] = 0
                self.state.base_values['bms_status'] = 0
        elif command == 'QUERY_STATUS':
            pass

        logger.info(f"Applied command {command} to {self.device_type}: {params}")
