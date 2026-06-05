package com.ems.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "dispatch_command", indexes = {
        @Index(name = "idx_cmd_status", columnList = "status"),
        @Index(name = "idx_cmd_created", columnList = "created_at")
})
public class DispatchCommand extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_type", nullable = false, length = 50)
    private String commandType;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "target_power", precision = 18, scale = 4)
    private BigDecimal targetPower;

    private Integer duration;

    private Integer priority = 5;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "sent_time")
    private LocalDateTime sentTime;

    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    @Column(name = "result_message", length = 500)
    private String resultMessage;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
