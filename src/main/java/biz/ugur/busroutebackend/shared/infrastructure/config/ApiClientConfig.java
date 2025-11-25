package biz.ugur.busroutebackend.shared.infrastructure.config;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Slf4j
@Configuration
public class ApiClientConfig {

    @Bean("gpsApiClient")
    public WebClient gpsApiClient(
            @Value("${external.api.gps.base-url}") String baseUrl,
            @Value("${external.api.gps.timeout:30s}") Duration timeout,
            @Value("${external.api.gps.connect-timeout:10s}") Duration connectTimeout) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());

        log.info("GPS API WebClient configured: baseUrl={}, responseTimeout={}s, connectTimeout={}s",
                baseUrl, timeout.toSeconds(), connectTimeout.toSeconds());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    @Bean("busInfoApiClient")
    public WebClient busInfoApiClient(
            @Value("${external.api.bus-info.base-url}") String baseUrl,
            @Value("${external.api.bus-info.timeout:30s}") Duration timeout,
            @Value("${external.api.bus-info.connect-timeout:10s}") Duration connectTimeout) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());

        log.info("Bus Info API WebClient configured: baseUrl={}, responseTimeout={}s, connectTimeout={}s",
                baseUrl, timeout.toSeconds(), connectTimeout.toSeconds());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
