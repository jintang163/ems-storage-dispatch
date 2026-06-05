package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.domain.dto.device.DeviceDTO;
import com.ems.domain.dto.device.DeviceQueryDTO;
import com.ems.domain.entity.Device;
import com.ems.domain.entity.DeviceType;
import com.ems.domain.vo.PageResult;
import com.ems.repository.DeviceRepository;
import com.ems.repository.DeviceTypeRepository;
import com.ems.service.DeviceService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceDTO create(DeviceDTO dto) {
        if (deviceRepository.existsByDeviceSn(dto.getDeviceSn())) {
            throw new EmsException("设备编号已存在: " + dto.getDeviceSn());
        }

        DeviceType deviceType = deviceTypeRepository.findById(dto.getDeviceTypeId())
                .orElseThrow(() -> new EmsException("设备类型不存在: " + dto.getDeviceTypeId()));

        Device device = new Device();
        convertToEntity(dto, device);

        device = deviceRepository.save(device);
        log.info("Created device: {}", device.getDeviceSn());
        return convertToDTO(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceDTO update(Long id, DeviceDTO dto) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new EmsException("设备不存在: " + id));

        if (!device.getDeviceSn().equals(dto.getDeviceSn())
                && deviceRepository.existsByDeviceSn(dto.getDeviceSn())) {
            throw new EmsException("设备编号已存在: " + dto.getDeviceSn());
        }

        convertToEntity(dto, device);
        device = deviceRepository.save(device);
        log.info("Updated device: {}", device.getDeviceSn());
        return convertToDTO(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new EmsException("设备不存在: " + id);
        }
        deviceRepository.deleteById(id);
        log.info("Deleted device: {}", id);
    }

    @Override
    public DeviceDTO getById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new EmsException("设备不存在: " + id));
        return convertToDTO(device);
    }

    @Override
    public DeviceDTO getByDeviceSn(String deviceSn) {
        Device device = deviceRepository.findByDeviceSn(deviceSn)
                .orElseThrow(() -> new EmsException("设备不存在: " + deviceSn));
        return convertToDTO(device);
    }

    @Override
    public PageResult<DeviceDTO> query(DeviceQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPageNum() - 1,
                queryDTO.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<Device> spec = buildSpecification(queryDTO);
        Page<Device> page = deviceRepository.findAll(spec, pageable);

        List<DeviceDTO> dtoList = page.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtoList, page.getTotalElements(),
                queryDTO.getPageNum(), queryDTO.getPageSize());
    }

    @Override
    public List<DeviceDTO> listByType(Long deviceTypeId) {
        List<Device> devices = deviceRepository.findByDeviceTypeId(deviceTypeId);
        return devices.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<DeviceDTO> listAll() {
        List<Device> devices = deviceRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return devices.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Boolean enabled) {
        int updated = deviceRepository.updateEnabled(id, enabled);
        if (updated == 0) {
            throw new EmsException("设备不存在: " + id);
        }
        log.info("Updated device enabled status: {} -> {}", id, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String deviceSn, String status) {
        deviceRepository.updateStatus(deviceSn, status, LocalDateTime.now());
    }

    private Specification<Device> buildSpecification(DeviceQueryDTO queryDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (queryDTO.getDeviceSn() != null && !queryDTO.getDeviceSn().isEmpty()) {
                predicates.add(cb.like(root.get("deviceSn"), "%" + queryDTO.getDeviceSn() + "%"));
            }
            if (queryDTO.getName() != null && !queryDTO.getName().isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }
            if (queryDTO.getDeviceTypeId() != null) {
                predicates.add(cb.equal(root.get("deviceTypeId"), queryDTO.getDeviceTypeId()));
            }
            if (queryDTO.getProtocol() != null && !queryDTO.getProtocol().isEmpty()) {
                predicates.add(cb.equal(root.get("protocol"), queryDTO.getProtocol()));
            }
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus()));
            }
            if (queryDTO.getLocation() != null && !queryDTO.getLocation().isEmpty()) {
                predicates.add(cb.like(root.get("location"), "%" + queryDTO.getLocation() + "%"));
            }
            if (queryDTO.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), queryDTO.getEnabled()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private DeviceDTO convertToDTO(Device device) {
        DeviceDTO dto = new DeviceDTO();
        dto.setId(device.getId());
        dto.setDeviceSn(device.getDeviceSn());
        dto.setDeviceTypeId(device.getDeviceTypeId());
        dto.setName(device.getName());
        dto.setProtocol(device.getProtocol());
        dto.setHost(device.getHost());
        dto.setPort(device.getPort());
        dto.setSlaveId(device.getSlaveId());
        dto.setLocation(device.getLocation());
        dto.setStatus(device.getStatus());
        dto.setSamplingInterval(device.getSamplingInterval());
        dto.setEnabled(device.getEnabled());
        dto.setConfig(device.getConfig());
        dto.setDescription(device.getDescription());
        dto.setLastOnlineAt(device.getLastOnlineAt());
        dto.setCreatedAt(device.getCreatedAt());
        dto.setUpdatedAt(device.getUpdatedAt());

        if (device.getDeviceType() != null) {
            dto.setDeviceTypeName(device.getDeviceType().getName());
        }

        return dto;
    }

    private void convertToEntity(DeviceDTO dto, Device device) {
        device.setDeviceSn(dto.getDeviceSn());
        device.setDeviceTypeId(dto.getDeviceTypeId());
        device.setName(dto.getName());
        device.setProtocol(dto.getProtocol() != null ? dto.getProtocol() : "modbus");
        device.setHost(dto.getHost());
        device.setPort(dto.getPort());
        device.setSlaveId(dto.getSlaveId());
        device.setLocation(dto.getLocation());
        device.setSamplingInterval(dto.getSamplingInterval() != null ? dto.getSamplingInterval() : 5000);
        device.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        device.setConfig(dto.getConfig());
        device.setDescription(dto.getDescription());
    }
}
