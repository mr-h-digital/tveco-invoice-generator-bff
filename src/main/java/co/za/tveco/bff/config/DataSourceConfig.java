package co.za.tveco.bff.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment environment) {
        ResolvedDataSource resolved = resolveDataSource(environment);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(resolved.jdbcUrl());
        if (hasText(resolved.username())) {
            dataSource.setUsername(resolved.username());
        }
        if (hasText(resolved.password())) {
            dataSource.setPassword(resolved.password());
        }
        return dataSource;
    }

    private ResolvedDataSource resolveDataSource(Environment environment) {
        String fallbackUsername = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_USERNAME"),
                environment.getProperty("PGUSER"),
                environment.getProperty("DATABASE_USERNAME"),
                "postgres"
        );
        String fallbackPassword = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_PASSWORD"),
                environment.getProperty("PGPASSWORD"),
                environment.getProperty("DATABASE_PASSWORD"),
                ""
        );

        String rawUrl = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("JDBC_DATABASE_URL"),
                environment.getProperty("DATABASE_PUBLIC_URL"),
                environment.getProperty("DATABASE_URL")
        );

        if (hasText(rawUrl)) {
            return resolveFromUrl(rawUrl, fallbackUsername, fallbackPassword);
        }

        String host = firstNonBlank(environment.getProperty("PGHOST"), "localhost");
        String port = firstNonBlank(environment.getProperty("PGPORT"), "5432");
        String database = firstNonBlank(environment.getProperty("PGDATABASE"), "tveco");

        return new ResolvedDataSource(
                "jdbc:postgresql://" + host + ":" + port + "/" + database,
                fallbackUsername,
                fallbackPassword
        );
    }

    private ResolvedDataSource resolveFromUrl(String rawUrl, String fallbackUsername, String fallbackPassword) {
        if (rawUrl.startsWith("jdbc:postgresql://")) {
            return new ResolvedDataSource(rawUrl, fallbackUsername, fallbackPassword);
        }

        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme();
        if (!"postgres".equalsIgnoreCase(scheme) && !"postgresql".equalsIgnoreCase(scheme)) {
            return new ResolvedDataSource(rawUrl, fallbackUsername, fallbackPassword);
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost())
                .append(":")
                .append(uri.getPort() > 0 ? uri.getPort() : 5432)
                .append(uri.getPath());
        if (hasText(uri.getQuery())) {
            jdbcUrl.append("?").append(uri.getQuery());
        }

        String username = fallbackUsername;
        String password = fallbackPassword;
        if (hasText(uri.getUserInfo())) {
            String[] userInfoParts = uri.getUserInfo().split(":", 2);
            if (!hasText(username) && userInfoParts.length >= 1) {
                username = userInfoParts[0];
            }
            if (!hasText(password) && userInfoParts.length == 2) {
                password = userInfoParts[1];
            }
        }

        return new ResolvedDataSource(jdbcUrl.toString(), username, password);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ResolvedDataSource(String jdbcUrl, String username, String password) {
    }
}