import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, Subject } from 'rxjs';
import { AtendimentosComponent } from './atendimentos.component';
import { AppointmentService } from '../../core/services/appointment.service';
import { ChatService } from '../../core/services/chat.service';
import { AuthService } from '../../core/services/auth.service';
import { ProfessionalService } from '../../core/services/professional.service';
import { ConversationSession, ChatMessage } from '../../core/models/appointment.model';

describe('AtendimentosComponent - Live Chat & Resiliência', () => {
  let fixture: ComponentFixture<AtendimentosComponent>;
  let component: AtendimentosComponent;
  let globalStream$: Subject<ChatMessage>;
  let sessionStream$: Subject<ChatMessage>;

  const mockSessions: ConversationSession[] = [
    {
      id: 'sess-human',
      customerId: 'cust-1',
      customerName: 'Gabriel Silva',
      customerPhone: '5541999991111',
      currentState: 'TRANSBORDO_HUMANO',
      assignedUserId: 'prof-1',
      assignedArtistName: 'Carlos Silveira',
      contextData: JSON.stringify({
        style: 'Realismo Preto e Cinza',
        sizeCategory: 'MEDIA',
        estimatedSizeCm: '15x10 cm',
        estimatedHours: 3.0,
        suggestedBodyLocation: 'Antebraço',
        technicalNotes: 'Sombreamento com Magnum.',
        recommendedArtists: ['Carlos Silveira'],
        minPrice: 450,
        maxPrice: 550,
        referenceImageUrl: 'data:image/jpeg;base64,abc123'
      }),
      lastInteractionAt: '2026-11-15T14:00:00Z'
    },
    {
      id: 'sess-bot',
      customerId: 'cust-2',
      customerName: 'Camila Costa',
      customerPhone: '5541999992222',
      currentState: 'ORCAMENTO_AGUARDANDO_FOTO',
      lastInteractionAt: '2026-11-15T14:05:00Z'
    }
  ];

  const mockMessages: ChatMessage[] = [
    {
      id: 'msg-1',
      sessionId: 'sess-human',
      customerId: 'cust-1',
      senderType: 'CUSTOMER',
      senderName: 'Gabriel Silva',
      content: 'Quero fazer esse leão no antebraço',
      mediaUrl: 'data:image/jpeg;base64,abc123',
      isFromMe: false,
      createdAt: '2026-11-15T14:00:00Z'
    },
    {
      id: 'msg-2',
      sessionId: 'sess-human',
      customerId: 'cust-1',
      senderType: 'BOT',
      senderName: 'IA Vício Tattoo',
      content: '🎨 *Orçamento Estimado com Inteligência Artificial*',
      isFromMe: true,
      createdAt: '2026-11-15T14:00:05Z'
    }
  ];

  beforeEach(async () => {
    globalStream$ = new Subject<ChatMessage>();
    sessionStream$ = new Subject<ChatMessage>();

    await TestBed.configureTestingModule({
      imports: [AtendimentosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AppointmentService,
          useValue: {
            getConversations: () => of(mockSessions),
            updateConversationState: (id: string, state: any) =>
              of({ ...mockSessions[0], currentState: state })
          }
        },
        {
          provide: ChatService,
          useValue: {
            getMessages: () => of(mockMessages),
            sendMessage: (sessionId: string, message: string) =>
              of({
                id: 'msg-new',
                sessionId,
                customerId: 'cust-1',
                senderType: 'AGENT',
                senderName: 'Dono - Vício Tattoo',
                content: message,
                isFromMe: true,
                createdAt: '2026-11-15T14:10:00Z'
              }),
            streamMessages: () => sessionStream$.asObservable(),
            streamAllMessages: () => globalStream$.asObservable(),
            assignArtist: () => of(void 0),
            resetBotSession: () => of(void 0),
            getBotStatus: () =>
              of({
                status: 'CONNECTED',
                instance: 'cefan-studio',
                webhookActive: true,
                springBootOnline: true,
                postgresOnline: true,
                evolutionApiConnected: true,
                mercadoPagoWebhookActive: true,
                timestamp: '2026-11-15T14:00:00Z'
              }),
            reconnectBot: () =>
              of({
                status: 'CONNECTED',
                restarted: true,
                instance: 'cefan-studio',
                message: 'Instância reiniciada com sucesso!',
                timestamp: '2026-11-15T14:12:00Z'
              }),
            reportIssue: () =>
              of({
                protocol: 'INC-654321',
                status: 'REPORTED',
                category: 'WHATSAPP_DELAY',
                message: 'Problema reportado (INC-654321).',
                timestamp: '2026-11-15T14:13:00Z'
              })
          }
        },
        {
          provide: AuthService,
          useValue: {
            isOwner: () => true,
            isAuthenticated: () => true,
            token: () => 'fake-jwt-token',
            currentUser: () => ({
              id: 'owner-1',
              name: 'Dono - Vício Tattoo',
              email: 'admin@viciotattoo.com',
              role: 'OWNER',
              studioName: 'Vício Tattoo CWB',
              token: 'fake-jwt-token'
            })
          }
        },
        {
          provide: ProfessionalService,
          useValue: {
            listAll: () =>
              of([
                {
                  id: 'prof-1',
                  name: 'Carlos Silveira',
                  email: 'carlos@viciotattoo.com',
                  role: 'ARTIST',
                  specialties: 'Realismo Preto e Cinza',
                  rateType: 'PERCENTAGE',
                  rateValue: 60,
                  reputation: 4.9,
                  isActive: true
                }
              ])
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AtendimentosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load sessions and filter by state and professional', () => {
    expect(component.sessions().length).toBe(2);
    expect(component.humanCount()).toBe(1);
    expect(component.botCount()).toBe(1);

    component.setFilter('TRANSBORDO_HUMANO');
    expect(component.filteredSessions().length).toBe(1);
    expect(component.filteredSessions()[0].customerName).toBe('Gabriel Silva');

    component.setFilter('BOT');
    expect(component.filteredSessions().length).toBe(1);
    expect(component.filteredSessions()[0].customerName).toBe('Camila Costa');

    component.setFilter('ALL');
    component.setArtistFilter('UNASSIGNED');
    expect(component.filteredSessions().length).toBe(1);
    expect(component.filteredSessions()[0].customerName).toBe('Camila Costa');

    component.setArtistFilter('prof-1');
    expect(component.filteredSessions().length).toBe(1);
    expect(component.filteredSessions()[0].customerName).toBe('Gabriel Silva');
  });

  it('should select session, parse AI analysis, toggle retractable card and open zoom modal', () => {
    component.selectSession(mockSessions[0]);
    expect(component.selectedSession()?.id).toBe('sess-human');
    expect(component.messages().length).toBe(2);

    const art = component.parsedAiAnalysis();
    expect(art).toBeTruthy();
    expect(art?.style).toBe('Realismo Preto e Cinza');
    expect(component.sessionReferenceImage()).toBe('data:image/jpeg;base64,abc123');

    expect(component.aiCardExpanded()).toBe(true);
    component.toggleAiCard();
    expect(component.aiCardExpanded()).toBe(false);

    component.openImageZoom('data:image/jpeg;base64,abc123', 'Leão Realismo');
    expect(component.showImageModal()).toBe(true);
    expect(component.zoomImageUrl()).toBe('data:image/jpeg;base64,abc123');
  });

  it('should apply quick reply and send agent message', () => {
    component.selectSession(mockSessions[1]);
    component.applyQuickReply('Olá! Vamos agendar?');
    expect(component.replyText).toBe('Olá! Vamos agendar?');

    component.sendMessage();
    expect(component.replyText).toBe('');
    expect(component.selectedSession()?.currentState).toBe('TRANSBORDO_HUMANO');
    expect(component.messages().some(m => m.content === 'Olá! Vamos agendar?')).toBe(true);
  });

  it('should reset bot session and return to MENU', () => {
    component.promptResetBot(mockSessions[0]);
    expect(component.showResetModal()).toBe(true);

    component.confirmResetBot();
    expect(component.showResetModal()).toBe(false);
    expect(component.selectedSession()?.currentState).toBe('MENU');
  });

  it('should handle diagnostics modal, reconnect WhatsApp and report issue', () => {
    component.openDiagnosticsModal();
    expect(component.showDiagnosticsModal()).toBe(true);
    expect(component.botHealthDetails()?.postgresOnline).toBe(true);

    component.reconnectWhatsAppInstance();
    expect(component.botOnline()).toBe(true);
    expect(component.feedbackMessage()).toContain('reiniciada');

    component.issueDescription = 'Cliente enviou foto em formato HEIC';
    component.submitIssueReport();
    expect(component.showDiagnosticsModal()).toBe(false);
    expect(component.feedbackMessage()).toContain('INC-654321');
  });

  it('should safely escape HTML entities in formatMessageText while preserving markdown bold and italic', () => {
    const formatted = component.formatMessageText('Quero uma tattoo < 10cm no *antebraço* _delicada_ <img src=x onerror=alert(1)>');
    expect(formatted).toContain('&lt; 10cm');
    expect(formatted).toContain('<strong>antebraço</strong>');
    expect(formatted).toContain('<em>delicada</em>');
    expect(formatted).toContain('&lt;img src=x onerror=alert(1)&gt;');
    expect(formatted).not.toContain('<img');
  });

  it('should display "A combinar" when session AI analysis is Fechamento', () => {
    const fechamentoSession: ConversationSession = {
      ...mockSessions[0],
      id: 'sess-fechamento',
      contextData: JSON.stringify({
        style: 'Realismo Preto e Cinza',
        sizeCategory: 'FECHAMENTO',
        estimatedSizeCm: 'Fechamento (26 cm+ / Projeto Amplo)',
        isFechamento: true,
        priceDisplay: 'A combinar',
        recommendedArtists: ['Carlos Silveira']
      })
    };

    component.selectSession(fechamentoSession);
    fixture.detectChanges();

    const art = component.parsedAiAnalysis();
    expect(art?.isFechamento).toBe(true);
    expect(art?.priceDisplay).toBe('A combinar');
    expect(component.formatArtSize(art!)).toBe('Fechamento (26 cm+ / Projeto Amplo)');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('A combinar');
  });

  it('should not render blank AI card when contextData only contains pendingNotes and should format accented Média size without duplicating category', () => {
    const pendingOnlySession: ConversationSession = {
      ...mockSessions[0],
      id: 'sess-pending',
      contextData: JSON.stringify({ pendingNotes: 'Quero tatuar no braço' })
    };

    component.selectSession(pendingOnlySession);
    fixture.detectChanges();
    expect(component.parsedAiAnalysis()).toBeNull();

    expect(
      component.formatArtSize({
        sizeCategory: 'MEDIA',
        estimatedSizeCm: 'Média (11 a 18 cm)'
      })
    ).toBe('Média (11 a 18 cm)');
  });
});
