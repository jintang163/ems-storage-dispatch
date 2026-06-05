package com.ems.mqtt.handler;

import com.alibaba.fastjson2.JSON;
import com.ems.common.constants.EmsConstants;
import com.ems.common.enums.DeviceTypeEnum;
import com.ems.common.exception.EmsException;
import com.ems.common.utils.TopicUtils;
import com.ems.domain.dto.mqtt.*;
import com.ems.domain.tsdb.*;
import com.ems.influxdb.service.RealtimeDataCache;
import com.ems.influxdb.service.TimeSeriesService;
import com.ems.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * MQTT消息处理器
 * 负责接收Python采集端通过MQTT协议上报的设备实时数据，并进行以下处理：
 * 1. 根据主题区分消息类型（状态上报、电表数据、光伏数据、BMS数据、PCS数据）
 * 2. 解析JSON消息体，通过@JSONField注解映射Python snake_case字段到Java camelCase
 * 3. 将数据转换为InfluxDB实体对象
 * 4. 异步写入时序数据库进行持久化存储
 * 5. 更新实时数据缓存，供前端查询展示
 * 6. 更新设备在线状态
 *
 * 数据链路：Python采集端 -> MQTT Broker -> MqttMessageHandler -> InfluxDB + Cache
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

    /**
     * 时序数据库服务，负责异步写入InfluxDB
     */
    private final TimeSeriesService timeSeriesService;

    /**
     * 实时数据缓存，存储最新的设备数据供前端快速查询
     */
    private final RealtimeDataCache realtimeDataCache;

    /**
     * 设备数据仓库，负责PostgreSQL中设备状态的更新
     */
    private final DeviceRepository deviceRepository;

    /**
     * MQTT消息入口方法
     * Spring Integration通过@ServiceActivator自动将mqttInputChannel通道的消息路由到此方法
     * 处理流程：
     * 1. 从消息头获取MQTT主题
     * 2. 根据主题模式分发给对应的处理方法
     * 3. 发生异常时记录日志并抛出，由事务管理器回滚
     *
     * @param message MQTT消息，包含消息头（主题等元数据）和消息体（JSON数据）
     * @throws EmsException 消息处理失败时抛出
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    @Transactional(rollbackFor = Exception.class)
    public void handleMessage(Message<String> message) {
        MessageHeaders headers = message.getHeaders();
        String topic = headers.get("mqtt_receivedTopic", String.class);
        String payload = message.getPayload();

        if (topic == null) {
            log.warn("Received message without topic");
            return;
        }

        log.debug("Received MQTT message on topic: {}, payload: {}", topic, payload);

        try {
            if (topic.contains("status")) {
                handleStatusMessage(topic, payload);
            } else if (topic.contains("meter")) {
                handleMeterData(topic, payload);
            } else if (topic.contains("/pv/")) {
                handlePvData(topic, payload);
            } else if (topic.contains("/bms/")) {
                handleBmsData(topic, payload);
            } else if (topic.contains("/pcs/")) {
                handlePcsData(topic, payload);
            } else {
                log.warn("Unknown topic pattern: {}", topic);
            }
        } catch (Exception e) {
            log.error("Error handling MQTT message on topic: {}", topic, e);
            throw new EmsException("Failed to handle MQTT message", e);
        }
    }

    /**
     * 处理设备状态消息
     * 当设备上线、离线或状态变更时触发，更新PostgreSQL中设备的运行状态
     * 优先从主题中提取设备编号，若主题解析失败则从消息体中获取
     *
     * @param topic   MQTT主题，格式：ems/device/{deviceSn}/status
     * @param payload JSON消息体，包含设备状态信息
     */
    private void handleStatusMessage(String topic, String payload) {
        DeviceStatusPayload status = JSON.parseObject(payload, DeviceStatusPayload.class);
        String deviceSn = TopicUtils.extractDeviceSnFromTopic(topic);
        if (deviceSn == null) {
            deviceSn = status.getDeviceSn();
        }

        if (deviceSn != null) {
            deviceRepository.updateStatus(
                    deviceSn,
                    status.getStatus() != null ? status.getStatus() : EmsConstants.DEVICE_STATUS_ONLINE,
                    LocalDateTime.now()
            );
            log.info("Device status updated: {} -> {}", deviceSn, status.getStatus());
        }
    }

    /**
     * 处理电表数据
     * 接收电表实时采集的电压、电流、功率、电能、谐波等数据
     * 处理流程：
     * 1. 使用fastjson2解析JSON消息体到MeterDataPayload（自动映射snake_case到camelCase）
     * 2. 提取设备编号
     * 3. 将DTO转换为InfluxDB实体对象MeterData
     * 4. 异步写入InfluxDB时序数据库
     * 5. 更新实时数据缓存
     * 6. 更新设备在线状态
     *
     * @param topic   MQTT主题，格式：ems/device/meter/{deviceSn}/data
     * @param payload JSON消息体，包含电表采集的所有用电数据
     */
    private void handleMeterData(String topic, String payload) {
        MeterDataPayload dataPayload = JSON.parseObject(payload, MeterDataPayload.class);
        String deviceSn = TopicUtils.extractDeviceSnFromTopic(topic);
        if (deviceSn == null) {
            deviceSn = dataPayload.getDeviceSn();
        }

        if (deviceSn == null) {
            log.warn("Cannot determine device SN for meter data");
            return;
        }

        MeterData data = new MeterData();
        data.setDeviceSn(deviceSn);
        data.setLocation(dataPayload.getLocation());
        data.setVoltageA(dataPayload.getVoltageA());
        data.setVoltageB(dataPayload.getVoltageB());
        data.setVoltageC(dataPayload.getVoltageC());
        data.setCurrentA(dataPayload.getCurrentA());
        data.setCurrentB(dataPayload.getCurrentB());
        data.setCurrentC(dataPayload.getCurrentC());
        data.setActivePower(dataPayload.getActivePower());
        data.setReactivePower(dataPayload.getReactivePower());
        data.setApparentPower(dataPayload.getApparentPower());
        data.setPowerFactor(dataPayload.getPowerFactor());
        data.setFrequency(dataPayload.getFrequency());
        data.setTotalActiveEnergy(dataPayload.getTotalActiveEnergy());
        data.setTotalReactiveEnergy(dataPayload.getTotalReactiveEnergy());
        data.setImportActiveEnergy(dataPayload.getImportActiveEnergy());
        data.setExportActiveEnergy(dataPayload.getExportActiveEnergy());
        data.setDemand(dataPayload.getDemand());
        data.setThdVoltageA(dataPayload.getThdVoltageA());
        data.setThdCurrentA(dataPayload.getThdCurrentA());
        data.setTimestamp(dataPayload.getTimestampInstant());

        timeSeriesService.writeMeterDataAsync(data);
        realtimeDataCache.putMeterData(deviceSn, data);

        deviceRepository.updateStatus(deviceSn, EmsConstants.DEVICE_STATUS_ONLINE, LocalDateTime.now());
    }

    /**
     * 处理光伏逆变器数据
     * 接收光伏逆变器采集的直流侧、交流侧、发电量、温度等数据
     * 处理流程与handleMeterData一致
     *
     * @param topic   MQTT主题，格式：ems/device/pv/{deviceSn}/data
     * @param payload JSON消息体，包含光伏逆变器采集数据
     */
    private void handlePvData(String topic, String payload) {
        PvDataPayload dataPayload = JSON.parseObject(payload, PvDataPayload.class);
        String deviceSn = TopicUtils.extractDeviceSnFromTopic(topic);
        if (deviceSn == null) {
            deviceSn = dataPayload.getDeviceSn();
        }

        if (deviceSn == null) {
            log.warn("Cannot determine device SN for PV data");
            return;
        }

        PvData data = new PvData();
        data.setDeviceSn(deviceSn);
        data.setLocation(dataPayload.getLocation());
        data.setDcVoltage(dataPayload.getDcVoltage());
        data.setDcCurrent(dataPayload.getDcCurrent());
        data.setDcPower(dataPayload.getDcPower());
        data.setAcVoltageA(dataPayload.getAcVoltageA());
        data.setAcVoltageB(dataPayload.getAcVoltageB());
        data.setAcVoltageC(dataPayload.getAcVoltageC());
        data.setAcCurrentA(dataPayload.getAcCurrentA());
        data.setAcCurrentB(dataPayload.getAcCurrentB());
        data.setAcCurrentC(dataPayload.getAcCurrentC());
        data.setAcPower(dataPayload.getAcPower());
        data.setAcReactivePower(dataPayload.getAcReactivePower());
        data.setPowerFactor(dataPayload.getPowerFactor());
        data.setFrequency(dataPayload.getFrequency());
        data.setEfficiency(dataPayload.getEfficiency());
        data.setTotalEnergy(dataPayload.getTotalEnergy());
        data.setDailyEnergy(dataPayload.getDailyEnergy());
        data.setModuleTemperature(dataPayload.getModuleTemperature());
        data.setAmbientTemperature(dataPayload.getAmbientTemperature());
        data.setIrradiance(dataPayload.getIrradiance());
        data.setOperatingStatus(dataPayload.getOperatingStatus());
        data.setFaultCode(dataPayload.getFaultCode());
        data.setTimestamp(dataPayload.getTimestampInstant());

        timeSeriesService.writePvDataAsync(data);
        realtimeDataCache.putPvData(deviceSn, data);

        deviceRepository.updateStatus(deviceSn, EmsConstants.DEVICE_STATUS_ONLINE, LocalDateTime.now());
    }

    /**
     * 处理BMS电池管理系统数据
     * 接收BMS采集的SOC、SOH、电压、电流、温度、告警等数据
     * BMS数据是储能系统安全运行的关键，包含电池状态用于充放电控制决策的重要依据
     * 处理流程与handleMeterData一致
     *
     * @param topic   MQTT主题，格式：ems/device/bms/{deviceSn}/data
     * @param payload JSON消息体，包含BMS采集数据
     */
    private void handleBmsData(String topic, String payload) {
        BmsDataPayload dataPayload = JSON.parseObject(payload, BmsDataPayload.class);
        String deviceSn = TopicUtils.extractDeviceSnFromTopic(topic);
        if (deviceSn == null) {
            deviceSn = dataPayload.getDeviceSn();
        }

        if (deviceSn == null) {
            log.warn("Cannot determine device SN for BMS data");
            return;
        }

        BmsData data = new BmsData();
        data.setDeviceSn(deviceSn);
        data.setLocation(dataPayload.getLocation());
        data.setSoc(dataPayload.getSoc());
        data.setSoh(dataPayload.getSoh());
        data.setTotalVoltage(dataPayload.getTotalVoltage());
        data.setTotalCurrent(dataPayload.getTotalCurrent());
        data.setMaxCellVoltage(dataPayload.getMaxCellVoltage());
        data.setMinCellVoltage(dataPayload.getMinCellVoltage());
        data.setMaxCellVoltageNo(dataPayload.getMaxCellVoltageNo());
        data.setMinCellVoltageNo(dataPayload.getMinCellVoltageNo());
        data.setAvgCellVoltage(dataPayload.getAvgCellVoltage());
        data.setMaxTemperature(dataPayload.getMaxTemperature());
        data.setMinTemperature(dataPayload.getMinTemperature());
        data.setAvgTemperature(dataPayload.getAvgTemperature());
        data.setMaxTempNo(dataPayload.getMaxTempNo());
        data.setMinTempNo(dataPayload.getMinTempNo());
        data.setChargeCurrentLimit(dataPayload.getChargeCurrentLimit());
        data.setDischargeCurrentLimit(dataPayload.getDischargeCurrentLimit());
        data.setMaxChargePower(dataPayload.getMaxChargePower());
        data.setMaxDischargePower(dataPayload.getMaxDischargePower());
        data.setCycleCount(dataPayload.getCycleCount());
        data.setCapacity(dataPayload.getCapacity());
        data.setRemainingCapacity(dataPayload.getRemainingCapacity());
        data.setDesignCapacity(dataPayload.getDesignCapacity());
        data.setBmsStatus(dataPayload.getBmsStatus());
        data.setChargeEnable(dataPayload.getChargeEnable());
        data.setDischargeEnable(dataPayload.getDischargeEnable());
        data.setHeatingEnable(dataPayload.getHeatingEnable());
        data.setFaultCode(dataPayload.getFaultCode());
        data.setWarningCode(dataPayload.getWarningCode());
        data.setProtectionCode(dataPayload.getProtectionCode());
        data.setCellCount(dataPayload.getCellCount());
        data.setTempSensorCount(dataPayload.getTempSensorCount());
        data.setTimestamp(dataPayload.getTimestampInstant());

        timeSeriesService.writeBmsDataAsync(data);
        realtimeDataCache.putBmsData(deviceSn, data);

        deviceRepository.updateStatus(deviceSn, EmsConstants.DEVICE_STATUS_ONLINE, LocalDateTime.now());
    }

    /**
     * 处理PCS储能变流器数据
     * 接收PCS采集的充放电功率、运行状态、并网状态等数据
     * PCS是储能系统的核心执行设备，负责电池与电网之间的能量转换
     * activePower字段正负值含义：正值表示放电（电池向电网送电），负值表示充电（电网向电池充电）
     * 处理流程与handleMeterData一致
     *
     * @param topic   MQTT主题，格式：ems/device/pcs/{deviceSn}/data
     * @param payload JSON消息体，包含PCS采集数据
     */
    private void handlePcsData(String topic, String payload) {
        PcsDataPayload dataPayload = JSON.parseObject(payload, PcsDataPayload.class);
        String deviceSn = TopicUtils.extractDeviceSnFromTopic(topic);
        if (deviceSn == null) {
            deviceSn = dataPayload.getDeviceSn();
        }

        if (deviceSn == null) {
            log.warn("Cannot determine device SN for PCS data");
            return;
        }

        PcsData data = new PcsData();
        data.setDeviceSn(deviceSn);
        data.setLocation(dataPayload.getLocation());
        data.setDcVoltage(dataPayload.getDcVoltage());
        data.setDcCurrent(dataPayload.getDcCurrent());
        data.setDcPower(dataPayload.getDcPower());
        data.setAcVoltageA(dataPayload.getAcVoltageA());
        data.setAcVoltageB(dataPayload.getAcVoltageB());
        data.setAcVoltageC(dataPayload.getAcVoltageC());
        data.setAcCurrentA(dataPayload.getAcCurrentA());
        data.setAcCurrentB(dataPayload.getAcCurrentB());
        data.setAcCurrentC(dataPayload.getAcCurrentC());
        data.setActivePower(dataPayload.getActivePower());
        data.setReactivePower(dataPayload.getReactivePower());
        data.setApparentPower(dataPayload.getApparentPower());
        data.setPowerFactor(dataPayload.getPowerFactor());
        data.setFrequency(dataPayload.getFrequency());
        data.setEfficiency(dataPayload.getEfficiency());
        data.setTotalChargeEnergy(dataPayload.getTotalChargeEnergy());
        data.setTotalDischargeEnergy(dataPayload.getTotalDischargeEnergy());
        data.setDailyChargeEnergy(dataPayload.getDailyChargeEnergy());
        data.setDailyDischargeEnergy(dataPayload.getDailyDischargeEnergy());
        data.setGridVoltage(dataPayload.getGridVoltage());
        data.setGridFrequency(dataPayload.getGridFrequency());
        data.setInverterTemperature(dataPayload.getInverterTemperature());
        data.setHeatSinkTemperature(dataPayload.getHeatSinkTemperature());
        data.setRunningStatus(dataPayload.getRunningStatus());
        data.setWorkMode(dataPayload.getWorkMode());
        data.setControlMode(dataPayload.getControlMode());
        data.setPowerSetpoint(dataPayload.getPowerSetpoint());
        data.setReactivePowerSetpoint(dataPayload.getReactivePowerSetpoint());
        data.setGridConnectStatus(dataPayload.getGridConnectStatus());
        data.setFaultCode(dataPayload.getFaultCode());
        data.setWarningCode(dataPayload.getWarningCode());
        data.setDcMaxVoltage(dataPayload.getDcMaxVoltage());
        data.setDcMinVoltage(dataPayload.getDcMinVoltage());
        data.setAcMaxCurrent(dataPayload.getAcMaxCurrent());
        data.setTimestamp(dataPayload.getTimestampInstant());

        timeSeriesService.writePcsDataAsync(data);
        realtimeDataCache.putPcsData(deviceSn, data);

        deviceRepository.updateStatus(deviceSn, EmsConstants.DEVICE_STATUS_ONLINE, LocalDateTime.now());
    }
}
