package br.com.cefan.whatsapp.application.service;

import br.com.cefan.whatsapp.application.statemachine.handler.AgendamentoStateHandler;
import br.com.cefan.whatsapp.domain.model.Appointment;
import br.com.cefan.whatsapp.domain.model.AppointmentStatus;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.AppointmentRepository;
import br.com.cefan.whatsapp.domain.repository.CustomerRepository;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.ai.GeminiVisionClient;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.mercadopago.MercadoPagoPaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração contra PostgreSQL 15 real (Testcontainers) com Flyway completo (V1..V11).
 * Valida:
 * 1. Colunas scheduled_start / scheduled_end são timestamptz;
 * 2. Migração V10 (btree_gist + EXCLUDE USING gist) aplica sem erros;
 * 3. Duas threads concorrentes tentando o mesmo artista/horário -> exatamente 1 reserva vence;
 * 4. Sobreposição direta via repositório é barrada pela constraint excl_appointments_no_overlap_per_artist;
 * 5. cancelTemporaryReservation (REQUIRES_NEW) libera imediatamente a vaga para nova reserva.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AppointmentConcurrencyPostgresTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private AppointmentReservationService reservationService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TenantConfigRepository tenantConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EvolutionApiClient evolutionApiClient;

    @MockitoBean
    private GeminiVisionClient geminiVisionClient;

    @MockitoBean
    private MercadoPagoPaymentClient mercadoPagoPaymentClient;

    @Test
    void shouldApplyV10OnRealPostgresEnsureTimestamptzAndAllowOnlyOneConcurrentReservation() throws Exception {
        // 1. Confirma que scheduled_start e scheduled_end são timestamp with time zone (timestamptz)
        String startType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'appointments' AND column_name = 'scheduled_start'",
                String.class);
        String endType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'appointments' AND column_name = 'scheduled_end'",
                String.class);
        assertEquals("timestamp with time zone", startType);
        assertEquals("timestamp with time zone", endType);

        // 2. Confirma que a constraint EXCLUDE USING gist da V10 foi criada no PostgreSQL
        Integer constraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'excl_appointments_no_overlap_per_artist'",
                Integer.class);
        assertEquals(1, constraintCount);

        TenantConfig config = tenantConfigRepository.findAll().stream().findFirst().orElseThrow();
        User artist = userRepository.findByRoleAndIsActive(UserRole.ARTIST, true).stream().findFirst().orElseThrow();

        Customer c1 = customerRepository.save(Customer.builder().name("Cliente 1").phone("5541990000001").build());
        Customer c2 = customerRepository.save(Customer.builder().name("Cliente 2").phone("5541990000002").build());

        OffsetDateTime now = OffsetDateTime.now(AgendamentoStateHandler.STUDIO_ZONE);
        LocalDate nextWednesday = now.toLocalDate().plusDays(1);
        while (nextWednesday.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            nextWednesday = nextWednesday.plusDays(1);
        }
        ZoneOffset brt = AgendamentoStateHandler.STUDIO_ZONE.getRules().getOffset(nextWednesday.atTime(14, 0));
        OffsetDateTime slotStart = nextWednesday.atTime(14, 0).atOffset(brt);
        OffsetDateTime slotEnd = slotStart.plusHours(2);
        OffsetDateTime expiresAt = now.plusHours(2);

        // 3. Dispara 2 threads simultaneamente para o mesmo artista e horário
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<Appointment>> f1 = pool.submit(() -> {
                ready.countDown();
                fire.await(5, TimeUnit.SECONDS);
                return reservationService.reserveSlotAtomically(
                        c1, artist, slotStart, slotEnd,
                        new BigDecimal("400.00"), new BigDecimal("120.00"), expiresAt, config, now);
            });
            Future<Optional<Appointment>> f2 = pool.submit(() -> {
                ready.countDown();
                fire.await(5, TimeUnit.SECONDS);
                return reservationService.reserveSlotAtomically(
                        c2, artist, slotStart, slotEnd,
                        new BigDecimal("400.00"), new BigDecimal("120.00"), expiresAt, config, now);
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();

            Optional<Appointment> r1 = f1.get(15, TimeUnit.SECONDS);
            Optional<Appointment> r2 = f2.get(15, TimeUnit.SECONDS);

            // Exatamente 1 thread vence e a outra recebe Optional.empty()
            long winners = List.of(r1, r2).stream().filter(Optional::isPresent).count();
            assertEquals(1, winners, "Apenas 1 das 2 threads concorrentes pode reservar o horário");

            Appointment winner = r1.orElseGet(r2::orElseThrow);

            // 4. Inserção direta sobreposta (ex: via painel/SQL) deve ser barrada pela constraint EXCLUDE USING gist
            Appointment overlappingDirect = Appointment.builder()
                    .customer(c2)
                    .artist(artist)
                    .scheduledStart(slotStart.plusHours(1))
                    .scheduledEnd(slotEnd.plusHours(1))
                    .status(AppointmentStatus.CONFIRMADO)
                    .totalPrice(new BigDecimal("300.00"))
                    .depositAmount(new BigDecimal("90.00"))
                    .build();
            assertThrows(DataIntegrityViolationException.class, () -> appointmentRepository.saveAndFlush(overlappingDirect));

            // 5. cancelTemporaryReservation (REQUIRES_NEW) libera imediatamente o horário
            reservationService.cancelTemporaryReservation(winner);

            Optional<Appointment> afterCancel = reservationService.reserveSlotAtomically(
                    c2, artist, slotStart, slotEnd,
                    new BigDecimal("400.00"), new BigDecimal("120.00"), expiresAt, config, now);
            assertTrue(afterCancel.isPresent(), "Após cancelTemporaryReservation, o horário deve voltar a ficar livre");
        } finally {
            pool.shutdownNow();
        }
    }
}
