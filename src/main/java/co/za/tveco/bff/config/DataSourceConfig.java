package co.za.tveco.bff.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment environment) {
        ResolvedDataSource resolved = resolveDataSource(environment);
        log.info("Using datasource [{}] -> {}", resolved.source(), redactJdbcUrl(resolved.jdbcUrl()));

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
    String envSpringDatasourceUsername = System.getenv("SPRING_DATASOURCE_USERNAME");
    String envPgUser = System.getenv("PGUSER");
    String envDatabaseUsername = System.getenv("DATABASE_USERNAME");

    String envSpringDatasourcePassword = System.getenv("SPRING_DATASOURCE_PASSWORD");
    String envPgPassword = System.getenv("PGPASSWORD");
    String envDatabasePassword = System.getenv("DATABASE_PASSWORD");

        String fallbackUsername = firstNonBlank(
        envSpringDatasourceUsername,
        envPgUser,
        envDatabaseUsername,
        environment.getProperty("spring.datasource.username"),
                "postgres"
        );
        String fallbackPassword = firstNonBlank(
        envSpringDatasourcePassword,
        envPgPassword,
        envDatabasePassword,
        environment.getProperty("spring.datasource.password"),
                ""
        );

    String springDatasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
    String jdbcDatabaseUrl = System.getenv("JDBC_DATABASE_URL");
    String databaseUrl = System.getenv("DATABASE_URL");
    String databasePublicUrl = System.getenv("DATABASE_PUBLIC_URL");

    String rawUrl = firstNonBlank(
        springDatasourceUrl,
        jdbcDatabaseUrl,
        databaseUrl,
        databasePublicUrl,
        environment.getProperty("spring.datasource.url")
    );
    String source = sourceOf(
        rawUrl,
        springDatasourceUrl,
        jdbcDatabaseUrl,
        databaseUrl,
        databasePublicUrl,
        environment.getProperty("spring.datasource.url")
    );

    if (looksLikeRailwayInternalUrl(rawUrl) && hasText(databasePublicUrl)) {
        rawUrl = databasePublicUrl;
        source = "DATABASE_PUBLIC_URL (fallback from Railway internal host)";
    }

        if (hasText(rawUrl)) {
        return resolveFromUrl(rawUrl, source, fallbackUsername, fallbackPassword, shouldRequireSsl(rawUrl, source));
        }

    String host = firstNonBlank(System.getenv("PGHOST"), environment.getProperty("PGHOST"), "localhost");
    String port = firstNonBlank(System.getenv("PGPORT"), environment.getProperty("PGPORT"), "5432");
    String database = firstNonBlank(System.getenv("PGDATABASE"), environment.getProperty("PGDATABASE"), "tveco");

        return new ResolvedDataSource(
                "jdbc:postgresql://" + host + ":" + port + "/" + database,
                fallbackUsername,
        fallbackPassword,
        "PGHOST/PGPORT/PGDATABASE"
        );
    }

    private ResolvedDataSource resolveFromUrl(String rawUrl, String source, String fallbackUsername, String fallbackPassword, boolean requireSsl) {
        if (rawUrl.startsWith("jdbc:postgresql://")) {
        return new ResolvedDataSource(applySslMode(rawUrl, requireSsl), fallbackUsername, fallbackPassword, source);
        }

        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme();
        if (!"postgres".equalsIgnoreCase(scheme) && !"postgresql".equalsIgnoreCase(scheme)) {
        return new ResolvedDataSource(rawUrl, fallbackUsername, fallbackPassword, source);
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost())
                .append(":")
                .append(uri.getPort() > 0 ? uri.getPort() : 5432)
                .append(uri.getPath());
        if (hasText(uri.getQuery())) {
            jdbcUrl.append("?").append(uri.getQuery());
        }
        String finalJdbcUrl = applySslMode(jdbcUrl.toString(), requireSsl);

        String username = fallbackUsername;
        String password = fallbackPassword;
        if (hasText(uri.getUserInfo())) {
            String[] userInfoParts = uri.getUserInfo().split(":", 2);
            if (userInfoParts.length >= 1 && hasText(userInfoParts[0])) {
                username = userInfoParts[0];
            }
            if (userInfoParts.length == 2 && hasText(userInfoParts[1])) {
                password = userInfoParts[1];
            }
        }

        return new ResolvedDataSource(finalJdbcUrl, username, password, source);
    }

    private String sourceOf(String selected, String springDatasourceUrl, String jdbcDatabaseUrl, String databaseUrl, String databasePublicUrl, String propertyDatasourceUrl) {
        if (!hasText(selected)) {
            return "default";
        }
        if (selected.equals(springDatasourceUrl)) {
            return "SPRING_DATASOURCE_URL";
        }
        if (selected.equals(jdbcDatabaseUrl)) {
            return "JDBC_DATABASE_URL";
        }
        if (selected.equals(databaseUrl)) {
            return "DATABASE_URL";
        }
        if (selected.equals(databasePublicUrl)) {
            return "DATABASE_PUBLIC_URL";
        }
        if (selected.equals(propertyDatasourceUrl)) {
            return "spring.datasource.url property";
        }
        return "unknown";
    }

    private boolean looksLikeRailwayInternalUrl(String url) {
        return hasText(url) && url.contains("postgres.railway.internal");
    }

    private boolean shouldRequireSsl(String rawUrl, String source) {
        return hasText(rawUrl)
                && (rawUrl.contains("proxy.rlwy.net") || String.valueOf(source).contains("DATABASE_PUBLIC_URL"));
    }

    private String applySslMode(String jdbcUrl, boolean requireSsl) {
        if (!requireSsl || !hasText(jdbcUrl) || jdbcUrl.contains("sslmode=")) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
    }

    private String redactJdbcUrl(String jdbcUrl) {
        if (!hasText(jdbcUrl)) {
            return "<empty>";
        }
        return jdbcUrl.replaceAll("(//)([^/@:]+)(:[^/@]*)?@", "$1***:***@");
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

    private record ResolvedDataSource(String jdbcUrl, String username, String password, String source) {
    }
}