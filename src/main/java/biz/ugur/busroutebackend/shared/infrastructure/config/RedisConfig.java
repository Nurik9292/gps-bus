package biz.ugur.busroutebackend.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        RedisSerializationContext<String, Object> serializationContext =
                RedisSerializationContext.<String, Object>newSerializationContext()
                        .key(stringSerializer)           // Ключи как строки
                        .value(jsonSerializer)          // Значения как JSON
                        .hashKey(stringSerializer)      // ✅ Hash ключи как строки
                        .hashValue(jsonSerializer)      // ✅ Hash значения как JSON
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
