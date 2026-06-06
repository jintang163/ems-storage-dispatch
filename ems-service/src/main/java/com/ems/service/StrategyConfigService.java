package com.ems.service;

import com.ems.domain.dto.strategy.StrategyConfigDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 策略配置服务接口
 * 提供策略配置的CRUD操作和多目标权重配置管理
 *
 * 核心功能：
 * 1. 策略基本配置：峰谷套利、削峰填谷、需量控制开关
 * 2. 多目标权重配置：套利收益 vs 寿命损耗 vs 需量费用
 * 3. 电池寿命约束：充放电倍率、SOC范围、循环次数限制
 * 4. 策略参数校验和默认值设置
 *
 * 多目标优化权重配置：
 * - 套利收益权重 (arbitrageWeight): 追求低价充电高价放电的收益最大化
 * - 寿命损耗权重 (lifespanWeight): 最小化电池衰减，延长电池寿命
 * - 需量费用权重 (demandWeight): 控制最大需量，降低需量电费
 *
 * 权重之和建议为1.0，系统会自动归一化处理。
 *
 * @author EMS Team
 * @since 1.0.0
 */
public interface StrategyConfigService {

    StrategyConfigDTO create(StrategyConfigDTO dto);

    StrategyConfigDTO update(Long id, StrategyConfigDTO dto);

    void delete(Long id);

    StrategyConfigDTO getById(Long id);

    StrategyConfigDTO getByStrategyCode(String strategyCode);

    List<StrategyConfigDTO> listAll();

    List<StrategyConfigDTO> listEnabled();

    List<StrategyConfigDTO> listByStrategyType(String strategyType);

    StrategyConfigDTO getDefaultStrategy();

    List<StrategyConfigDTO> listByBatterySn(String batterySn);

    List<StrategyConfigDTO> listByTransformerCode(String transformerCode);

    void updateEnabled(Long id, Boolean enabled);

    void setDefaultStrategy(Long id);

    Map<String, String> validateStrategyConfig(StrategyConfigDTO dto);

    BigDecimal normalizeWeights(StrategyConfigDTO dto);

    Map<String, Long> getStrategyTypeStatistics();

    long getEnabledStrategyCount();
}
