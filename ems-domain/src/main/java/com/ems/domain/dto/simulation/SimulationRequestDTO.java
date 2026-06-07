package com.ems.domain.dto.simulation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class SimulationRequestDTO {

    @NotBlank(message = "仿真名称不能为空")
    private String simulationName;

    @NotNull(message = "仿真日期不能为空")
    private LocalDate simulationDate;

    @NotBlank(message = "策略编码不能为空")
    private String strategyCode;

    private String strategyName;

    private String strategyType;

    private String batterySn;

    private String transformerCode;

    @DecimalMin(value = "0", message = "初始SOC不能小于0")
    @DecimalMax(value = "100", message = "初始SOC不能大于100")
    private BigDecimal initialSoc = new BigDecimal("50.00");

    @NotNull(message = "电池容量不能为空")
    @DecimalMin(value = "0", message = "电池容量不能小于0")
    private BigDecimal batteryCapacity;

    @NotNull(message = "电池功率不能为空")
    @DecimalMin(value = "0", message = "电池功率不能小于0")
    private BigDecimal batteryPower;

    @DecimalMin(value = "0", message = "充电效率不能小于0")
    @DecimalMax(value = "1", message = "充电效率不能大于1")
    private BigDecimal chargeEfficiency = new BigDecimal("0.95");

    @DecimalMin(value = "0", message = "放电效率不能小于0")
    @DecimalMax(value = "1", message = "放电效率不能大于1")
    private BigDecimal dischargeEfficiency = new BigDecimal("0.95");

    @DecimalMin(value = "0", message = "最小SOC不能小于0")
    @DecimalMax(value = "100", message = "最小SOC不能大于100")
    private BigDecimal minSoc = new BigDecimal("20.00");

    @DecimalMin(value = "0", message = "最大SOC不能小于0")
    @DecimalMax(value = "100", message = "最大SOC不能大于100")
    private BigDecimal maxSoc = new BigDecimal("90.00");

    private BigDecimal demandThreshold;

    private BigDecimal demandPrice;

    private Long degradationModelId;

    private String dataSource = "IMPORT";

    private LocalDate dataStartDate;

    private LocalDate dataEndDate;

    private List<SimulationDataPointDTO> loadData;

    private List<SimulationDataPointDTO> pvData;

    private List<SimulationDataPointDTO> priceData;

    private String remark;
}
