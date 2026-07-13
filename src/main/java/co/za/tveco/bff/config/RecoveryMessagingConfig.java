package co.za.tveco.bff.config;

import co.za.tveco.bff.service.RecoveryMessagingProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for recovery messaging providers.
 * Provides RestTemplate for HTTP-based providers (Meta WhatsApp, Twilio, etc.)
 */
@Configuration
public class RecoveryMessagingConfig {

    /**
     * RestTemplate bean for calling external messaging provider APIs.
     * Configured with reasonable timeouts and error handling.
     */
    @Bean
    public RestTemplate messagingRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(java.time.Duration.ofSeconds(5))
                .setReadTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }
}
