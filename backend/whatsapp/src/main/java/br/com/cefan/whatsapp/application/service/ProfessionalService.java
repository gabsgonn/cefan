package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.api.dto.ProfessionalDto;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final UserRepository userRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<ProfessionalDto> listAll(boolean onlyActive) {
        List<User> artists = onlyActive
                ? userRepository.findByRoleAndIsActive(UserRole.ARTIST, true)
                : userRepository.findByRole(UserRole.ARTIST);

        return artists.stream()
                .map(ProfessionalDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProfessionalDto getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado com ID: " + id));
        return ProfessionalDto.fromEntity(user);
    }

    @Transactional
    public ProfessionalDto create(ProfessionalDto dto) {
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new IllegalArgumentException("O e-mail do profissional é obrigatório.");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Já existe um profissional cadastrado com este e-mail: " + normalizedEmail);
        }

        TenantConfig tenant = tenantConfigRepository.findAll().stream().findFirst().orElse(null);

        String rawPassword = (dto.getPassword() != null && !dto.getPassword().isBlank())
                ? dto.getPassword().trim()
                : "cefan123";

        User user = User.builder()
                .tenant(tenant)
                .name(dto.getName() != null ? dto.getName().trim() : "Tatuador")
                .email(normalizedEmail)
                .phone(dto.getPhone() != null ? dto.getPhone().trim() : "")
                .role(UserRole.ARTIST)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .specialties(dto.getSpecialties() != null ? dto.getSpecialties().trim() : "Geral")
                .artistRateType(dto.getArtistRateType() != null ? dto.getArtistRateType() : "PERCENTAGE")
                .artistRateValue(dto.getArtistRateValue() != null ? dto.getArtistRateValue() : new BigDecimal("60.00"))
                .reputation(dto.getReputation() != null ? dto.getReputation() : new BigDecimal("5.0"))
                .hireDate(dto.getHireDate() != null ? dto.getHireDate() : LocalDate.now())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        user = userRepository.save(user);
        log.info("Novo profissional [{}] cadastrado com sucesso (ID: {}).", user.getName(), user.getId());
        return ProfessionalDto.fromEntity(user);
    }

    @Transactional
    public ProfessionalDto update(UUID id, ProfessionalDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado com ID: " + id));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            user.setName(dto.getName().trim());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone().trim());
        }
        if (dto.getSpecialties() != null) {
            user.setSpecialties(dto.getSpecialties().trim());
        }
        if (dto.getArtistRateType() != null) {
            user.setArtistRateType(dto.getArtistRateType());
        }
        if (dto.getArtistRateValue() != null) {
            user.setArtistRateValue(dto.getArtistRateValue());
        }
        if (dto.getReputation() != null) {
            user.setReputation(dto.getReputation());
        }
        if (dto.getHireDate() != null) {
            user.setHireDate(dto.getHireDate());
        }
        if (dto.getIsActive() != null) {
            user.setIsActive(dto.getIsActive());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword().trim()));
        }

        user = userRepository.save(user);
        log.info("Dados do profissional [{}] atualizados com sucesso.", user.getName());
        return ProfessionalDto.fromEntity(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado com ID: " + id));

        user.setIsActive(false);
        userRepository.save(user);
        log.info("Profissional [{}] desativado com sucesso (desativação lógica).", user.getName());
    }

    @Transactional
    public void seedInitialArtists() {
        long artistCount = userRepository.findByRole(UserRole.ARTIST).size();
        if (artistCount >= 11) {
            log.info("Equipe de profissionais já populada ({} encontrados).", artistCount);
            return;
        }

        TenantConfig tenant = tenantConfigRepository.findAll().stream().findFirst().orElse(null);

        List<ArtistSeedData> team = List.of(
                new ArtistSeedData("Carlos Eduardo", "carlos@viciotattoo.com", "5511988880001", "Fineline, Traço Fino, Minimalista", "PERCENTAGE", new BigDecimal("60.00"), new BigDecimal("4.9"), LocalDate.now().minusMonths(24)),
                new ArtistSeedData("Mariana Rocha", "mariana@viciotattoo.com", "5511988880002", "Realismo Preto e Cinza, Retratos", "PERCENTAGE", new BigDecimal("65.00"), new BigDecimal("5.0"), LocalDate.now().minusMonths(18)),
                new ArtistSeedData("Lucas 'Blade'", "lucas@viciotattoo.com", "5511988880003", "Blackwork, Tribal, Geométrico", "PERCENTAGE", new BigDecimal("60.00"), new BigDecimal("4.8"), LocalDate.now().minusMonths(30)),
                new ArtistSeedData("Beatriz Lima", "beatriz@viciotattoo.com", "5511988880004", "Aquarela, Floral, Botânico", "PERCENTAGE", new BigDecimal("60.00"), new BigDecimal("4.9"), LocalDate.now().minusMonths(12)),
                new ArtistSeedData("Rodrigo 'Old' Santos", "rodrigo@viciotattoo.com", "5511988880005", "Old School, Tradicional Americano", "PERCENTAGE", new BigDecimal("60.00"), new BigDecimal("4.9"), LocalDate.now().minusMonths(36)),
                new ArtistSeedData("Felipe Alcantara", "felipe@viciotattoo.com", "5511988880006", "Geométrico, Pontilhismo, Mandala", "PERCENTAGE", new BigDecimal("60.00"), new BigDecimal("4.8"), LocalDate.now().minusMonths(15)),
                new ArtistSeedData("Camila Fernandes", "camila@viciotattoo.com", "5511988880007", "Microrealismo, Caligrafia, Delicadas", "PERCENTAGE", new BigDecimal("65.00"), new BigDecimal("5.0"), LocalDate.now().minusMonths(20)),
                new ArtistSeedData("Gabriel Moura", "gabriel@viciotattoo.com", "5511988880008", "Neo Tradicional, Oriental, Anime", "PERCENTAGE", new BigDecimal("60.00"), new BigDecimal("4.8"), LocalDate.now().minusMonths(14)),
                new ArtistSeedData("Larissa Piercing", "larissa@viciotattoo.com", "5511988880009", "Body Piercing, Aplicações & Joias", "HOURLY", new BigDecimal("80.00"), new BigDecimal("4.9"), LocalDate.now().minusMonths(22)),
                new ArtistSeedData("Thiago 'Dark' Souza", "thiago@viciotattoo.com", "5511988880010", "Dark Art, Lettering, Chicano", "PERCENTAGE", new BigDecimal("60.00"), new BigDecimal("4.7"), LocalDate.now().minusMonths(10)),
                new ArtistSeedData("Julia Piercer", "julia@viciotattoo.com", "5511988880011", "Body Piercing, Microdermal, Mini Tattoos", "HOURLY", new BigDecimal("75.00"), new BigDecimal("4.9"), LocalDate.now().minusMonths(8))
        );

        for (ArtistSeedData a : team) {
            if (userRepository.findByEmail(a.email).isEmpty()) {
                userRepository.save(User.builder()
                        .tenant(tenant)
                        .name(a.name)
                        .email(a.email)
                        .phone(a.phone)
                        .role(UserRole.ARTIST)
                        .passwordHash(passwordEncoder.encode("cefan123"))
                        .specialties(a.specialties)
                        .artistRateType(a.rateType)
                        .artistRateValue(a.rateValue)
                        .reputation(a.reputation)
                        .hireDate(a.hireDate)
                        .isActive(true)
                        .build());
            }
        }
        log.info("Equipe de 11 profissionais do Vício Tattoo inicializada com sucesso!");
    }

    private record ArtistSeedData(
            String name,
            String email,
            String phone,
            String specialties,
            String rateType,
            BigDecimal rateValue,
            BigDecimal reputation,
            LocalDate hireDate
    ) {}
}
