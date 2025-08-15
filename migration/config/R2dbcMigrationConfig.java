package biz.ugur.busroutebackend.migration.config;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.core.DatabaseClient;

import java.net.URI;

@Configuration
public class R2dbcMigrationConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.r2dbc")
    public R2dbcProperties mainR2dbcProperties() {
        return new R2dbcProperties();
    }

    @Bean
    @ConfigurationProperties("app.migration.source-db")
    public R2dbcProperties sourceR2dbcProperties() {
        return new R2dbcProperties();
    }

    @Bean
    @ConfigurationProperties("app.migration.target-db")
    public R2dbcProperties targetR2dbcProperties() {
        return new R2dbcProperties();
    }

    @Bean("migrationSourceConnectionFactory")
    public ConnectionFactory migrationSourceConnectionFactory(
            @Qualifier("sourceR2dbcProperties") R2dbcProperties props) {

        return new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(extractHost(props.getUrl()))
                        .port(extractPort(props.getUrl()))
                        .database(extractDatabase(props.getUrl()))
                        .username(props.getUsername())
                        .password(props.getPassword())
                        .build()
        );
    }

    @Bean("migrationTargetConnectionFactory")
    public ConnectionFactory migrationTargetConnectionFactory(
            @Qualifier("targetR2dbcProperties") R2dbcProperties props) {

        return new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(extractHost(props.getUrl()))
                        .port(extractPort(props.getUrl()))
                        .database(extractDatabase(props.getUrl()))
                        .username(props.getUsername())
                        .password(props.getPassword())
                        .build()
        );
    }

    @Bean("migrationSourceDatabaseClient")
    public DatabaseClient migrationSourceDatabaseClient(
            @Qualifier("migrationSourceConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean("migrationTargetDatabaseClient")
    public DatabaseClient migrationTargetDatabaseClient(
            @Qualifier("migrationTargetConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    private String extractHost(String url) {
        return URI.create(url.replace("r2dbc:", "")).getHost();
    }

    private int extractPort(String url) {
        int port = URI.create(url.replace("r2dbc:", "")).getPort();
        return port == -1 ? 5432 : port;
    }

    private String extractDatabase(String url) {
        String path = URI.create(url.replace("r2dbc:", "")).getPath();
        return path != null && path.length() > 1 ? path.substring(1) : "";
    }
}
