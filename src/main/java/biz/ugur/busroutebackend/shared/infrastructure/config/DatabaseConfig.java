package biz.ugur.busroutebackend.shared.infrastructure.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;

import java.time.Duration;
@Configuration
@EnableR2dbcRepositories(basePackages = {
        "biz.ugur.busroutebackend.transport.infrastructure.repository",
        "biz.ugur.busroutebackend.routing.infrastructure.repository",
        "biz.ugur.busroutebackend.admin.infrastructure.repository"
})
@EnableR2dbcAuditing
public class DatabaseConfig extends AbstractR2dbcConfiguration {

    @Value("${spring.r2dbc.username:bus_route_user}")
    private String username;

    @Value("${spring.r2dbc.password:bus_route_pass}")
    private String password;

    @Value("${spring.r2dbc.host:localhost}")
    private String host;

    @Value("${spring.r2dbc.port:5432}")
    private int port;

    @Value("${spring.r2dbc.database:bus_route_db}")
    private String database;

    @Value("${spring.r2dbc.pool.initial-size}")
    private Integer initialPoolSize;

    @Value("${spring.r2dbc.pool.max-size}")
    private Integer maxPoolSize;



    @Bean
    @Primary
    @Override
    public ConnectionFactory connectionFactory() {
        PostgresqlConnectionFactory factory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(host)
                        .port(port)
                        .database(database)
                        .username(username)
                        .password(password)
                        .connectTimeout(Duration.ofSeconds(30))
                        .build()
        );

        ConnectionPoolConfiguration config = ConnectionPoolConfiguration.builder(factory)
                .initialSize(initialPoolSize)
                .maxSize(maxPoolSize)
                .maxIdleTime(Duration.ofMinutes(10))
                .maxCreateConnectionTime(Duration.ofSeconds(30))
                .build();

        return new ConnectionPool(config);
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

}