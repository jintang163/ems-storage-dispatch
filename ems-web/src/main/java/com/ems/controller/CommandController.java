package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.mqtt.service.MqttPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/commands")
@RequiredArgsConstructor
@CrossOrigin
public class CommandController {

    private final MqttPublisherService mqttPublisherService;

    @PostMapping("/power-control")
    public Result<Void> sendPowerControl(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        double targetPower = ((Number) params.get("targetPower")).doubleValue();
        int duration = params.get("duration") != null ? ((Number) params.get("duration")).intValue() : 3600;
        mqttPublisherService.sendPowerControl(deviceSn, targetPower, duration);
        return Result.success();
    }

    @PostMapping("/charge")
    public Result<Void> sendStartCharge(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        double power = params.get("power") != null ? ((Number) params.get("power")).doubleValue() : 100;
        mqttPublisherService.sendStartCharge(deviceSn, power);
        return Result.success();
    }

    @PostMapping("/discharge")
    public Result<Void> sendStartDischarge(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        double power = params.get("power") != null ? ((Number) params.get("power")).doubleValue() : 100;
        mqttPublisherService.sendStartDischarge(deviceSn, power);
        return Result.success();
    }

    @PostMapping("/stop")
    public Result<Void> sendStop(@RequestBody Map<String, String> params) {
        String deviceSn = params.get("deviceSn");
        mqttPublisherService.sendStop(deviceSn);
        return Result.success();
    }

    @PostMapping("/query-status")
    public Result<Void> sendQueryStatus(@RequestBody Map<String, String> params) {
        String deviceSn = params.get("deviceSn");
        mqttPublisherService.sendQueryStatus(deviceSn);
        return Result.success();
    }

    @PostMapping("/custom")
    public Result<Void> sendCustomCommand(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        String commandType = (String) params.get("commandType");
        Object commandParams = params.get("params");
        mqttPublisherService.sendCommand(deviceSn, commandType, commandParams);
        return Result.success();
    }
}
