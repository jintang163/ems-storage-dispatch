package com.ems.mqtt.service;

import com.alibaba.fastjson2.JSON;
import com.ems.common.constants.EmsConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttPublisherService {

    private final MqttPahoMessageHandler mqttPahoMessageHandler;

    public void sendCommand(String deviceSn, String commandType, Object params) {
        Map<String, Object> message = new HashMap<>();
        message.put("deviceSn", deviceSn);
        message.put("commandType", commandType);
        message.put("params", params);
        message.put("timestamp", System.currentTimeMillis());

        String payload = JSON.toJSONString(message);
        sendMessage(EmsConstants.COMMAND_TOPIC, payload);
    }

    public void sendPowerControl(String deviceSn, double targetPower, int duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("targetPower", targetPower);
        params.put("duration", duration);
        sendCommand(deviceSn, "POWER_CONTROL", params);
    }

    public void sendStartCharge(String deviceSn, double power) {
        Map<String, Object> params = new HashMap<>();
        params.put("power", power);
        sendCommand(deviceSn, "START_CHARGE", params);
    }

    public void sendStartDischarge(String deviceSn, double power) {
        Map<String, Object> params = new HashMap<>();
        params.put("power", power);
        sendCommand(deviceSn, "START_DISCHARGE", params);
    }

    public void sendStop(String deviceSn) {
        sendCommand(deviceSn, "STOP", null);
    }

    public void sendQueryStatus(String deviceSn) {
        sendCommand(deviceSn, "QUERY_STATUS", null);
    }

    public void sendMessage(String topic, String payload) {
        try {
            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader("mqtt_topic", topic)
                    .setHeader("mqtt_qos", 1)
                    .build();
            mqttPahoMessageHandler.handleMessage(message);
            log.debug("Sent MQTT message to topic: {}, payload: {}", topic, payload);
        } catch (Exception e) {
            log.error("Failed to send MQTT message to topic: {}", topic, e);
            throw new RuntimeException("Failed to send MQTT message", e);
        }
    }
}
