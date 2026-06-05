package com.ems.repository;

import com.ems.domain.entity.AlarmRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlarmRuleRepository extends JpaRepository<AlarmRule, Long>, JpaSpecificationExecutor<AlarmRule> {

    List<AlarmRule> findByDeviceIdAndEnabledTrue(Long deviceId);

    List<AlarmRule> findByPointIdAndEnabledTrue(Long pointId);

    List<AlarmRule> findByEnabledTrue();
}
