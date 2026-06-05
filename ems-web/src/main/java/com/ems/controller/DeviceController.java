package com.ems.controller;

import com.ems.common.result.Result;
import com.ems.domain.dto.device.DeviceDTO;
import com.ems.domain.dto.device.DeviceQueryDTO;
import com.ems.domain.vo.PageResult;
import com.ems.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@CrossOrigin
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public Result<DeviceDTO> create(@Valid @RequestBody DeviceDTO dto) {
        return Result.success(deviceService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<DeviceDTO> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        return Result.success(deviceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<DeviceDTO> getById(@PathVariable Long id) {
        return Result.success(deviceService.getById(id));
    }

    @GetMapping("/sn/{deviceSn}")
    public Result<DeviceDTO> getByDeviceSn(@PathVariable String deviceSn) {
        return Result.success(deviceService.getByDeviceSn(deviceSn));
    }

    @PostMapping("/query")
    public Result<PageResult<DeviceDTO>> query(@RequestBody DeviceQueryDTO queryDTO) {
        return Result.success(deviceService.query(queryDTO));
    }

    @GetMapping("/list")
    public Result<List<DeviceDTO>> listAll() {
        return Result.success(deviceService.listAll());
    }

    @GetMapping("/type/{deviceTypeId}")
    public Result<List<DeviceDTO>> listByType(@PathVariable Long deviceTypeId) {
        return Result.success(deviceService.listByType(deviceTypeId));
    }

    @PatchMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        deviceService.updateEnabled(id, body.get("enabled"));
        return Result.success();
    }
}
