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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeOfUsePriceServiceImpl implements TimeOfUsePriceService {

    private final TimeOfUsePriceRepository priceRepository;

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
}
