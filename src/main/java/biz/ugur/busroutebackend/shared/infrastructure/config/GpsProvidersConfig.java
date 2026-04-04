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
public class GpsProvidersConfig {


    @Bean("tugdkGpsWebClient")
    @ConditionalOnProperty(prefix = "external.api.gps-tugdk", name = "enabled", havingValue = "true")
    public WebClient tugdkGpsWebClient(
            @Value("${external.api.gps-tugdk.base-url}") String baseUrl,
            @Value("${external.api.gps-tugdk.timeout:30s}") Duration timeout,
            @Value("${external.api.gps-tugdk.connect-timeout:10s}") Duration connectTimeout,
            @Value("${external.api.gps-tugdk.read-timeout:60s}") Duration readTimeout,
            @Value("${external.api.gps-tugdk.write-timeout:10s}") Duration writeTimeout) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS)));

        log.info("TUGDK GPS API WebClient configured: baseUrl={}, responseTimeout={}s, connectTimeout={}s, readTimeout={}s, writeTimeout={}s",
                baseUrl, timeout.toSeconds(), connectTimeout.toSeconds(), readTimeout.toSeconds(), writeTimeout.toSeconds());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
