package com.ems.service.impl;

import com.ems.common.exception.EmsException;
import com.ems.config.PythonOptimizerConfig;
import com.ems.domain.dto.strategy.PythonOptimizationRequest;
import com.ems.domain.dto.strategy.PythonOptimizationResult;
import com.ems.domain.dto.strategy.PythonRealTimeAdjustRequest;
import com.ems.domain.dto.strategy.PythonRealTimeAdjustResult;
import com.ems.service.PythonOptimizerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonOptimizerServiceImpl implements PythonOptimizerService {

    private final PythonOptimizerConfig config;
    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(config.getConnectTimeout());
            factory.setReadTimeout(config.getReadTimeout());
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    @Override
    public PythonOptimizationResult rollingOptimize(PythonOptimizationRequest request) {
        if (!config.isEnabled()) {
            log.warn("Python优化服务未启用，返回null");
            return null;
        }

        log.info("调用Python滚动优化服务 - 策略: {}, 开始时段: {}",
                request.getStrategyCode(), request.getStartHour());

        String url = config.getBaseUrl() + config.getRollingOptimizeEndpoint();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String jsonBody = objectMapper.writeValueAsString(request);
            log.debug("滚动优化请求体: {}", jsonBody);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = getRestTemplate().exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                PythonOptimizationResult result = objectMapper.readValue(
                        response.getBody(), PythonOptimizationResult.class);

                if (result.isSuccess()) {
                    log.info("Python滚动优化成功 - 时段数: {}, 预期收益: {}",
                            result.getPlanHours() != null ? result.getPlanHours().size() : 0,
                            result.getExpectedRevenue());
                } else {
                    log.error("Python滚动优化失败: {}", result.getMessage());
                }

                return result;
            } else {
                throw new EmsException("Python优化服务响应异常: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("调用Python滚动优化服务失败: {}", e.getMessage(), e);
            throw new EmsException("调用Python优化服务失败: " + e.getMessage());
        }
    }

    @Override
    public PythonRealTimeAdjustResult realTimeAdjust(PythonRealTimeAdjustRequest request) {
        if (!config.isEnabled()) {
            log.warn("Python优化服务未启用，返回null");
            return null;
        }

        log.info("调用Python实时调整服务 - 策略: {}, SOC偏差: {}",
                request.getStrategyCode(),
                request.getCurrentSoc().subtract(request.getExpectedSoc()));

        String url = config.getBaseUrl() + config.getRealTimeAdjustEndpoint();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String jsonBody = objectMapper.writeValueAsString(request);
            log.debug("实时调整请求体: {}", jsonBody);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = getRestTemplate().exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                PythonRealTimeAdjustResult result = objectMapper.readValue(
                        response.getBody(), PythonRealTimeAdjustResult.class);

                if (result.isSuccess()) {
                    log.info("Python实时调整完成 - 类型: {}, 原功率: {}, 调整后: {}",
                            result.getAdjustmentType(),
                            result.getOriginalPower(),
                            result.getAdjustedPower());
                } else {
                    log.error("Python实时调整失败: {}", result.getMessage());
                }

                return result;
            } else {
                throw new EmsException("Python调整服务响应异常: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("调用Python实时调整服务失败: {}", e.getMessage(), e);
            throw new EmsException("调用Python调整服务失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isHealthy() {
        if (!config.isEnabled()) {
            return false;
        }

        String url = config.getBaseUrl() + config.getHealthEndpoint();

        try {
            ResponseEntity<Map> response = getRestTemplate().getForEntity(url, Map.class);
            return response.getStatusCode() == HttpStatus.OK
                    && "healthy".equals(response.getBody().get("status"));
        } catch (Exception e) {
            log.warn("Python优化服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        if (!config.isEnabled()) {
            Map<String, Object> status = new HashMap<>();
            status.put("status", "disabled");
            return status;
        }

        String url = config.getBaseUrl() + "/api/optimizer/status";

        try {
            ResponseEntity<Map> response = getRestTemplate().getForEntity(url, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("获取Python优化服务状态失败: {}", e.getMessage());
        }

        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        return error;
    }

    @Override
    public List<Map<String, Object>> getOptimizationHistory(String strategyCode, int limit) {
        if (!config.isEnabled()) {
            return List.of();
        }

        String url = UriComponentsBuilder
                .fromUriString(config.getBaseUrl() + "/api/optimizer/history/optimization")
                .queryParam("strategy_code", strategyCode)
                .queryParam("limit", limit)
                .toUriString();

        try {
            ResponseEntity<Map> response = getRestTemplate().getForEntity(url, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody().get("history");
            }
        } catch (Exception e) {
            log.error("获取优化历史失败: {}", e.getMessage());
        }

        return List.of();
    }

    @Override
    public List<Map<String, Object>> getAdjustmentHistory(String strategyCode, String adjustmentType, int limit) {
        if (!config.isEnabled()) {
            return List.of();
        }

        String url = UriComponentsBuilder
                .fromUriString(config.getBaseUrl() + "/api/optimizer/history/adjustment")
                .queryParam("strategy_code", strategyCode)
                .queryParam("adjustment_type", adjustmentType)
                .queryParam("limit", limit)
                .toUriString();

        try {
            ResponseEntity<Map> response = getRestTemplate().getForEntity(url, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody().get("history");
            }
        } catch (Exception e) {
            log.error("获取调整历史失败: {}", e.getMessage());
        }

        return List.of();
    }

    @Override
    public Map<String, Object> getAdjustmentStatistics(String strategyCode) {
        if (!config.isEnabled()) {
            return Map.of();
        }

        String url = UriComponentsBuilder
                .fromUriString(config.getBaseUrl() + "/api/optimizer/statistics/adjustment")
                .queryParam("strategy_code", strategyCode)
                .toUriString();

        try {
            ResponseEntity<Map> response = getRestTemplate().getForEntity(url, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("statistics");
            }
        } catch (Exception e) {
            log.error("获取调整统计失败: {}", e.getMessage());
        }

        return Map.of();
    }

    @Override
    public Map<String, Object> rollingOptimize15Min(Map<String, Object> request) {
        if (!config.isEnabled()) {
            return Map.of("success", false, "message", "Python优化服务未启用");
        }

        log.info("调用Python 15分钟滚动优化服务");

        String url = config.getBaseUrl() + config.getRollingOptimize15MinEndpoint();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String jsonBody = objectMapper.writeValueAsString(request);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<Map> response = getRestTemplate().exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                return Map.of("success", false, "message", "响应异常: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("调用Python 15分钟滚动优化失败: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
