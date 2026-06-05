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

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final TimeSeriesService timeSeriesService;
    private final RealtimeDataCache realtimeDataCache;
    private final DeviceRepository deviceRepository;

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
