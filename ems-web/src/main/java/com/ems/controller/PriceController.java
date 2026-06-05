package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.price.TimeOfUsePriceDTO;
import com.ems.service.TimeOfUsePriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 电价管理控制器
 * 提供分时电价的CRUD接口、当前电价查询、以及电力公司电价对接接口
 *
 * 电价数据在储能优化调度中的作用：
 * - 高峰时段（高价）：控制PCS放电，将存储的电能送入电网获取收益
 * - 低谷时段（低价）：控制PCS充电，从电网购入廉价电能存储
 * - 平段时段：根据SOC状态和负荷需求灵活调整
 *
 * @author EMS Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
@CrossOrigin
public class PriceController {

    private final TimeOfUsePriceService priceService;

    /**
     * 创建电价配置
     * @param dto 电价数据
     * @return 创建后的电价
     */
    @PostMapping
    public Result<TimeOfUsePriceDTO> create(@Valid @RequestBody TimeOfUsePriceDTO dto) {
        return Result.success(priceService.create(dto));
    }

    /**
     * 更新电价配置
     * @param id 电价ID
     * @param dto 电价数据
     * @return 更新后的电价
     */
    @PutMapping("/{id}")
    public Result<TimeOfUsePriceDTO> update(@PathVariable Long id, @Valid @RequestBody TimeOfUsePriceDTO dto) {
        return Result.success(priceService.update(id, dto));
    }

    /**
     * 删除电价配置
     * @param id 电价ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        priceService.delete(id);
        return Result.success();
    }

    /**
     * 根据ID获取电价配置
     * @param id 电价ID
     * @return 电价详情
     */
    @GetMapping("/{id}")
    public Result<TimeOfUsePriceDTO> getById(@PathVariable Long id) {
        return Result.success(priceService.getById(id));
    }

    /**
     * 获取所有电价配置列表
     * @return 电价列表
     */
    @GetMapping
    public Result<List<TimeOfUsePriceDTO>> listAll() {
        return Result.success(priceService.listAll());
    }

    /**
     * 获取当前有效的电价配置列表
     * @return 有效电价列表
     */
    @GetMapping("/valid")
    public Result<List<TimeOfUsePriceDTO>> listValidPrices() {
        return Result.success(priceService.listValidPrices());
    }

    /**
     * 获取当前电价信息
     * 返回当前时段的电价和时段类型（尖峰/高峰/平段/低谷）
     * @return 包含price和periodType的Map
     */
    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentPriceInfo() {
        BigDecimal price = priceService.getCurrentPrice();
        String periodType = priceService.getCurrentPeriodType();
        return Result.success(Map.of(
                "price", price,
                "periodType", periodType
        ));
    }

    /**
     * 更新电价启用状态
     * @param id 电价ID
     * @param body 包含enabled字段
     * @return 操作结果
     */
    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        priceService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }

    /**
     * 从电力公司拉取最新电价数据（仅查询，不更新数据库）
     * 调用此接口可以预览电力公司返回的最新电价政策
     * @return 电力公司返回的电价列表
     */
    @GetMapping("/fetch")
    public Result<List<TimeOfUsePriceDTO>> fetchFromPowerCompany() {
        return Result.success(priceService.fetchFromPowerCompany());
    }

    /**
     * 从电力公司拉取电价并同步到本地数据库
     * 此接口会：
     * 1. 调用电力公司API获取最新电价
     * 2. 禁用本地所有旧电价记录
     * 3. 保存新的电价数据到数据库
     * 完成后可以通过GET /api/prices/valid查看最新生效的电价
     *
     * @return 操作结果
     */
    @PostMapping("/sync")
    public Result<Void> syncFromPowerCompany() {
        priceService.syncFromPowerCompany();
        return Result.success();
    }
}
