package com.ems.service;

import com.ems.domain.dto.device.DeviceDTO;
import com.ems.domain.dto.device.DeviceQueryDTO;
import com.ems.domain.vo.PageResult;

import java.util.List;

public interface DeviceService {

    DeviceDTO create(DeviceDTO dto);

    DeviceDTO update(Long id, DeviceDTO dto);

    void delete(Long id);

    DeviceDTO getById(Long id);

    DeviceDTO getByDeviceSn(String deviceSn);

    PageResult<DeviceDTO> query(DeviceQueryDTO queryDTO);

    List<DeviceDTO> listByType(Long deviceTypeId);

    List<DeviceDTO> listAll();

    void updateEnabled(Long id, Boolean enabled);

    void updateStatus(String deviceSn, String status);
}
