import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ConfiguracoesComponent } from './configuracoes.component';
import { TenantConfigService } from '../../core/services/tenant-config.service';
import { AppointmentService } from '../../core/services/appointment.service';
import { ChatService } from '../../core/services/chat.service';
import { TenantConfig } from '../../core/models/tenant-config.model';

describe('ConfiguracoesComponent - Parametrização Global & Diagnóstico', () => {
  let fixture: ComponentFixture<ConfiguracoesComponent>;
  let component: ConfiguracoesComponent;
  let lastSavedConfig: TenantConfig | null = null;

  const mockConfig: TenantConfig = {
    id: 'cfg-1',
    studioName: 'Vício Tattoo CWB',
    defaultHourlyRate: 180,
    fixedSetupCost: 60,
    absorbCreditCardFees: true,
    signalPercentage: 30,
    minDepositAmount: 50,
    pixExpirationMinutes: 90,
    openingTime: '10:00',
    closingTime: '20:00',
    workingDays: 'SEG-SAB',
    welcomeMessage: 'Menu personalizado Vício Tattoo',
    orcamentoIntroMessage: 'Envie foto da tatuagem',
    agendamentoIntroMessage: 'Escolha data e profissional',
    pixPaymentMessage: 'Pague o sinal PIX abaixo',
    pixConfirmedMessage: 'PIX confirmado!',
    humanTransferMessage: 'Transferindo para tatuador',
    botResetMessage: 'Conversa reiniciada',
    absenceMessage: 'Fora de expediente (Seg-Sáb 10h às 20h)',
    orcamentoShowGreeting: true,
    orcamentoShowStyle: true,
    orcamentoShowSize: true,
    orcamentoShowDuration: true,
    orcamentoShowComplexity: true,
    orcamentoShowLocation: true,
    orcamentoShowArtists: true,
    orcamentoShowDescription: true,
    orcamentoShowPrice: true,
    orcamentoShowMaterialsNote: true,
    orcamentoShowAdjustHint: true
  };

  beforeEach(async () => {
    lastSavedConfig = null;

    await TestBed.configureTestingModule({
      imports: [ConfiguracoesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: TenantConfigService,
          useValue: {
            getConfig: () => of(mockConfig),
            updateConfig: (cfg: TenantConfig) => {
              lastSavedConfig = { ...cfg };
              return of(lastSavedConfig);
            }
          }
        },
        {
          provide: AppointmentService,
          useValue: {
            getNotificationPreferences: () =>
              of({
                notifyNewLead: true,
                notifyPaymentReceived: true,
                notifyHumanRequest: true
              }),
            updateNotificationPreferences: (prefs: any) => of(prefs)
          }
        },
        {
          provide: ChatService,
          useValue: {
            getBotStatus: () =>
              of({
                status: 'CONNECTED',
                instance: 'cefan-studio',
                webhookActive: true,
                springBootOnline: true,
                postgresOnline: true,
                evolutionApiConnected: true,
                mercadoPagoWebhookActive: true,
                timestamp: '2026-11-15T15:00:00Z'
              }),
            reconnectBot: () =>
              of({
                status: 'CONNECTED',
                restarted: true,
                instance: 'cefan-studio',
                message: 'Instância do Bot WhatsApp reiniciada com sucesso!',
                timestamp: '2026-11-15T15:05:00Z'
              })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfiguracoesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load all studio financial, schedule, PIX expiration and bot message parameters on init', () => {
    expect(component.config.studioName).toBe('Vício Tattoo CWB');
    expect(component.config.defaultHourlyRate).toBe(180);
    expect(component.config.fixedSetupCost).toBe(60);
    expect(component.config.pixExpirationMinutes).toBe(90);
    expect(component.config.openingTime).toBe('10:00');
    expect(component.config.closingTime).toBe('20:00');
    expect(component.config.workingDays).toBe('SEG-SAB');
    expect(component.config.pixPaymentMessage).toBe('Pague o sinal PIX abaixo');
    expect(component.config.absenceMessage).toContain('Fora de expediente');
    expect(component.botStatus()?.status).toBe('CONNECTED');
  });

  it('should persist updated parameters and retain values without resetting to 150', () => {
    component.config.defaultHourlyRate = 220;
    component.config.fixedSetupCost = 75;
    component.config.pixExpirationMinutes = 60;
    component.config.openingTime = '09:00';
    component.config.closingTime = '21:00';
    component.config.workingDays = 'SEG-SEX';

    component.saveConfig();

    expect(lastSavedConfig).toBeTruthy();
    expect(lastSavedConfig?.defaultHourlyRate).toBe(220);
    expect(lastSavedConfig?.fixedSetupCost).toBe(75);
    expect(lastSavedConfig?.pixExpirationMinutes).toBe(60);
    expect(lastSavedConfig?.openingTime).toBe('09:00');
    expect(lastSavedConfig?.closingTime).toBe('21:00');
    expect(lastSavedConfig?.workingDays).toBe('SEG-SEX');
    expect(component.feedbackMessage()).toContain('sucesso');
  });

  it('should switch to MESSAGES tab, restore default templates and save', () => {
    component.activeTab.set('MESSAGES');
    fixture.detectChanges();

    component.config.welcomeMessage = 'Texto alterado';
    component.restoreDefaultTemplates();

    expect(component.config.welcomeMessage).toContain('Vício Tattoo CWB');
    expect(component.config.orcamentoIntroMessage).toContain('Flash / Mini');
    expect(component.config.orcamentoIntroMessage).toContain('Fechamento');
    expect(component.config.orcamentoIntroMessage).toContain('Valor a combinar');
    expect(component.config.pixPaymentMessage).toContain('Pré-Agendamento Criado');
    expect(component.config.absenceMessage).toContain('Segunda a Sábado');
  });

  it('should test WhatsApp connection and restart bot instance from diagnostics card', () => {
    component.testWhatsAppConnection();
    expect(component.testingConnection()).toBe(false);
    expect(component.feedbackMessage()).toContain('operacional');

    component.restartWhatsAppBot();
    expect(component.restartingBot()).toBe(false);
    expect(component.feedbackMessage()).toContain('reiniciada');
  });

  it('should update live quote preview when quote visibility toggles change and persist them', () => {
    expect(component.buildQuotePreviewText()).toContain('Olá, Cliente!');
    expect(component.buildQuotePreviewText()).toContain('📌 *Estilo:* Old School / Tradicional');
    expect(component.buildQuotePreviewText()).toContain('💰 *Faixa de Preço Estimada:*');
    expect(component.buildQuotePreviewText()).toContain('⚠️ _Estimativa preliminar calculada por IA');
    expect(component.buildQuotePreviewText()).toContain('1️⃣ *Agendar sessão*');

    component.config.orcamentoShowPrice = false;
    component.config.orcamentoShowDescription = false;
    component.config.orcamentoShowGreeting = false;

    const updatedPreview = component.buildQuotePreviewText();
    expect(updatedPreview).not.toContain('Olá, Cliente!');
    expect(updatedPreview).not.toContain('💰 *Faixa de Preço Estimada:*');
    expect(updatedPreview).not.toContain('⚠️ _Estimativa preliminar calculada por IA');
    expect(updatedPreview).not.toContain('📝 *Sobre a sua arte:*');
    // 3 post-quote actions are non-parameterizable and always visible
    expect(updatedPreview).toContain('1️⃣ *Agendar sessão*');
    expect(updatedPreview).toContain('2️⃣ *Falar com o tatuador*');
    expect(updatedPreview).toContain('3️⃣ *Voltar ao menu*');

    component.saveConfig();
    expect(lastSavedConfig?.orcamentoShowPrice).toBe(false);
    expect(lastSavedConfig?.orcamentoShowDescription).toBe(false);
    expect(lastSavedConfig?.orcamentoShowGreeting).toBe(false);
  });
});
