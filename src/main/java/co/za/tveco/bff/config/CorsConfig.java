package co.za.tveco.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Always include first-party TVECO origins even if env var formatting is incorrect.
        List<String> coreOrigins = List.of(
            "https://tveco.co.za",
            "https://www.tveco.co.za",
            "https://app.tveco.co.za",
            "http://localhost:5173",
            "http://localhost:4173"
        );

        List<String> configuredOrigins = Arrays.stream(allowedOrigins.split(","))
            .map(origin -> origin.replace("\"", "").trim())
            .filter(origin -> !origin.isEmpty() && !"null".equalsIgnoreCase(origin))
            .toList();

        List<String> normalizedOrigins = Stream.concat(coreOrigins.stream(), configuredOrigins.stream())
            .distinct()
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(normalizedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
