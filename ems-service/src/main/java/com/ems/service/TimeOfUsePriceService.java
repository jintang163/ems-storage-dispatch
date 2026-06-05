package com.ems.service;

import com.ems.domain.dto.price.TimeOfUsePriceDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TimeOfUsePriceService {

    TimeOfUsePriceDTO create(TimeOfUsePriceDTO dto);

    TimeOfUsePriceDTO update(Long id, TimeOfUsePriceDTO dto);

    void delete(Long id);

    TimeOfUsePriceDTO getById(Long id);

    List<TimeOfUsePriceDTO> listAll();

    List<TimeOfUsePriceDTO> listValidPrices();

    BigDecimal getCurrentPrice();

    BigDecimal getPriceAtTime(LocalDateTime dateTime);

    String getCurrentPeriodType();

    void updateEnabled(Long id, Boolean enabled);
}
