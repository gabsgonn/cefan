package br.com.cefan.whatsapp.infrastructure.security;

import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final br.com.cefan.whatsapp.application.service.ProfessionalService professionalService;

    @Value("${cefan.admin.email:admin@viciotattoo.com}")
    private String adminEmail;

    @Value("${cefan.admin.password:cefan123}")
    private String adminPassword;

    @Value("${cefan.admin.name:Dono - Vício Tattoo}")
    private String adminName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        TenantConfig tenant = tenantConfigRepository.findAll().stream().findFirst().orElseGet(() ->
                tenantConfigRepository.save(TenantConfig.builder()
                        .studioName("Vício Tattoo")
                        .defaultHourlyRate(new BigDecimal("150.00"))
                        .fixedSetupCost(new BigDecimal("50.00"))
                        .absorbCreditCardFees(false)
                        .build())
        );

        String normalizedEmail = adminEmail.trim().toLowerCase();
        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);

        if (existingUser.isEmpty()) {
            User owner = User.builder()
                    .tenant(tenant)
                    .name(adminName)
                    .email(normalizedEmail)
                    .phone("5511999990000")
                    .role(UserRole.OWNER)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .isActive(true)
                    .reputation(new BigDecimal("5.0"))
                    .hireDate(LocalDate.now())
                    .build();
            userRepository.save(owner);
            log.info("Usuário Dono inicial [{}] criado com sucesso com hash BCrypt.", normalizedEmail);
        } else {
            User user = existingUser.get();
            boolean needsUpdate = false;
            if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(adminPassword));
                needsUpdate = true;
            }
            if (user.getRole() != UserRole.OWNER) {
                user.setRole(UserRole.OWNER);
                needsUpdate = true;
            }
            if (needsUpdate) {
                userRepository.save(user);
                log.info("Credenciais e role do usuário Dono [{}] sincronizados.", normalizedEmail);
            }
        }

        // Popula a equipe dos 11 profissionais do estúdio Vício Tattoo se necessário
        professionalService.seedInitialArtists();
    }
}
