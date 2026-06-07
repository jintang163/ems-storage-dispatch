package com.ems.domain.dto.simulation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SimulationBatchRequestDTO {

    @NotBlank(message = "仿真名称不能为空")
    private String simulationName;

    @NotEmpty(message = "策略编码列表不能为空")
    private List<String> strategyCodes;

    private SimulationRequestDTO baseConfig;

    private Boolean compareMode = true;
}
