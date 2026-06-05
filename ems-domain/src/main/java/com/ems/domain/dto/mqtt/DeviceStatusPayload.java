package com.ems.domain.dto.mqtt;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.time.Instant;

/**
 * 设备状态MQTT消息载荷
 * 接收Python采集端发送的设备状态变更通知
 * 字段映射关系：
 * Python(snake_case) -> Java(camelCase)
 */
@Data
public class DeviceStatusPayload {

    /**
     * 设备编号
     */
    @JSONField(name = "deviceSn")
    private String deviceSn;

    /**
     * 设备状态：online-在线，offline-离线
     */
    @JSONField(name = "status")
    private String status;

    /**
     * 时间戳 (毫秒)
     */
    @JSONField(name = "timestamp")
    private Long timestamp;

    /**
     * 获取时间戳的Instant对象
     * @return Instant对象，如果时间戳为空则返回当前时间
     */
    public Instant getTimestampInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
