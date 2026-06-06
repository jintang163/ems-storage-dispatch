package com.ems.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ems.optimizer.python")
public class PythonOptimizerConfig {

    private String baseUrl = "http://localhost:8001";
    private int connectTimeout = 5000;
    private int readTimeout = 30000;
    private boolean enabled = true;
    private String rollingOptimizeEndpoint = "/api/optimizer/rolling-optimize";
    private String realTimeAdjustEndpoint = "/api/optimizer/real-time-adjust";
    private String healthEndpoint = "/api/optimizer/health";
    private String rollingOptimize15MinEndpoint = "/api/optimizer/rolling-optimize/15min";
}
