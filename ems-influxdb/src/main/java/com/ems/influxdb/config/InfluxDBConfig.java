package com.ems.influxdb.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Data
@Configuration
@ConfigurationProperties(prefix = "influxdb")
public class InfluxDBConfig {

    private String url;
    private String token;
    private String org;
    private String bucket;
    private String timeout = "30s";

    @Bean
    public InfluxDBClient influxDBClient() {
        InfluxDBClient client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
        client.enableGzip();
        return client;
    }

    @Bean
    public WriteApiBlocking writeApiBlocking(InfluxDBClient influxDBClient) {
        return influxDBClient.getWriteApiBlocking();
    }

    @Bean
    public WriteApi writeApi(InfluxDBClient influxDBClient) {
        return influxDBClient.makeWriteApi();
    }

    public WritePrecision getWritePrecision() {
        return WritePrecision.MS;
    }

    public long getTimeoutMillis() {
        if (timeout.endsWith("s")) {
            return TimeUnit.SECONDS.toMillis(Long.parseLong(timeout.replace("s", "")));
        } else if (timeout.endsWith("ms")) {
            return Long.parseLong(timeout.replace("ms", ""));
        }
        return 30000;
    }
}
