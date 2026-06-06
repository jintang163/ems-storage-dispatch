package com.ems.repository;

import com.ems.domain.entity.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long>, JpaSpecificationExecutor<StrategyConfig> {

    Optional<StrategyConfig> findByStrategyCode(String strategyCode);

    List<StrategyConfig> findByEnabledTrue();

    List<StrategyConfig> findByStrategyTypeAndEnabledTrue(String strategyType);

    Optional<StrategyConfig> findByDefaultStrategyTrueAndEnabledTrue();

    List<StrategyConfig> findByBatterySnAndEnabledTrue(String batterySn);

    List<StrategyConfig> findByTransformerCodeAndEnabledTrue(String transformerCode);

    @Modifying
    @Query("UPDATE StrategyConfig s SET s.defaultStrategy = false WHERE s.defaultStrategy = true")
    int clearDefaultStrategy();

    @Modifying
    @Query("UPDATE StrategyConfig s SET s.enabled = :enabled WHERE s.id = :id")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

    @Query("SELECT s.strategyType, COUNT(s) FROM StrategyConfig s GROUP BY s.strategyType")
    List<Object[]> countByStrategyType();

    @Query("SELECT COUNT(s) FROM StrategyConfig s WHERE s.enabled = true")
    long countEnabled();
}
