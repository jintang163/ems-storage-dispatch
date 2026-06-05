package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.mqtt.service.MqttPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 控制命令控制器
 * 提供储能系统的控制指令下发接口，是EMS系统优化调度的执行入口
 *
 * 控制命令链路：
 * 前端/调度算法 -> CommandController -> MqttPublisherService -> MQTT Broker
 *    -> Python采集端 -> Modbus TCP -> PCS储能变流器 -> 电池充放电
 *
 * 充放电控制策略（削峰填谷）：
 * - 高峰时段（电价高）：发送放电命令，将电池电能送入电网，获取收益
 * - 低谷时段（电价低）：发送充电命令，从电网购入廉价电能存储
 * - 平段时段：根据SOC状态和负荷需求灵活调整
 *
 * 安全约束条件：
 * - 充电时SOC不能超过上限（通常95%）
 * - 放电时SOC不能低于下限（通常20%）
 * - 充放电功率不能超过PCS额定功率
 * - 电池温度过高时禁止充放电
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/commands")
@RequiredArgsConstructor
@CrossOrigin
public class CommandController {

    private final MqttPublisherService mqttPublisherService;

    /**
     * 发送功率控制命令
     * 直接指定目标功率值进行充放电控制
     * 功率值含义：正值表示放电，负值表示充电
     *
     * @param params 参数：
     *               - deviceSn: PCS设备编号
     *               - targetPower: 目标功率（kW，正放电负充电）
     *               - duration: 持续时间（秒，默认3600秒）
     * @return 操作结果
     */
    @PostMapping("/power-control")
    public Result<Void> sendPowerControl(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        double targetPower = ((Number) params.get("targetPower")).doubleValue();
        int duration = params.get("duration") != null ? ((Number) params.get("duration")).intValue() : 3600;
        mqttPublisherService.sendPowerControl(deviceSn, targetPower, duration);
        return Result.success();
    }

    /**
     * 发送充电命令
     * 控制PCS从电网向电池充电
     * 通常在电价低谷时段调用，降低用电成本
     *
     * @param params 参数：
     *               - deviceSn: PCS设备编号
     *               - power: 充电功率（kW，默认100kW）
     * @return 操作结果
     */
    @PostMapping("/charge")
    public Result<Void> sendStartCharge(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        double power = params.get("power") != null ? ((Number) params.get("power")).doubleValue() : 100;
        mqttPublisherService.sendStartCharge(deviceSn, power);
        return Result.success();
    }

    /**
     * 发送放电命令
     * 控制PCS从电池向电网放电
     * 通常在电价高峰时段调用，获取电价差收益
     *
     * @param params 参数：
     *               - deviceSn: PCS设备编号
     *               - power: 放电功率（kW，默认100kW）
     * @return 操作结果
     */
    @PostMapping("/discharge")
    public Result<Void> sendStartDischarge(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        double power = params.get("power") != null ? ((Number) params.get("power")).doubleValue() : 100;
        mqttPublisherService.sendStartDischarge(deviceSn, power);
        return Result.success();
    }

    /**
     * 发送停止命令
     * 停止PCS的充放电运行，切换到待机状态
     * 通常在系统维护或异常情况下调用
     *
     * @param params 参数：
     *               - deviceSn: PCS设备编号
     * @return 操作结果
     */
    @PostMapping("/stop")
    public Result<Void> sendStop(@RequestBody Map<String, String> params) {
        String deviceSn = params.get("deviceSn");
        mqttPublisherService.sendStop(deviceSn);
        return Result.success();
    }

    /**
     * 发送状态查询命令
     * 查询设备当前运行状态
     * 用于确认设备是否在线以及当前运行模式
     *
     * @param params 参数：
     *               - deviceSn: 设备编号
     * @return 操作结果
     */
    @PostMapping("/query-status")
    public Result<Void> sendQueryStatus(@RequestBody Map<String, String> params) {
        String deviceSn = params.get("deviceSn");
        mqttPublisherService.sendQueryStatus(deviceSn);
        return Result.success();
    }

    /**
     * 发送自定义命令
     * 支持扩展的设备控制指令，用于特殊场景调试
     *
     * @param params 参数：
     *               - deviceSn: 设备编号
     *               - commandType: 命令类型
     *               - params: 命令参数
     * @return 操作结果
     */
    @PostMapping("/custom")
    public Result<Void> sendCustomCommand(@RequestBody Map<String, Object> params) {
        String deviceSn = (String) params.get("deviceSn");
        String commandType = (String) params.get("commandType");
        Object commandParams = params.get("params");
        mqttPublisherService.sendCommand(deviceSn, commandType, commandParams);
        return Result.success();
    }
}
