package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.ChangePasswordRequest;
import co.za.tveco.bff.dto.ProfileResponse;
import co.za.tveco.bff.dto.UpdateProfileRequest;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.Client;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.exception.UnauthorizedException;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ClientRepository;
import co.za.tveco.bff.repository.RefreshTokenSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ProfileService {

    private final AppUserRepository appUserRepository;
    private final ClientRepository clientRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(
            AppUserRepository appUserRepository,
            ClientRepository clientRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.appUserRepository = appUserRepository;
        this.clientRepository = clientRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(String email) {
        AppUser user = findActiveUser(email);
        return toProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateMyProfile(String currentEmail, UpdateProfileRequest req) {
        AppUser user = findActiveUser(currentEmail);
        Client client = null;
        if ("client".equalsIgnoreCase(user.getRole())) {
            client = findClientForUser(user, true);
        }

        String normalizedEmail = req.email().trim().toLowerCase(Locale.ROOT);

        if (appUserRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, user.getId())) {
            throw new ConflictException("An account with this email already exists");
        }

        user.setEmail(normalizedEmail);
        appUserRepository.save(user);

        if (client != null) {
            if (clientRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, client.getId())) {
                throw new ConflictException("Another client profile already uses this email");
            }

            client.setEmail(normalizedEmail);
            if (isNotBlank(req.companyName())) {
                client.setCompanyName(req.companyName().trim());
            }
            if (isNotBlank(req.contactName())) {
                client.setContactName(req.contactName().trim());
            }
            if (isNotBlank(req.phone())) {
                client.setPhone(req.phone().trim());
            }
            if (isNotBlank(req.address())) {
                client.setAddress(req.address().trim());
            }
            clientRepository.save(client);
        }

        return toProfileResponse(user);
    }

    @Transactional
    public void changePassword(String currentEmail, ChangePasswordRequest req) {
        AppUser user = findActiveUser(currentEmail);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        appUserRepository.save(user);

        // Revoke all refresh sessions for security after password change.
        refreshTokenSessionRepository.deleteByUserId(user.getId());
    }

    private AppUser findActiveUser(String email) {
        return appUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthorizedException("User account not found"));
    }

    private Client findClientForUser(AppUser user) {
        return findClientForUser(user, false);
    }

    private Client findClientForUser(AppUser user, boolean relinkIfPossible) {
        if (user.getClientId() != null) {
            return clientRepository.findById(user.getClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        }

        Client client = clientRepository.findByEmailIgnoreCase(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile link is missing for this account"));

        if (relinkIfPossible) {
            user.setClientId(client.getId());
            appUserRepository.save(user);
        }

        return client;
    }

    private ProfileResponse toProfileResponse(AppUser user) {
        if (!"client".equalsIgnoreCase(user.getRole())) {
            return new ProfileResponse(
                    user.getEmail(),
                    user.getRole(),
                    null,
                    null,
                    null,
                    null
            );
        }

        Client client = findClientForUser(user);
        return new ProfileResponse(
                user.getEmail(),
                user.getRole(),
                client.getCompanyName(),
                client.getContactName(),
                client.getPhone(),
                client.getAddress()
        );
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
