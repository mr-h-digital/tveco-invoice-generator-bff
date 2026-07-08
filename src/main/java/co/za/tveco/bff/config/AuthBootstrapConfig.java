package co.za.tveco.bff.config;

import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AuthBootstrapConfig implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean bootstrapAdminEnabled;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminRole;

    public AuthBootstrapConfig(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.bootstrap-admin-enabled:true}") boolean bootstrapAdminEnabled,
            @Value("${app.auth.admin-email:admin@tveco.co.za}") String adminEmail,
            @Value("${app.auth.admin-password:tveco2026}") String adminPassword,
            @Value("${app.auth.admin-role:admin}") String adminRole
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapAdminEnabled = bootstrapAdminEnabled;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
        this.adminRole = adminRole.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapAdminEnabled) {
            return;
        }

        appUserRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() ->
                appUserRepository.save(AppUser.builder()
                        .email(adminEmail)
                        .passwordHash(passwordEncoder.encode(adminPassword))
                        .role(adminRole)
                        .active(true)
                        .build())
        );
    }
}
