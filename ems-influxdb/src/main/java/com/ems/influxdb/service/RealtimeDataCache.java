package com.ems.influxdb.service;

import com.ems.domain.tsdb.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RealtimeDataCache {

    private final Cache<String, MeterData> meterCache;
    private final Cache<String, PvData> pvCache;
    private final Cache<String, BmsData> bmsCache;
    private final Cache<String, PcsData> pcsCache;

    public RealtimeDataCache() {
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats();

        this.meterCache = cacheBuilder.build();
        this.pvCache = cacheBuilder.build();
        this.bmsCache = cacheBuilder.build();
        this.pcsCache = cacheBuilder.build();
    }

    public void putMeterData(String deviceSn, MeterData data) {
        meterCache.put(deviceSn, data);
    }

    public MeterData getMeterData(String deviceSn) {
        return meterCache.getIfPresent(deviceSn);
    }

    public void putPvData(String deviceSn, PvData data) {
        pvCache.put(deviceSn, data);
    }

    public PvData getPvData(String deviceSn) {
        return pvCache.getIfPresent(deviceSn);
    }

    public void putBmsData(String deviceSn, BmsData data) {
        bmsCache.put(deviceSn, data);
    }

    public BmsData getBmsData(String deviceSn) {
        return bmsCache.getIfPresent(deviceSn);
    }

    public void putPcsData(String deviceSn, PcsData data) {
        pcsCache.put(deviceSn, data);
    }

    public PcsData getPcsData(String deviceSn) {
        return pcsCache.getIfPresent(deviceSn);
    }

    public void invalidateAll() {
        meterCache.invalidateAll();
        pvCache.invalidateAll();
        bmsCache.invalidateAll();
        pcsCache.invalidateAll();
    }
}
