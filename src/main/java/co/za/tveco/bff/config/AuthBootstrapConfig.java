package co.za.tveco.bff.config;

import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AuthBootstrapConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrapConfig.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean bootstrapAdminEnabled;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminRole;
    private final String adminEmail2;
    private final String adminPassword2;
    private final String adminRole2;

    public AuthBootstrapConfig(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.bootstrap-admin-enabled:true}") boolean bootstrapAdminEnabled,
            @Value("${app.auth.admin-email:admin@tveco.co.za}") String adminEmail,
            @Value("${app.auth.admin-password:tveco2026}") String adminPassword,
            @Value("${app.auth.admin-role:admin}") String adminRole,
            @Value("${app.auth.admin-email-2:}") String adminEmail2,
            @Value("${app.auth.admin-password-2:}") String adminPassword2,
            @Value("${app.auth.admin-role-2:admin}") String adminRole2
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapAdminEnabled = bootstrapAdminEnabled;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
        this.adminRole = adminRole.trim().toLowerCase(Locale.ROOT);
        this.adminEmail2 = adminEmail2 == null ? "" : adminEmail2.trim().toLowerCase(Locale.ROOT);
        this.adminPassword2 = adminPassword2 == null ? "" : adminPassword2;
        this.adminRole2 = adminRole2 == null || adminRole2.isBlank() ? "admin" : adminRole2.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapAdminEnabled) {
            return;
        }

        bootstrapAccount(adminEmail, adminPassword, adminRole);

        if (!adminEmail2.isBlank()) {
            if (adminPassword2.isBlank()) {
                log.warn("ADMIN_EMAIL_2 is set but ADMIN_PASSWORD_2 is blank; skipping second admin bootstrap");
            } else {
                bootstrapAccount(adminEmail2, adminPassword2, adminRole2);
            }
        }
    }

    private void bootstrapAccount(String email, String password, String role) {
        appUserRepository.findByEmailIgnoreCase(email).orElseGet(() ->
                appUserRepository.save(AppUser.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(password))
                        .role(role)
                        .active(true)
                        .build())
        );
    }
}

