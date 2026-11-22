package br.com.cefan.whatsapp.application.statemachine.handler;

import br.com.cefan.whatsapp.api.controller.StudioAdminController;
import br.com.cefan.whatsapp.application.service.ChatService;
import br.com.cefan.whatsapp.application.service.WhatsAppPollService;
import br.com.cefan.whatsapp.application.statemachine.ConversationStateHandler;
import br.com.cefan.whatsapp.application.statemachine.UserIntent;
import br.com.cefan.whatsapp.application.statemachine.UserIntentNormalizer;
import br.com.cefan.whatsapp.domain.model.ConversationSession;
import br.com.cefan.whatsapp.domain.model.ConversationState;
import br.com.cefan.whatsapp.domain.model.Customer;
import br.com.cefan.whatsapp.domain.model.TenantConfig;
import br.com.cefan.whatsapp.domain.model.User;
import br.com.cefan.whatsapp.domain.model.UserRole;
import br.com.cefan.whatsapp.domain.repository.TenantConfigRepository;
import br.com.cefan.whatsapp.domain.repository.UserRepository;
import br.com.cefan.whatsapp.infrastructure.evolution.EvolutionApiClient;
import br.com.cefan.whatsapp.infrastructure.evolution.dto.EvolutionWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MenuStateHandler implements ConversationStateHandler {

    private final EvolutionApiClient evolutionApiClient;
    private final ChatService chatService;
    private final TenantConfigRepository tenantConfigRepository;
    private final UserRepository userRepository;
    private final StudioAdminController studioAdminController;
    private final WhatsAppPollService pollService;

    public MenuStateHandler(
            EvolutionApiClient evolutionApiClient,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository) {
        this(evolutionApiClient, chatService, tenantConfigRepository, null, null, null);
    }

    public MenuStateHandler(
            EvolutionApiClient evolutionApiClient,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository) {
        this(evolutionApiClient, chatService, tenantConfigRepository, userRepository, null, null);
    }

    public MenuStateHandler(
            EvolutionApiClient evolutionApiClient,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository,
            StudioAdminController studioAdminController) {
        this(evolutionApiClient, chatService, tenantConfigRepository, userRepository, studioAdminController, null);
    }

    @Autowired
    public MenuStateHandler(
            EvolutionApiClient evolutionApiClient,
            ChatService chatService,
            TenantConfigRepository tenantConfigRepository,
            UserRepository userRepository,
            StudioAdminController studioAdminController,
            WhatsAppPollService pollService) {
        this.evolutionApiClient = evolutionApiClient;
        this.chatService = chatService;
        this.tenantConfigRepository = tenantConfigRepository;
        this.userRepository = userRepository;
        this.studioAdminController = studioAdminController;
        this.pollService = pollService;
    }

    private static final UserIntentNormalizer FALLBACK_NORMALIZER = new UserIntentNormalizer();

    @Override
    public ConversationState getSupportedState() {
        return ConversationState.MENU;
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText) {
        UserIntent intent = FALLBACK_NORMALIZER.normalize(ConversationState.MENU, false, messageText);
        handle(customer, session, webhook, messageText, intent);
    }

    @Override
    public void handle(Customer customer, ConversationSession session, EvolutionWebhookRequest webhook, String messageText, UserIntent intent) {
        UserIntent resolvedIntent = (intent != null)
                ? intent
                : FALLBACK_NORMALIZER.normalize(ConversationState.MENU, false, messageText);
        TenantConfig config = tenantConfigRepository.findAll().stream().findFirst().orElse(null);

        switch (resolvedIntent) {
            case ORCAMENTO -> {
                session.setCurrentState(ConversationState.ORCAMENTO_AGUARDANDO_FOTO);
                String customTemplate = resolveCleanOrcamentoIntro(config);
                String reply = String.format("Perfeito, %s! 🎨\n\n%s\n\n💡 _Você pode digitar 0 a qualquer momento para voltar ao menu inicial._",
                        customer.getName(), customTemplate);

                evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
                chatService.recordBotMessage(session, customer, reply);
            }
            case AGENDAR -> {
                session.setCurrentState(ConversationState.AGENDAMENTO_SELECAO_DATA);
                List<User> activeArtists = (userRepository != null)
                        ? userRepository.findAll().stream()
                                .filter(u -> u.getRole() == UserRole.ARTIST && Boolean.TRUE.equals(u.getIsActive()))
                                .toList()
                        : List.of();
                String reply = AgendamentoStateHandler.buildSchedulingEntryPrompt(
                        customer, session, config, activeArtists, null, true);

                evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
                chatService.recordBotMessage(session, customer, reply);
            }
            case FALAR_TATUADOR -> {
                session.setCurrentState(ConversationState.TRANSBORDO_HUMANO);
                String customTemplate = (config != null && config.getHumanTransferMessage() != null && !config.getHumanTransferMessage().isBlank())
                        ? config.getHumanTransferMessage()
                        : "Notifiquei nossa equipe e um dos nossos tatuadores irá te responder diretamente por aqui em instantes. Aguarde só um momento!";
                String absenceNotice = AgendamentoStateHandler.buildAbsenceNoticeIfOutsideHours(config);

                String reply = String.format("Entendido, %s! 👨‍🎨\n\n%s%s\n\n💡 _Se quiser reiniciar o assistente automático, basta enviar 0 a qualquer momento._",
                        customer.getName(), customTemplate, absenceNotice);

                evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), reply);
                chatService.recordBotMessage(session, customer, reply);
                if (studioAdminController != null) {
                    studioAdminController.publishNotification(
                            "HUMAN_REQUEST",
                            "Aguardando Tatuador",
                            customer.getName() + " solicitou atendimento humano no WhatsApp.",
                            customer.getPhone()
                    );
                }
            }
            case VER_ARTISTAS -> {
                String portfolioReply = buildArtistsPortfolioMessage(customer);
                evolutionApiClient.sendTextMessage(webhook.getInstance(), customer.getPhone(), portfolioReply);
                chatService.recordBotMessage(session, customer, portfolioReply);
            }
            default -> sendWelcomeMenu(customer, session, webhook.getInstance());
        }
    }

    public static String resolveCleanOrcamentoIntro(TenantConfig config) {
        String defaultIntro = "📸 *Envio de Referência para Orçamento*\n\n" +
                "Por favor, envie uma *foto da tatuagem* (ou referência visual) que você deseja fazer.\n\n" +
                "Se quiser, pode mandar na legenda o local do corpo ou a medida aproximada em cm (ex: antebraço, 12cm) — nossa IA já estima o tamanho automaticamente pela imagem!";

        if (config == null || config.getOrcamentoIntroMessage() == null || config.getOrcamentoIntroMessage().isBlank()) {
            return defaultIntro;
        }
        String custom = config.getOrcamentoIntroMessage().trim();
        // Ignora o template legado da V7 (mesmo parcialmente editado) que listava A-E na frente do cliente
        if (custom.contains("Opções de Tamanho da Tatuagem")
                || custom.contains("Digite *A*")
                || custom.contains("Flash / Mini")
                || custom.contains("26 cm+")) {
            return defaultIntro;
        }
        return custom;
    }

    private String buildArtistsPortfolioMessage(Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🌟 *Equipe de Especialistas - Vício Tattoo CWB* 🌟\n\nOlá, %s! Conheça nossos artistas residentes:\n\n", customer.getName()));

        List<User> artists = (userRepository != null)
                ? userRepository.findAll().stream()
                        .filter(u -> u.getRole() == UserRole.ARTIST && Boolean.TRUE.equals(u.getIsActive()))
                        .toList()
                : List.of();

        if (!artists.isEmpty()) {
            for (User artist : artists) {
                String spec = (artist.getSpecialties() != null && !artist.getSpecialties().isBlank())
                        ? artist.getSpecialties()
                        : "Tatuagem Autoral";
                String stars = (artist.getReputation() != null) ? "⭐ " + artist.getReputation() : "⭐ 5.0";
                sb.append(String.format("• *%s* (%s) — _%s_\n", artist.getName(), stars, spec));
            }
        } else {
            sb.append("• *Carlos Silveira* (⭐ 4.9) — _Realismo Preto e Cinza, Retratos_\n");
            sb.append("• *Mariana Rocha* (⭐ 5.0) — _Fineline, Traço Fino, Florais_\n");
            sb.append("• *Lucas Blade* (⭐ 4.8) — _Blackwork, Geometria, Tribal_\n");
            sb.append("• *Beatriz Lima* (⭐ 4.9) — _Aquarela, Colorido_\n");
            sb.append("• *Rodrigo Old School* (⭐ 4.9) — _Tradicional, Old School_\n");
            sb.append("• *Larissa & Julia* (⭐ 5.0) — _Body Piercing & Joias em Titânio_\n");
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("👉 Digite *1* para *Solicitar Orçamento com IA*\n");
        sb.append("👉 Digite *2* para *Agendar Horário*\n");
        sb.append("👉 Digite *3* para *Falar com um Tatuador*");
        return sb.toString();
    }

    public void sendWelcomeMenu(Customer customer, ConversationSession session, String instance) {
        TenantConfig config = tenantConfigRepository.findAll().stream().findFirst().orElse(null);
        String baseMenu = (config != null && config.getWelcomeMessage() != null && !config.getWelcomeMessage().isBlank())
                ? config.getWelcomeMessage()
                : String.format(
                "Olá, %s! 🎨 Bem-vindo ao *Vício Tattoo*!\n\n" +
                "Como posso te ajudar hoje?\n\n" +
                "1️⃣ - *Fazer Orçamento* (com foto de referência)\n" +
                "2️⃣ - *Agendar Sessão*\n" +
                "3️⃣ - *Falar com o Tatuador* (Atendimento Humano)\n\n" +
                "👉 _Digite o número da opção desejada._",
                customer.getName()
        );
        String menu = baseMenu + AgendamentoStateHandler.buildAbsenceNoticeIfOutsideHours(config);

        evolutionApiClient.sendTextMessage(instance, customer.getPhone(), menu);
        if (session != null) {
            chatService.recordBotMessage(session, customer, menu);
            if (pollService != null) {
                pollService.sendAndTrackPoll(
                        instance,
                        customer,
                        session,
                        ConversationState.MENU,
                        "Como posso te ajudar hoje? (ou digite o número acima)",
                        List.of(
                                new WhatsAppPollService.PollOption(0, "Fazer orçamento", UserIntent.ORCAMENTO),
                                new WhatsAppPollService.PollOption(1, "Agendar sessão", UserIntent.AGENDAR),
                                new WhatsAppPollService.PollOption(2, "Falar com o tatuador", UserIntent.FALAR_TATUADOR),
                                new WhatsAppPollService.PollOption(3, "Nossos artistas e portfólio", UserIntent.VER_ARTISTAS)
                        )
                );
            }
        }
    }

    public void sendWelcomeMenu(Customer customer, String instance) {
        sendWelcomeMenu(customer, null, instance);
    }
}
