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

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
@CrossOrigin
public class PriceController {

    private final TimeOfUsePriceService priceService;

    @PostMapping
    public Result<TimeOfUsePriceDTO> create(@Valid @RequestBody TimeOfUsePriceDTO dto) {
        return Result.success(priceService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<TimeOfUsePriceDTO> update(@PathVariable Long id, @Valid @RequestBody TimeOfUsePriceDTO dto) {
        return Result.success(priceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        priceService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<TimeOfUsePriceDTO> getById(@PathVariable Long id) {
        return Result.success(priceService.getById(id));
    }

    @GetMapping
    public Result<List<TimeOfUsePriceDTO>> listAll() {
        return Result.success(priceService.listAll());
    }

    @GetMapping("/valid")
    public Result<List<TimeOfUsePriceDTO>> listValidPrices() {
        return Result.success(priceService.listValidPrices());
    }

    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentPriceInfo() {
        BigDecimal price = priceService.getCurrentPrice();
        String periodType = priceService.getCurrentPeriodType();
        return Result.success(Map.of(
                "price", price,
                "periodType", periodType
        ));
    }

    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        priceService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }
}
