package biz.ugur.busroutebackend.catalogsearch.infrastructure.cache;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.config.CatalogSearchProperties;
import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisCatalogSearchCacheIntegrationTest {

    private static final SearchHit HIT = new SearchHit(CatalogObjectKind.STOP, "stop-1",
            "Berkarar SM", null, 37.95, 58.38, 2.4, "CURATED");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static ReactiveRedisTemplate<String, Object> template;
    private RedisCatalogSearchCache cache;

    @BeforeAll
    static void initTemplate() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(new ObjectMapper());
        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext(new StringRedisSerializer())
                .value(valueSerializer)
                .hashKey(new StringRedisSerializer())
                .hashValue(valueSerializer)
                .build();
        template = new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    @AfterAll
    static void closeFactory() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        cache = new RedisCatalogSearchCache(template, new CatalogSearchProperties());
        template.scan(ScanOptions.scanOptions().match("*").build())
                .flatMap(template::unlink).blockLast();
    }

    private Long keysByPattern() {
        return template.scan(ScanOptions.scanOptions()
                        .match(RedisKeyRegistry.CatalogSearch.pattern()).build())
                .count().block();
    }

    @Test
    void gate8PutStoresKeyGetHitsAndEvictClearsByPrefix() {
        StepVerifier.create(cache.put("berkar", 10, List.of(HIT))).verifyComplete();
        assertThat(keysByPattern()).isEqualTo(1L);

        StepVerifier.create(cache.get("berkar", 10))
                .assertNext(hits -> {
                    assertThat(hits).hasSize(1);
                    assertThat(hits.get(0).objectId()).isEqualTo("stop-1");
                    assertThat(hits.get(0).objectKind()).isEqualTo(CatalogObjectKind.STOP);
                    assertThat(hits.get(0).score()).isEqualTo(2.4);
                })
                .verifyComplete();

        StepVerifier.create(cache.evictAll())
                .assertNext(removed -> assertThat(removed).isEqualTo(1L))
                .verifyComplete();
        assertThat(keysByPattern()).isZero();
    }

    @Test
    void missingKeyYieldsEmpty() {
        StepVerifier.create(cache.get("нет такого", 10)).verifyComplete();
    }
}
