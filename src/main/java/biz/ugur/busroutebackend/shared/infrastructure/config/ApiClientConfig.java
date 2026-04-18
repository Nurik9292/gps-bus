package biz.ugur.busroutebackend.shared.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class ApiClientConfig {

    @Bean("gpsApiClient")
    public WebClient gpsApiClient(
            @Value("${external.api.gps.base-url}") String baseUrl,
            @Value("${external.api.gps.timeout:30s}") Duration timeout,
            @Value("${external.api.gps.connect-timeout:10s}") Duration connectTimeout,
            @Value("${external.api.gps.read-timeout:60s}") Duration readTimeout,
            @Value("${external.api.gps.write-timeout:10s}") Duration writeTimeout,
            @Value("${external.api.gps.max-in-memory-bytes:10485760}") int maxInMemoryBytes) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS)));

        log.info("GPS API WebClient configured: baseUrl={}, responseTimeout={}s, connectTimeout={}s, readTimeout={}s, writeTimeout={}s",
                baseUrl, timeout.toSeconds(), connectTimeout.toSeconds(), readTimeout.toSeconds(), writeTimeout.toSeconds());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
                .build();
    }

    @Bean("busInfoApiClient")
    @ConditionalOnProperty(prefix = "external.api.bus-info", name = "base-url")
    public WebClient busInfoApiClient(
            @Value("${external.api.bus-info.base-url}") String baseUrl,
            @Value("${external.api.bus-info.timeout:30s}") Duration timeout,
            @Value("${external.api.bus-info.connect-timeout:10s}") Duration connectTimeout,
            @Value("${external.api.bus-info.read-timeout:30s}") Duration readTimeout,
            @Value("${external.api.bus-info.write-timeout:10s}") Duration writeTimeout,
            @Value("${external.api.bus-info.max-in-memory-bytes:2097152}") int maxInMemoryBytes) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS)));

        log.info("Bus Info API WebClient configured: baseUrl={}, responseTimeout={}s, connectTimeout={}s, readTimeout={}s, writeTimeout={}s",
                baseUrl, timeout.toSeconds(), connectTimeout.toSeconds(), readTimeout.toSeconds(), writeTimeout.toSeconds());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
                .build();
    }

    @Bean("osrmClient")
    @ConditionalOnProperty(prefix = "external.api.osrm", name = "enabled", havingValue = "true")
    public WebClient osrmClient(
            @Value("${external.api.osrm.base-url}") String baseUrl,
            @Value("${external.api.osrm.timeout:3s}") Duration timeout,
            @Value("${external.api.osrm.connect-timeout:2s}") Duration connectTimeout,
            @Value("${external.api.osrm.read-timeout:3s}") Duration readTimeout,
            @Value("${external.api.osrm.write-timeout:2s}") Duration writeTimeout,
            @Value("${external.api.osrm.max-in-memory-bytes:524288}") int maxInMemoryBytes) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS)));

        log.info("OSRM WebClient configured: baseUrl={}", baseUrl);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
                .build();
    }

    @Bean("nominatimClient")
    @ConditionalOnProperty(prefix = "external.api.nominatim", name = "enabled", havingValue = "true")
    public WebClient nominatimClient(
            @Value("${external.api.nominatim.base-url}") String baseUrl,
            @Value("${external.api.nominatim.timeout:10s}") Duration timeout,
            @Value("${external.api.nominatim.connect-timeout:3s}") Duration connectTimeout,
            @Value("${external.api.nominatim.read-timeout:10s}") Duration readTimeout,
            @Value("${external.api.nominatim.write-timeout:3s}") Duration writeTimeout,
            @Value("${external.api.nominatim.max-in-memory-bytes:2097152}") int maxInMemoryBytes) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS)));

        log.info("Nominatim WebClient configured: baseUrl={}, responseTimeout={}s", baseUrl, timeout.toSeconds());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
                .build();
    }
}
