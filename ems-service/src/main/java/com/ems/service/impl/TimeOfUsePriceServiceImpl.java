package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.price.TimeOfUsePriceDTO;
import com.ems.domain.entity.TimeOfUsePrice;
import com.ems.repository.TimeOfUsePriceRepository;
import com.ems.service.TimeOfUsePriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分时电价服务实现类
 * 提供电价的增删改查、当前电价计算以及电力公司电价接口对接功能
 *
 * 分时电价说明：
 * - 尖峰时段：用电最高峰，电价最高（如夏季10:00-12:00, 19:00-21:00）
 * - 高峰时段：用电高峰期，电价较高（如8:00-10:00, 12:00-14:00, 17:00-19:00, 21:00-23:00）
 * - 平段时段：正常用电，电价中等（如6:00-8:00, 14:00-17:00, 23:00-24:00）
 * - 低谷时段：用电低谷，电价最低（如0:00-6:00）
 *
 * 削峰填谷策略：
 * - 高峰时段放电（从电池向电网送电），获取高电价收益
 * - 低谷时段充电（从电网向电池充电），降低用电成本
 *
 * @author EMS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeOfUsePriceServiceImpl implements TimeOfUsePriceService {

    private final TimeOfUsePriceRepository priceRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimeOfUsePriceDTO create(TimeOfUsePriceDTO dto) {
        TimeOfUsePrice price = new TimeOfUsePrice();
        convertToEntity(dto, price);
        price = priceRepository.save(price);
        log.info("Created time-of-use price: {} - {}", price.getPeriodType(), price.getPrice());
        return convertToDTO(price);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimeOfUsePriceDTO update(Long id, TimeOfUsePriceDTO dto) {
        TimeOfUsePrice price = priceRepository.findById(id)
                .orElseThrow(() -> new EmsException("电价记录不存在: " + id));
        convertToEntity(dto, price);
        price = priceRepository.save(price);
        log.info("Updated time-of-use price: {}", id);
        return convertToDTO(price);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!priceRepository.existsById(id)) {
            throw new EmsException("电价记录不存在: " + id);
        }
        priceRepository.deleteById(id);
        log.info("Deleted time-of-use price: {}", id);
    }

    @Override
    public TimeOfUsePriceDTO getById(Long id) {
        TimeOfUsePrice price = priceRepository.findById(id)
                .orElseThrow(() -> new EmsException("电价记录不存在: " + id));
        return convertToDTO(price);
    }

    @Override
    public List<TimeOfUsePriceDTO> listAll() {
        List<TimeOfUsePrice> prices = priceRepository.findAll(Sort.by(Sort.Direction.ASC, "startTime"));
        return prices.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TimeOfUsePriceDTO> listValidPrices() {
        LocalDateTime now = LocalDateTime.now();
        List<TimeOfUsePrice> prices = priceRepository.findValidPrices(now.toLocalDate());
        return prices.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getCurrentPrice() {
        return getPriceAtTime(LocalDateTime.now());
    }

    @Override
    public BigDecimal getPriceAtTime(LocalDateTime dateTime) {
        return priceRepository.findCurrentPrice(dateTime.toLocalDate(), dateTime.toLocalTime())
                .map(TimeOfUsePrice::getPrice)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public String getCurrentPeriodType() {
        LocalDateTime now = LocalDateTime.now();
        return priceRepository.findCurrentPrice(now.toLocalDate(), now.toLocalTime())
                .map(TimeOfUsePrice::getPeriodType)
                .orElse("flat");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Boolean enabled) {
        TimeOfUsePrice price = priceRepository.findById(id)
                .orElseThrow(() -> new EmsException("电价记录不存在: " + id));
        price.setEnabled(enabled);
        priceRepository.save(price);
        log.info("Updated price enabled status: {} -> {}", id, enabled);
    }

    private TimeOfUsePriceDTO convertToDTO(TimeOfUsePrice price) {
        TimeOfUsePriceDTO dto = new TimeOfUsePriceDTO();
        dto.setId(price.getId());
        dto.setPeriodType(price.getPeriodType());
        dto.setPrice(price.getPrice());
        dto.setStartTime(price.getStartTime());
        dto.setEndTime(price.getEndTime());
        dto.setEffectiveDate(price.getEffectiveDate());
        dto.setExpiryDate(price.getExpiryDate());
        dto.setEnabled(price.getEnabled());
        dto.setDescription(price.getDescription());
        return dto;
    }

    private void convertToEntity(TimeOfUsePriceDTO dto, TimeOfUsePrice price) {
        price.setPeriodType(dto.getPeriodType());
        price.setPrice(dto.getPrice());
        price.setStartTime(dto.getStartTime());
        price.setEndTime(dto.getEndTime());
        price.setEffectiveDate(dto.getEffectiveDate());
        price.setExpiryDate(dto.getExpiryDate());
        price.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        price.setDescription(dto.getDescription());
    }

    /**
     * 从电力公司接口拉取最新的分时电价数据
     * 模拟调用国家电网/南方电网的官方电价查询API
     *
     * 实际项目中需要替换为真实的电力公司API接口：
     * 1. 国家电网：https://api.sgcc.com.cn/price/query
     * 2. 南方电网：https://api.csg.cn/price/tou
     * 3. 各省电力交易中心：根据不同地区对接相应接口
     *
     * 接口参数示例：
     * - areaCode: 地区编码（如310000表示上海市）
     * - voltageLevel: 电压等级（10kV/35kV/110kV等）
     * - date: 查询日期
     *
     * @return 拉取到的最新电价数据列表
     */
    @Override
    public List<TimeOfUsePriceDTO> fetchFromPowerCompany() {
        log.info("开始从电力公司拉取最新电价数据...");

        List<TimeOfUsePriceDTO> priceList = new ArrayList<>();
        LocalDate effectiveDate = LocalDate.now();
        LocalDate expiryDate = effectiveDate.plusMonths(1);

        try {
            // 模拟调用电力公司API
            // String apiUrl = "https://api.sgcc.com.cn/price/query?areaCode=310000";
            // ResponseEntity<PriceResponse> response = restTemplate.getForEntity(apiUrl, PriceResponse.class);
            // List<TimeOfUsePriceDTO> priceList = response.getBody().getData();

            // 由于是模拟环境，返回标准的工商业分时电价数据
            // 实际项目中应使用上述注释的HTTP调用方式

            // 尖峰时段（每年7-8月，10:00-12:00, 19:00-21:00）
            TimeOfUsePriceDTO peak尖峰 = new TimeOfUsePriceDTO();
            peak尖峰.setPeriodType("尖峰");
            peak尖峰.setPrice(new BigDecimal("1.35"));
            peak尖峰.setStartTime(LocalTime.of(10, 0));
            peak尖峰.setEndTime(LocalTime.of(12, 0));
            peak尖峰.setEffectiveDate(effectiveDate);
            peak尖峰.setExpiryDate(expiryDate);
            peak尖峰.setEnabled(true);
            peak尖峰.setDescription("夏季尖峰电价（7-8月适用）");
            priceList.add(peak尖峰);

            TimeOfUsePriceDTO peak尖峰2 = new TimeOfUsePriceDTO();
            peak尖峰2.setPeriodType("尖峰");
            peak尖峰2.setPrice(new BigDecimal("1.35"));
            peak尖峰2.setStartTime(LocalTime.of(19, 0));
            peak尖峰2.setEndTime(LocalTime.of(21, 0));
            peak尖峰2.setEffectiveDate(effectiveDate);
            peak尖峰2.setExpiryDate(expiryDate);
            peak尖峰2.setEnabled(true);
            peak尖峰2.setDescription("夏季尖峰电价（7-8月适用）");
            priceList.add(peak尖峰2);

            // 高峰时段
            TimeOfUsePriceDTO peak1 = new TimeOfUsePriceDTO();
            peak1.setPeriodType("高峰");
            peak1.setPrice(new BigDecimal("1.15"));
            peak1.setStartTime(LocalTime.of(8, 0));
            peak1.setEndTime(LocalTime.of(10, 0));
            peak1.setEffectiveDate(effectiveDate);
            peak1.setExpiryDate(expiryDate);
            peak1.setEnabled(true);
            peak1.setDescription("早高峰用电");
            priceList.add(peak1);

            TimeOfUsePriceDTO peak2 = new TimeOfUsePriceDTO();
            peak2.setPeriodType("高峰");
            peak2.setPrice(new BigDecimal("1.15"));
            peak2.setStartTime(LocalTime.of(12, 0));
            peak2.setEndTime(LocalTime.of(14, 0));
            peak2.setEffectiveDate(effectiveDate);
            peak2.setExpiryDate(expiryDate);
            peak2.setEnabled(true);
            peak2.setDescription("午高峰用电");
            priceList.add(peak2);

            TimeOfUsePriceDTO peak3 = new TimeOfUsePriceDTO();
            peak3.setPeriodType("高峰");
            peak3.setPrice(new BigDecimal("1.15"));
            peak3.setStartTime(LocalTime.of(17, 0));
            peak3.setEndTime(LocalTime.of(19, 0));
            peak3.setEffectiveDate(effectiveDate);
            peak3.setExpiryDate(expiryDate);
            peak3.setEnabled(true);
            peak3.setDescription("晚高峰用电");
            priceList.add(peak3);

            TimeOfUsePriceDTO peak4 = new TimeOfUsePriceDTO();
            peak4.setPeriodType("高峰");
            peak4.setPrice(new BigDecimal("1.15"));
            peak4.setStartTime(LocalTime.of(21, 0));
            peak4.setEndTime(LocalTime.of(23, 0));
            peak4.setEffectiveDate(effectiveDate);
            peak4.setExpiryDate(expiryDate);
            peak4.setEnabled(true);
            peak4.setDescription("夜间高峰用电");
            priceList.add(peak4);

            // 平段时段
            TimeOfUsePriceDTO flat1 = new TimeOfUsePriceDTO();
            flat1.setPeriodType("平段");
            flat1.setPrice(new BigDecimal("0.75"));
            flat1.setStartTime(LocalTime.of(6, 0));
            flat1.setEndTime(LocalTime.of(8, 0));
            flat1.setEffectiveDate(effectiveDate);
            flat1.setExpiryDate(expiryDate);
            flat1.setEnabled(true);
            flat1.setDescription("早间平段");
            priceList.add(flat1);

            TimeOfUsePriceDTO flat2 = new TimeOfUsePriceDTO();
            flat2.setPeriodType("平段");
            flat2.setPrice(new BigDecimal("0.75"));
            flat2.setStartTime(LocalTime.of(14, 0));
            flat2.setEndTime(LocalTime.of(17, 0));
            flat2.setEffectiveDate(effectiveDate);
            flat2.setExpiryDate(expiryDate);
            flat2.setEnabled(true);
            flat2.setDescription("下午平段");
            priceList.add(flat2);

            TimeOfUsePriceDTO flat3 = new TimeOfUsePriceDTO();
            flat3.setPeriodType("平段");
            flat3.setPrice(new BigDecimal("0.75"));
            flat3.setStartTime(LocalTime.of(23, 0));
            flat3.setEndTime(LocalTime.of(0, 0));
            flat3.setEffectiveDate(effectiveDate);
            flat3.setExpiryDate(expiryDate);
            flat3.setEnabled(true);
            flat3.setDescription("夜间平段");
            priceList.add(flat3);

            // 低谷时段
            TimeOfUsePriceDTO valley = new TimeOfUsePriceDTO();
            valley.setPeriodType("低谷");
            valley.setPrice(new BigDecimal("0.35"));
            valley.setStartTime(LocalTime.of(0, 0));
            valley.setEndTime(LocalTime.of(6, 0));
            valley.setEffectiveDate(effectiveDate);
            valley.setExpiryDate(expiryDate);
            valley.setEnabled(true);
            valley.setDescription("深夜低谷用电");
            priceList.add(valley);

            log.info("成功从电力公司拉取电价数据，共{}条记录", priceList.size());
            return priceList;

        } catch (Exception e) {
            log.error("从电力公司拉取电价数据失败", e);
            throw new EmsException("从电力公司拉取电价数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从电力公司拉取电价并自动更新到本地数据库
     * 处理逻辑：
     * 1. 调用fetchFromPowerCompany()获取最新电价数据
     * 2. 禁用所有旧的电价记录（设置enabled=false）
     * 3. 将新拉取的电价数据保存到数据库
     * 4. 记录同步日志
     *
     * 此方法由定时任务每日凌晨00:30自动调用
     * 也可通过管理后台手动触发
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncFromPowerCompany() {
        log.info("开始同步电力公司电价数据到本地数据库...");

        try {
            // 1. 从电力公司拉取最新电价
            List<TimeOfUsePriceDTO> newPrices = fetchFromPowerCompany();

            if (newPrices == null || newPrices.isEmpty()) {
                log.warn("从电力公司拉取的电价数据为空，取消同步");
                return;
            }

            // 2. 禁用所有现有的电价记录（逻辑删除，保留历史数据）
            List<TimeOfUsePrice> existingPrices = priceRepository.findAll();
            for (TimeOfUsePrice price : existingPrices) {
                price.setEnabled(false);
            }
            priceRepository.saveAll(existingPrices);
            log.info("已禁用原有{}条电价记录", existingPrices.size());

            // 3. 保存新的电价数据
            for (TimeOfUsePriceDTO dto : newPrices) {
                TimeOfUsePrice newPrice = new TimeOfUsePrice();
                convertToEntity(dto, newPrice);
                newPrice.setEnabled(true);
                // createdAt由@CreationTimestamp自动设置
                priceRepository.save(newPrice);
            }

            log.info("电价数据同步完成，共保存{}条新电价记录", newPrices.size());

        } catch (Exception e) {
            log.error("同步电价数据失败", e);
            throw new EmsException("同步电价数据失败: " + e.getMessage(), e);
        }
    }
}
