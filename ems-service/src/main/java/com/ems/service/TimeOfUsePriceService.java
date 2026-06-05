package com.ems.service;

import com.ems.domain.dto.price.TimeOfUsePriceDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分时电价服务接口
 * 提供电价的CRUD操作、当前电价查询以及电力公司电价接口对接功能
 *
 * @author EMS Team
 * @since 1.0.0
 */
public interface TimeOfUsePriceService {

    /**
     * 创建电价配置
     * @param dto 电价数据传输对象
     * @return 创建后的电价DTO
     */
    TimeOfUsePriceDTO create(TimeOfUsePriceDTO dto);

    /**
     * 更新电价配置
     * @param id 电价ID
     * @param dto 电价数据传输对象
     * @return 更新后的电价DTO
     */
    TimeOfUsePriceDTO update(Long id, TimeOfUsePriceDTO dto);

    /**
     * 删除电价配置
     * @param id 电价ID
     */
    void delete(Long id);

    /**
     * 根据ID获取电价配置
     * @param id 电价ID
     * @return 电价DTO
     */
    TimeOfUsePriceDTO getById(Long id);

    /**
     * 获取所有电价配置列表
     * @return 电价DTO列表
     */
    List<TimeOfUsePriceDTO> listAll();

    /**
     * 获取当前有效的电价配置列表
     * @return 有效电价DTO列表
     */
    List<TimeOfUsePriceDTO> listValidPrices();

    /**
     * 获取当前时间段的电价
     * @return 当前电价（元/kWh）
     */
    BigDecimal getCurrentPrice();

    /**
     * 获取指定时间的电价
     * @param dateTime 指定时间
     * @return 指定时间的电价（元/kWh）
     */
    BigDecimal getPriceAtTime(LocalDateTime dateTime);

    /**
     * 获取当前时段类型（尖峰平谷）
     * @return 时段类型：尖峰、高峰、平段、低谷
     */
    String getCurrentPeriodType();

    /**
     * 更新电价启用状态
     * @param id 电价ID
     * @param enabled 是否启用
     */
    void updateEnabled(Long id, Boolean enabled);

    /**
     * 从电力公司接口拉取最新的分时电价数据
     * 模拟调用电力公司官方API获取最新电价政策
     * @return 拉取到的电价数据列表
     */
    List<TimeOfUsePriceDTO> fetchFromPowerCompany();

    /**
     * 从电力公司拉取电价并自动更新到本地数据库
     * 定时任务调用此方法，每日凌晨自动同步最新电价
     */
    void syncFromPowerCompany();
}
