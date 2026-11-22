import {
  Component,
  OnInit,
  OnDestroy,
  ViewChild,
  ElementRef,
  inject,
  signal,
  computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AppointmentService } from '../../core/services/appointment.service';
import { ChatService, BotStatus } from '../../core/services/chat.service';
import { AuthService } from '../../core/services/auth.service';
import { ProfessionalService } from '../../core/services/professional.service';
import { ConversationSession, ConversationState, ChatMessage } from '../../core/models/appointment.model';
import { Professional } from '../../core/models/professional.model';
import { ModalComponent } from '../../shared/components/modal/modal.component';

export interface AiAnalysisParsed {
  style?: string;
  sizeCategory?: string;
  estimatedSizeCm?: string;
  estimatedHours?: number;
  complexityLevel?: string;
  detectedBodyLocation?: string;
  suggestedBodyLocation?: string;
  customerDescription?: string;
  technicalNotes?: string;
  recommendedArtists?: string[];
  basePrice?: number;
  minPrice?: number;
  maxPrice?: number;
  priceDisplay?: string;
  isFechamento?: boolean;
  assignedArtistName?: string;
  referenceImageUrl?: string;
}

@Component({
  selector: 'app-atendimentos',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  template: `
    <div class="page-container">
      <!-- Top Bar -->
      <div class="page-header">
        <div>
          <h1 class="page-title">Live Chat & Atendimentos</h1>
          <p class="page-subtitle">
            Comunicação direta em tempo real com clientes do WhatsApp via Evolution API.
            {{ authService.isOwner() ? 'Visão gerencial completa do estúdio.' : 'Atendimentos sob sua responsabilidade.' }}
          </p>
        </div>
        <div class="header-actions">
          <button
            type="button"
            class="bot-badge"
            [class.online]="botOnline()"
            (click)="openDiagnosticsModal()"
            title="Abrir Central de Diagnóstico e Resiliência do Bot">
            <span class="status-dot"></span>
            <span>{{ botOnline() ? 'Bot Conectado' : 'Bot Offline' }}</span>
          </button>

          <button
            type="button"
            class="btn btn-secondary"
            (click)="openDiagnosticsModal()"
            title="Painel de Saúde, Reinício do WhatsApp e Reporte de Problemas">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
            <span>Diagnóstico & Bot</span>
          </button>

          <button type="button" class="btn btn-secondary" (click)="loadSessions()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
            </svg>
            <span>Atualizar</span>
          </button>
        </div>
      </div>

      <!-- Feedback Alert -->
      <div *ngIf="feedbackMessage()" class="alert-banner" [ngClass]="feedbackType()">
        <span>{{ feedbackMessage() }}</span>
        <button class="alert-close" (click)="feedbackMessage.set(null)">×</button>
      </div>

      <!-- Main Chat Workspace (WhatsApp Web Style) -->
      <div class="chat-workspace" [class.session-active]="selectedSession() !== null">
        <!-- Left Sidebar: Session List -->
        <div class="sessions-sidebar">
          <div class="sidebar-search">
            <input
              type="text"
              [(ngModel)]="searchQuery"
              (ngModelChange)="onSearchChange($event)"
              placeholder="Buscar por cliente, telefone ou tatuador..."
              class="search-input"
            />
            <select
              *ngIf="authService.isOwner()"
              class="artist-filter-select"
              [ngModel]="artistFilter()"
              (ngModelChange)="setArtistFilter($event)">
              <option value="ALL">Todos os Profissionais</option>
              <option value="UNASSIGNED">Sem Tatuador Atribuído</option>
              <option *ngFor="let prof of professionals()" [value]="prof.id">
                🟣 {{ prof.name }}
              </option>
            </select>
          </div>

          <div class="sidebar-filters">
            <button
              type="button"
              class="filter-tab"
              [class.active]="stateFilter() === 'ALL'"
              (click)="setFilter('ALL')">
              Todas ({{ sessions().length }})
            </button>
            <button
              type="button"
              class="filter-tab"
              [class.active]="stateFilter() === 'TRANSBORDO_HUMANO'"
              (click)="setFilter('TRANSBORDO_HUMANO')">
              🟢 Humano ({{ humanCount() }})
            </button>
            <button
              type="button"
              class="filter-tab"
              [class.active]="stateFilter() === 'BOT'"
              (click)="setFilter('BOT')">
              🔵 Bot ({{ botCount() }})
            </button>
          </div>

          <div class="session-list">
            <div *ngIf="loadingSessions()" class="loading-state">
              Carregando conversas...
            </div>

            <div *ngIf="!loadingSessions() && filteredSessions().length === 0" class="empty-state">
              Nenhuma conversa encontrada para o filtro selecionado.
            </div>

            <div
              *ngFor="let s of filteredSessions()"
              class="session-item"
              [class.selected]="selectedSession()?.id === s.id"
              (click)="selectSession(s)">
              <div class="session-avatar">
                {{ (s.customerName || 'C').charAt(0).toUpperCase() }}
                <span
                  class="avatar-status-indicator"
                  [ngClass]="s.currentState === 'TRANSBORDO_HUMANO' ? 'ind-human' : 'ind-bot'"
                  [title]="s.currentState === 'TRANSBORDO_HUMANO' ? 'Em Atendimento Humano (Bot silenciado)' : 'Em Atendimento com o Bot'">
                </span>
              </div>
              <div class="session-info">
                <div class="session-top">
                  <span class="customer-name">{{ s.customerName || 'Cliente' }}</span>
                  <div class="session-meta-right">
                    <span class="unread-badge" *ngIf="getUnreadCount(s.id) > 0">
                      {{ getUnreadCount(s.id) }}
                    </span>
                    <span class="session-time">{{ formatTime(s.lastInteractionAt) }}</span>
                  </div>
                </div>
                <div class="session-middle">
                  <span class="customer-phone">{{ s.customerPhone }}</span>
                  <span class="assigned-pill" *ngIf="s.assignedArtistName">
                    🟣 {{ s.assignedArtistName }}
                  </span>
                </div>
                <div class="session-bottom">
                  <span class="state-pill" [ngClass]="getStateClass(s.currentState)">
                    {{ formatStateIndicator(s.currentState) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Main: Live Chat Window -->
        <div class="chat-main" *ngIf="selectedSession() as active; else noSessionSelected">
          <!-- Chat Header -->
          <div class="chat-header">
            <button
              type="button"
              class="mobile-back-btn"
              (click)="clearSelectedSession()"
              title="Voltar para a lista de conversas"
              aria-label="Voltar para a lista">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M15 18l-6-6 6-6"/>
              </svg>
              <span>Conversas</span>
            </button>
            <div class="chat-customer-info">
              <div class="chat-avatar">
                {{ (active.customerName || 'C').charAt(0).toUpperCase() }}
              </div>
              <div>
                <div class="chat-title-row">
                  <span class="customer-fullname">{{ active.customerName }}</span>
                  <span class="state-pill" [ngClass]="getStateClass(active.currentState)">
                    {{ formatStateIndicator(active.currentState) }}
                  </span>
                </div>
                <div class="customer-subinfo">
                  <span>{{ active.customerPhone }}</span>
                  <span *ngIf="active.assignedArtistName" class="assigned-tag">
                    • 🟣 Vinculado a: <strong>{{ active.assignedArtistName }}</strong>
                  </span>
                </div>
              </div>
            </div>

            <div class="chat-actions">
              <!-- Atribuição de Tatuador (Dono) -->
              <div class="assign-select-wrapper" *ngIf="authService.isOwner()">
                <select
                  class="assign-select"
                  [ngModel]="active.assignedUserId || null"
                  (ngModelChange)="onAssignArtist($event)">
                  <option [ngValue]="null">Atribuir Profissional...</option>
                  <option *ngFor="let artist of professionals()" [value]="artist.id">
                    {{ artist.name }} ({{ artist.specialties || 'Tatuador' }})
                  </option>
                </select>
              </div>

              <!-- Reset Bot Button -->
              <button
                type="button"
                class="btn btn-warning-outline"
                title="Reiniciar sessão do bot e enviar mensagem de boas-vindas"
                (click)="promptResetBot(active)">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M2.5 2v6h6M21.5 22v-6h-6"/>
                  <path d="M22 11.5A10 10 0 0 0 3.2 7.2M2 12.5a10 10 0 0 0 18.8 4.3"/>
                </svg>
                <span>Reset Bot</span>
              </button>

              <!-- Toggle Atendimento Humano -->
              <button
                type="button"
                class="btn btn-primary"
                *ngIf="active.currentState !== 'TRANSBORDO_HUMANO'"
                (click)="changeState(active, 'TRANSBORDO_HUMANO')">
                Assumir Conversa (Silenciar Bot)
              </button>
              <button
                type="button"
                class="btn btn-secondary"
                *ngIf="active.currentState === 'TRANSBORDO_HUMANO'"
                (click)="changeState(active, 'MENU')">
                Devolver p/ Assistente IA
              </button>
            </div>
          </div>

          <!-- Retractable AI Art Reference Card (Se houver análise de orçamento pela IA) -->
          <div class="ai-art-card" *ngIf="parsedAiAnalysis() as art">
            <div class="ai-card-header" (click)="toggleAiCard()" style="cursor: pointer;">
              <div class="ai-tag">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                </svg>
                <span>Resumo da Arte / Orçamento IA</span>
                <span class="ai-style-chip" *ngIf="art.style">{{ art.style }}</span>
              </div>

              <div class="ai-header-right">
                <span class="ai-price" *ngIf="art.isFechamento || art.sizeCategory === 'FECHAMENTO' || art.priceDisplay === 'A combinar'">
                  A combinar
                </span>
                <span class="ai-price" *ngIf="!art.isFechamento && art.sizeCategory !== 'FECHAMENTO' && art.priceDisplay !== 'A combinar' && art.minPrice && art.maxPrice">
                  R$ {{ art.minPrice }} a R$ {{ art.maxPrice }}
                </span>
                <button type="button" class="collapse-btn" [title]="aiCardExpanded() ? 'Recolher resumo' : 'Expandir resumo'">
                  {{ aiCardExpanded() ? '▲' : '▼' }}
                </button>
              </div>
            </div>

            <div class="ai-card-collapsible" *ngIf="aiCardExpanded()">
              <div class="ai-card-content-row">
                <!-- Thumbnail clicável com Zoom se houver imagem de referência -->
                <div
                  class="ai-reference-thumb"
                  *ngIf="sessionReferenceImage() as refImg"
                  (click)="openImageZoom(refImg, art.style || 'Referência da Arte')">
                  <img [src]="refImg" alt="Referência do cliente" />
                  <span class="zoom-overlay">🔍 Ampliar</span>
                </div>

                <div class="ai-metrics-wrap">
                  <div class="ai-card-body">
                    <div class="ai-metric" *ngIf="art.style">
                      <span class="ai-label">Estilo:</span>
                      <span class="ai-value">{{ art.style }}</span>
                    </div>
                    <div class="ai-metric" *ngIf="art.estimatedSizeCm || art.sizeCategory">
                      <span class="ai-label">Tamanho:</span>
                      <span class="ai-value">{{ formatArtSize(art) }}</span>
                    </div>
                    <div class="ai-metric" *ngIf="art.estimatedHours && !art.isFechamento && art.sizeCategory !== 'FECHAMENTO'">
                      <span class="ai-label">Tempo de Agulha:</span>
                      <span class="ai-value">~{{ art.estimatedHours }}h</span>
                    </div>
                    <div class="ai-metric" *ngIf="art.isFechamento || art.sizeCategory === 'FECHAMENTO' || art.priceDisplay === 'A combinar'">
                      <span class="ai-label">Valor Estimado:</span>
                      <span class="ai-value highlight-artist">A combinar com o tatuador</span>
                    </div>
                    <div class="ai-metric" *ngIf="art.detectedBodyLocation">
                      <span class="ai-label">Local Detectado:</span>
                      <span class="ai-value">{{ art.detectedBodyLocation }}</span>
                    </div>
                    <div class="ai-metric" *ngIf="art.suggestedBodyLocation">
                      <span class="ai-label">Local Sugerido:</span>
                      <span class="ai-value">{{ art.suggestedBodyLocation }}</span>
                    </div>
                    <div class="ai-metric" *ngIf="art.recommendedArtists && art.recommendedArtists.length > 0">
                      <span class="ai-label">Especialista Indicado:</span>
                      <span class="ai-value highlight-artist">{{ art.recommendedArtists.join(', ') }}</span>
                      <button
                        *ngIf="authService.isOwner() && canQuickAssignRecommended(art.recommendedArtists)"
                        type="button"
                        class="btn-quick-assign"
                        (click)="assignRecommendedArtist(art.recommendedArtists); $event.stopPropagation()">
                        Vincular Especialista
                      </button>
                    </div>
                  </div>

                  <div class="ai-notes" *ngIf="art.technicalNotes">
                    <strong>Observações Técnicas:</strong> {{ art.technicalNotes }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Message Bubbles Stream -->
          <div class="chat-messages" #scrollContainer>
            <div *ngIf="loadingMessages()" class="messages-loading">
              Carregando histórico de mensagens...
            </div>

            <div *ngIf="!loadingMessages() && messages().length === 0" class="no-messages-notice">
              Nenhuma mensagem registrada nesta sessão ainda. Envie uma mensagem abaixo para iniciar o contato!
            </div>

            <div
              *ngFor="let msg of messages()"
              class="message-row"
              [ngClass]="msg.isFromMe ? 'row-outgoing' : 'row-incoming'">

              <!-- Customer Message Bubble (Incoming) -->
              <div *ngIf="!msg.isFromMe" class="message-bubble bubble-customer">
                <div class="sender-tag">{{ msg.senderName || 'Cliente' }}</div>

                <!-- Imagem enviada pelo cliente (se presente como Data URI ou HTTP URL) -->
                <div
                  class="message-media"
                  *ngIf="isRenderableImage(msg.mediaUrl)"
                  (click)="openImageZoom(msg.mediaUrl!, 'Foto enviada por ' + (msg.senderName || 'Cliente'))">
                  <img [src]="msg.mediaUrl" alt="Foto enviada pelo cliente" class="chat-media-img" />
                  <span class="media-zoom-hint">🔍 Clique para ampliar</span>
                </div>

                <div class="media-placeholder-badge" *ngIf="msg.mediaUrl && !isRenderableImage(msg.mediaUrl)">
                  📸 Imagem de referência recebida via WhatsApp
                </div>

                <div class="message-body" [innerHTML]="formatMessageText(msg.content)"></div>
                <div class="message-meta">{{ formatClock(msg.createdAt) }}</div>
              </div>

              <!-- Bot or Agent Message Bubble (Outgoing) -->
              <div *ngIf="msg.isFromMe" class="message-bubble" [ngClass]="msg.senderType === 'BOT' ? 'bubble-bot' : 'bubble-agent'">
                <div class="sender-tag">
                  <span *ngIf="msg.senderType === 'BOT'">🤖 Assistente IA</span>
                  <span *ngIf="msg.senderType !== 'BOT'">👨‍🎨 {{ msg.senderName }}</span>
                </div>
                <div class="message-body" [innerHTML]="formatMessageText(msg.content)"></div>
                <div class="message-meta">
                  <span>{{ formatClock(msg.createdAt) }}</span>
                  <span class="check-icon">✓✓</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Quick Replies Bar -->
          <div class="quick-replies-bar">
            <span class="quick-label">Respostas rápidas:</span>
            <button
              *ngFor="let qr of quickReplies"
              type="button"
              class="quick-reply-chip"
              (click)="applyQuickReply(qr.text)">
              {{ qr.label }}
            </button>
          </div>

          <!-- Chat Input Bar -->
          <div class="chat-input-bar">
            <div class="input-container">
              <input
                type="text"
                [(ngModel)]="replyText"
                placeholder="Digite sua mensagem para o cliente no WhatsApp..."
                (keyup.enter)="sendMessage()"
                class="message-input"
                [disabled]="sending()"
              />
              <button
                type="button"
                class="send-btn"
                [disabled]="sending() || !replyText.trim()"
                (click)="sendMessage()">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="22" y1="2" x2="11" y2="13"/>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
              </button>
            </div>
            <div class="input-helper">
              <span>Pressione Enter para enviar diretamente ao WhatsApp do cliente (ativa transbordo humano automaticamente).</span>
              <span class="helper-status" *ngIf="selectedSession()?.currentState === 'TRANSBORDO_HUMANO'">
                🟢 Em Atendimento Humano (Bot Silenciado)
              </span>
            </div>
          </div>
        </div>

        <!-- No Selection Placeholder -->
        <ng-template #noSessionSelected>
          <div class="no-selection-panel">
            <div class="placeholder-icon">💬</div>
            <h3>Nenhum Atendimento Selecionado</h3>
            <p>Selecione uma conversa na barra lateral para visualizar o histórico de mensagens, o orçamento da IA e conversar em tempo real com o cliente.</p>
          </div>
        </ng-template>
      </div>
    </div>

    <!-- Modal 1: Zoom da Imagem de Referência -->
    <app-modal
      [isOpen]="showImageModal()"
      (isOpenChange)="showImageModal.set($event)"
      [title]="zoomImageTitle()"
      maxWidth="680px"
      [showFooter]="false">
      <div class="zoom-modal-body" *ngIf="zoomImageUrl()">
        <img [src]="zoomImageUrl()" alt="Arte ampliada" class="zoom-full-image" />
      </div>
    </app-modal>

    <!-- Modal 2: Confirmação de Reset da Sessão do Bot -->
    <app-modal
      [isOpen]="showResetModal()"
      (isOpenChange)="showResetModal.set($event)"
      title="Reiniciar Sessão do Bot do Cliente"
      maxWidth="480px">
      <div *ngIf="selectedSession() as s">
        <p style="font-size: 0.9rem; color: var(--text-secondary); line-height: 1.5;">
          Deseja reiniciar a conversa de <strong>{{ s.customerName }}</strong> ({{ s.customerPhone }})?
          <br><br>
          O estado atual (<strong>{{ formatState(s.currentState) }}</strong>) será limpo e o cliente receberá uma mensagem no WhatsApp retornando para o Menu Inicial do assistente.
        </p>
      </div>
      <div modal-footer style="display: flex; justify-content: flex-end; gap: 0.75rem;">
        <button type="button" class="btn btn-secondary" (click)="showResetModal.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" (click)="confirmResetBot()">Confirmar Reset do Bot</button>
      </div>
    </app-modal>

    <!-- Modal 3: Central de Diagnóstico, Reinício do WhatsApp e Reportar Problema -->
    <app-modal
      [isOpen]="showDiagnosticsModal()"
      (isOpenChange)="showDiagnosticsModal.set($event)"
      title="Central de Diagnóstico & Resiliência do Bot"
      maxWidth="600px"
      [showFooter]="false">
      <div class="diagnostics-container">
        <p class="diag-intro">
          Verifique o status operacional dos microsserviços em tempo real, reinicie a conexão com o WhatsApp ou reporte uma ocorrência técnica.
        </p>

        <div class="diag-grid">
          <div class="diag-card">
            <span class="diag-dot online"></span>
            <div>
              <div class="diag-name">Backend Spring Boot Core</div>
              <div class="diag-status">{{ botHealthDetails()?.springBootOnline !== false ? 'Online (Porta 8082)' : 'Indisponível' }}</div>
            </div>
          </div>

          <div class="diag-card">
            <span class="diag-dot" [class.online]="botHealthDetails()?.postgresOnline !== false"></span>
            <div>
              <div class="diag-name">Banco de Dados PostgreSQL</div>
              <div class="diag-status">{{ botHealthDetails()?.postgresOnline !== false ? 'Online & Persistindo' : 'Falha de Conexão' }}</div>
            </div>
          </div>

          <div class="diag-card">
            <span class="diag-dot" [class.online]="botOnline()"></span>
            <div>
              <div class="diag-name">Evolution API ({{ botHealthDetails()?.instance || 'cefan-studio' }})</div>
              <div class="diag-status">{{ botOnline() ? 'Instância Conectada' : 'Reconexão Necessária' }}</div>
            </div>
          </div>

          <div class="diag-card">
            <span class="diag-dot online"></span>
            <div>
              <div class="diag-name">Mercado Pago PIX Webhook</div>
              <div class="diag-status">{{ botHealthDetails()?.mercadoPagoWebhookActive !== false ? 'Ativo & Recebendo Eventos' : 'Inativo' }}</div>
            </div>
          </div>
        </div>

        <div class="diag-actions-bar">
          <button
            type="button"
            class="btn btn-secondary"
            [disabled]="reconnectingBot()"
            (click)="checkBotHealth(true)">
            🔄 Testar Conexão Agora
          </button>

          <button
            type="button"
            class="btn btn-primary"
            [disabled]="reconnectingBot()"
            (click)="reconnectWhatsAppInstance()">
            {{ reconnectingBot() ? 'Reiniciando Instância...' : '⚡ Reiniciar Instância WhatsApp' }}
          </button>
        </div>

        <hr class="diag-divider" />

        <div class="report-section">
          <h4>Reportar Problema Operacional</h4>
          <p>Caso um atendimento apresente comportamento inesperado, registre o diagnóstico para auditoria técnica:</p>

          <div class="report-form">
            <select [(ngModel)]="issueCategory" class="report-select">
              <option value="WHATSAPP_DELAY">Lentidão ou Atraso no WhatsApp</option>
              <option value="ORCAMENTO_IA">Divergência em Orçamento da IA</option>
              <option value="PIX_WEBHOOK">Dúvida na Confirmação de PIX</option>
              <option value="OUTRO">Outro Problema Operacional</option>
            </select>

            <textarea
              rows="3"
              [(ngModel)]="issueDescription"
              placeholder="Descreva brevemente o que aconteceu (ex: cliente enviou áudio ou travou na escolha de data)..."
              class="report-textarea"></textarea>

            <div style="display: flex; justify-content: flex-end;">
              <button
                type="button"
                class="btn btn-warning-outline"
                [disabled]="reportingIssue() || !issueDescription.trim()"
                (click)="submitIssueReport()">
                {{ reportingIssue() ? 'Enviando...' : '🚨 Registrar Problema com Diagnóstico' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </app-modal>
  `,
  styles: [`
    .page-container {
      max-width: 1400px;
      margin: 0 auto;
      padding: 1.5rem;
      height: calc(100vh - 70px);
      display: flex;
      flex-direction: column;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
      flex-shrink: 0;
    }

    .page-title {
      font-size: 1.5rem;
      font-weight: 800;
      letter-spacing: -0.02em;
      color: var(--text-primary);
    }

    .page-subtitle {
      font-size: 0.85rem;
      color: var(--text-secondary);
      margin-top: 0.2rem;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .bot-badge {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.4rem 0.85rem;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-full);
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-secondary);
      cursor: pointer;

      &.online {
        color: var(--color-success);
        border-color: rgba(16, 185, 129, 0.3);
        background: var(--color-success-bg);
      }

      .status-dot {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background-color: var(--color-danger);
      }

      &.online .status-dot {
        background-color: var(--color-success);
      }
    }

    .alert-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 1.25rem;
      border-radius: var(--radius-md);
      margin-bottom: 0.75rem;
      font-size: 0.85rem;
      flex-shrink: 0;

      &.success {
        background-color: var(--color-success-bg);
        color: var(--color-success);
        border: 1px solid rgba(16, 185, 129, 0.3);
      }
      &.error {
        background-color: var(--color-danger-bg);
        color: var(--color-danger);
        border: 1px solid rgba(239, 68, 68, 0.3);
      }
    }

    .alert-close {
      background: none;
      border: none;
      color: inherit;
      font-size: 1.2rem;
      cursor: pointer;
    }

    /* Workspace */
    .chat-workspace {
      display: grid;
      grid-template-columns: 350px 1fr;
      gap: 0;
      flex: 1;
      min-height: 0;
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      overflow: hidden;
    }

    /* Left Sidebar */
    .sessions-sidebar {
      display: flex;
      flex-direction: column;
      border-right: 1px solid var(--border-color);
      background-color: var(--bg-surface);
      min-height: 0;
    }

    .sidebar-search {
      padding: 0.75rem;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      gap: 0.5rem;

      .search-input, .artist-filter-select {
        width: 100%;
        padding: 0.55rem 0.85rem;
        background-color: var(--bg-card);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-size: 0.8rem;
        outline: none;
        &:focus {
          border-color: var(--accent-primary);
        }
      }
    }

    .sidebar-filters {
      display: flex;
      padding: 0.5rem 0.75rem;
      gap: 0.35rem;
      border-bottom: 1px solid var(--border-color);
    }

    .filter-tab {
      flex: 1;
      padding: 0.4rem 0.25rem;
      font-size: 0.72rem;
      font-weight: 600;
      background: none;
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      color: var(--text-secondary);
      cursor: pointer;
      transition: all 0.2s;

      &.active {
        background-color: var(--accent-primary);
        color: #fff;
        border-color: var(--accent-primary);
      }
    }

    .session-list {
      flex: 1;
      overflow-y: auto;
    }

    .loading-state, .empty-state {
      padding: 2rem 1rem;
      text-align: center;
      font-size: 0.82rem;
      color: var(--text-muted);
    }

    .session-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.85rem 1rem;
      border-bottom: 1px solid var(--border-color);
      cursor: pointer;
      transition: background 0.15s, transform 0.1s;

      &:hover {
        background-color: var(--bg-card);
      }

      &:active {
        background-color: rgba(99, 102, 241, 0.2);
        transform: scale(0.99);
      }

      &.selected {
        background-color: rgba(99, 102, 241, 0.12);
        border-left: 3px solid var(--accent-primary);
      }
    }

    .session-avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: linear-gradient(135deg, #4f46e5, #7c3aed);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      color: white;
      font-size: 0.95rem;
      flex-shrink: 0;
      position: relative;
    }

    .avatar-status-indicator {
      position: absolute;
      bottom: -1px;
      right: -1px;
      width: 11px;
      height: 11px;
      border-radius: 50%;
      border: 2px solid var(--bg-surface);

      &.ind-human {
        background-color: #10b981;
      }
      &.ind-bot {
        background-color: #3b82f6;
      }
    }

    .session-info {
      flex: 1;
      min-width: 0;
    }

    .session-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.2rem;
    }

    .session-meta-right {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .unread-badge {
      background-color: #10b981;
      color: #fff;
      font-size: 0.65rem;
      font-weight: 800;
      padding: 0.08rem 0.4rem;
      border-radius: var(--radius-full);
    }

    .customer-name {
      font-size: 0.875rem;
      font-weight: 700;
      color: var(--text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .session-time {
      font-size: 0.7rem;
      color: var(--text-muted);
      flex-shrink: 0;
    }

    .session-middle {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.35rem;
      gap: 0.35rem;
    }

    .customer-phone {
      font-size: 0.75rem;
      color: var(--text-secondary);
    }

    .assigned-pill {
      font-size: 0.65rem;
      background: rgba(139, 92, 246, 0.15);
      color: #c084fc;
      padding: 0.1rem 0.4rem;
      border-radius: var(--radius-sm);
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 140px;
    }

    .session-bottom {
      display: flex;
      align-items: center;
    }

    .state-pill {
      font-size: 0.65rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
      letter-spacing: 0.02em;

      &.state-human {
        background-color: rgba(16, 185, 129, 0.15);
        color: #34d399;
        border: 1px solid rgba(16, 185, 129, 0.35);
      }
      &.state-orcamento {
        background-color: rgba(139, 92, 246, 0.2);
        color: #a78bfa;
        border: 1px solid rgba(139, 92, 246, 0.4);
      }
      &.state-agendamento {
        background-color: rgba(59, 130, 246, 0.2);
        color: #60a5fa;
        border: 1px solid rgba(59, 130, 246, 0.4);
      }
      &.state-menu {
        background-color: rgba(59, 130, 246, 0.12);
        color: #93c5fd;
        border: 1px solid rgba(59, 130, 246, 0.25);
      }
    }

    /* Right Main Panel */
    .chat-main {
      display: flex;
      flex-direction: column;
      min-height: 0;
      background-color: var(--bg-surface);
    }

    .chat-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.85rem 1.25rem;
      border-bottom: 1px solid var(--border-color);
      background-color: var(--bg-card);
      flex-shrink: 0;
      gap: 1rem;
    }

    .chat-customer-info {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .chat-avatar {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      color: white;
      font-size: 1.1rem;
    }

    .chat-title-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .customer-fullname {
      font-size: 1rem;
      font-weight: 800;
      color: var(--text-primary);
    }

    .customer-subinfo {
      font-size: 0.775rem;
      color: var(--text-secondary);
      margin-top: 0.15rem;
    }

    .assigned-tag {
      color: #c084fc;
    }

    .chat-actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .assign-select {
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      color: var(--text-primary);
      padding: 0.45rem 0.65rem;
      font-size: 0.75rem;
      outline: none;
    }

    .btn-warning-outline {
      background: transparent;
      border: 1px solid rgba(245, 158, 11, 0.4);
      color: #f59e0b;
      padding: 0.45rem 0.75rem;
      border-radius: var(--radius-md);
      font-size: 0.75rem;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 0.35rem;
      cursor: pointer;
      &:hover:not(:disabled) {
        background: rgba(245, 158, 11, 0.1);
      }
      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }

    /* AI Art Preview Card */
    .ai-art-card {
      margin: 0.65rem 1.25rem 0.25rem 1.25rem;
      padding: 0.75rem 1rem;
      background: linear-gradient(145deg, rgba(30, 27, 75, 0.75), rgba(15, 23, 42, 0.92));
      border: 1px solid rgba(99, 102, 241, 0.35);
      border-radius: var(--radius-md);
      flex-shrink: 0;
    }

    .ai-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .ai-tag {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      color: #818cf8;
      font-size: 0.8rem;
      font-weight: 700;
    }

    .ai-style-chip {
      background: rgba(99, 102, 241, 0.2);
      color: #c7d2fe;
      padding: 0.1rem 0.45rem;
      border-radius: var(--radius-sm);
      font-size: 0.7rem;
    }

    .ai-header-right {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .ai-price {
      font-size: 0.9rem;
      font-weight: 800;
      color: #34d399;
      background: rgba(16, 185, 129, 0.15);
      padding: 0.2rem 0.5rem;
      border-radius: var(--radius-sm);
    }

    .collapse-btn {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      border-radius: var(--radius-sm);
      width: 24px;
      height: 24px;
      font-size: 0.65rem;
      cursor: pointer;
    }

    .ai-card-collapsible {
      margin-top: 0.65rem;
      padding-top: 0.65rem;
      border-top: 1px solid rgba(99, 102, 241, 0.2);
    }

    .ai-card-content-row {
      display: flex;
      gap: 1rem;
      align-items: flex-start;
    }

    .ai-reference-thumb {
      width: 72px;
      height: 72px;
      border-radius: var(--radius-md);
      overflow: hidden;
      border: 1px solid rgba(129, 140, 248, 0.4);
      position: relative;
      cursor: pointer;
      flex-shrink: 0;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .zoom-overlay {
        position: absolute;
        inset: 0;
        background: rgba(0, 0, 0, 0.55);
        color: #fff;
        font-size: 0.62rem;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transition: opacity 0.2s;
      }

      &:hover .zoom-overlay {
        opacity: 1;
      }
    }

    .ai-metrics-wrap {
      flex: 1;
    }

    .ai-card-body {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1.25rem;
      font-size: 0.775rem;
    }

    .ai-metric {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      .ai-label { color: var(--text-muted); }
      .ai-value { color: var(--text-primary); font-weight: 600; }
      .highlight-artist { color: #f59e0b; }
    }

    .btn-quick-assign {
      background: rgba(245, 158, 11, 0.15);
      border: 1px solid rgba(245, 158, 11, 0.4);
      color: #fbbf24;
      padding: 0.1rem 0.45rem;
      border-radius: var(--radius-sm);
      font-size: 0.68rem;
      font-weight: 700;
      cursor: pointer;
      &:hover {
        background: rgba(245, 158, 11, 0.28);
      }
    }

    .ai-notes {
      margin-top: 0.4rem;
      font-size: 0.725rem;
      color: var(--text-secondary);
      line-height: 1.35;
    }

    /* Chat Messages Stream */
    .chat-messages {
      flex: 1;
      overflow-y: auto;
      padding: 1rem 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .messages-loading, .no-messages-notice {
      text-align: center;
      padding: 2rem;
      font-size: 0.85rem;
      color: var(--text-muted);
    }

    .message-row {
      display: flex;
      width: 100%;

      &.row-incoming {
        justify-content: flex-start;
      }
      &.row-outgoing {
        justify-content: flex-end;
      }
    }

    .message-bubble {
      max-width: min(72%, 600px);
      padding: 0.65rem 0.9rem;
      border-radius: var(--radius-lg);
      font-size: 0.85rem;
      line-height: 1.4;
      position: relative;
      box-shadow: var(--shadow-sm);

      .sender-tag {
        font-size: 0.7rem;
        font-weight: 700;
        margin-bottom: 0.2rem;
      }

      .message-body {
        white-space: pre-wrap;
        word-break: break-word;
      }

      .message-meta {
        font-size: 0.65rem;
        text-align: right;
        margin-top: 0.25rem;
        display: flex;
        justify-content: flex-end;
        align-items: center;
        gap: 0.25rem;
      }
    }

    .message-media {
      margin: 0.35rem 0;
      cursor: pointer;
      border-radius: var(--radius-md);
      overflow: hidden;
      border: 1px solid var(--border-color);
      max-width: 240px;

      .chat-media-img {
        width: 100%;
        max-height: 220px;
        object-fit: cover;
        display: block;
      }

      .media-zoom-hint {
        display: block;
        font-size: 0.65rem;
        padding: 0.2rem 0.5rem;
        background: rgba(0, 0, 0, 0.45);
        color: #cbd5e1;
        text-align: center;
      }
    }

    .media-placeholder-badge {
      margin: 0.25rem 0 0.35rem 0;
      padding: 0.3rem 0.55rem;
      background: rgba(99, 102, 241, 0.12);
      border: 1px dashed rgba(99, 102, 241, 0.35);
      border-radius: var(--radius-sm);
      font-size: 0.72rem;
      color: #a5b4fc;
    }

    .bubble-customer {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      border-bottom-left-radius: 2px;

      .sender-tag { color: #818cf8; }
      .message-meta { color: var(--text-muted); }
    }

    .bubble-bot {
      background: linear-gradient(135deg, rgba(79, 70, 229, 0.2), rgba(124, 58, 237, 0.25));
      border: 1px solid rgba(139, 92, 246, 0.4);
      color: var(--text-primary);
      border-bottom-right-radius: 2px;

      .sender-tag { color: #c084fc; }
      .message-meta { color: var(--text-muted); }
      .check-icon { color: #a78bfa; }
    }

    .bubble-agent {
      background: linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(5, 150, 105, 0.2));
      border: 1px solid rgba(16, 185, 129, 0.35);
      color: var(--text-primary);
      border-bottom-right-radius: 2px;

      .sender-tag { color: #34d399; }
      .message-meta { color: var(--text-muted); }
      .check-icon { color: #34d399; }
    }

    /* Quick Replies Bar */
    .quick-replies-bar {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.45rem 1.25rem;
      background-color: var(--bg-card);
      border-top: 1px solid var(--border-color);
      overflow-x: auto;
      flex-shrink: 0;
      scrollbar-width: none;

      .quick-label {
        font-size: 0.7rem;
        font-weight: 700;
        color: var(--text-muted);
        white-space: nowrap;
        margin-right: 0.2rem;
      }

      .quick-reply-chip {
        background: var(--bg-surface);
        border: 1px solid var(--border-color);
        color: var(--text-secondary);
        padding: 0.25rem 0.6rem;
        border-radius: var(--radius-full);
        font-size: 0.72rem;
        white-space: nowrap;
        cursor: pointer;
        transition: all 0.15s;

        &:hover {
          border-color: var(--accent-primary);
          color: var(--text-primary);
        }
      }
    }

    /* Input Bar */
    .chat-input-bar {
      padding: 0.75rem 1.25rem;
      border-top: 1px solid var(--border-color);
      background-color: var(--bg-card);
      flex-shrink: 0;
    }

    .input-container {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }

    .message-input {
      flex: 1;
      padding: 0.7rem 1rem;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      color: var(--text-primary);
      font-size: 0.875rem;
      outline: none;
      transition: border-color 0.2s;

      &:focus {
        border-color: var(--accent-primary);
      }
    }

    .send-btn {
      width: 42px;
      height: 42px;
      border-radius: var(--radius-md);
      background-color: var(--accent-primary);
      color: white;
      border: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      box-shadow: 0 2px 8px var(--accent-glow);
      transition: opacity 0.2s;

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }

    .input-helper {
      display: flex;
      justify-content: space-between;
      margin-top: 0.35rem;
      font-size: 0.7rem;
      color: var(--text-muted);

      .helper-status {
        color: #34d399;
        font-weight: 700;
      }
    }

    .no-selection-panel {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      text-align: center;
      padding: 2rem;
      color: var(--text-muted);

      .placeholder-icon {
        font-size: 3rem;
        margin-bottom: 1rem;
        opacity: 0.5;
      }
      h3 {
        font-size: 1.15rem;
        font-weight: 700;
        color: var(--text-primary);
        margin-bottom: 0.35rem;
      }
      p {
        font-size: 0.85rem;
        max-width: 400px;
      }
    }

    /* Modals */
    .zoom-modal-body {
      display: flex;
      justify-content: center;
      align-items: center;
      background: #090d16;
      border-radius: var(--radius-md);
      padding: 0.5rem;
    }

    .zoom-full-image {
      max-width: 100%;
      max-height: 72vh;
      object-fit: contain;
      border-radius: var(--radius-sm);
    }

    .diagnostics-container {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .diag-intro {
      font-size: 0.85rem;
      color: var(--text-secondary);
      line-height: 1.4;
    }

    .diag-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.75rem;
    }

    .diag-card {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding: 0.75rem;
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);

      .diag-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background-color: var(--color-danger);
        flex-shrink: 0;
        &.online {
          background-color: var(--color-success);
          box-shadow: 0 0 8px var(--color-success);
        }
      }

      .diag-name {
        font-size: 0.78rem;
        font-weight: 700;
        color: var(--text-primary);
      }

      .diag-status {
        font-size: 0.7rem;
        color: var(--text-secondary);
      }
    }

    .diag-actions-bar {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
    }

    .diag-divider {
      border: none;
      border-top: 1px solid var(--border-color);
      margin: 0.25rem 0;
    }

    .report-section {
      h4 {
        font-size: 0.95rem;
        font-weight: 700;
        color: var(--text-primary);
        margin-bottom: 0.2rem;
      }
      p {
        font-size: 0.78rem;
        color: var(--text-secondary);
        margin-bottom: 0.75rem;
      }
    }

    .report-form {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }

    .report-select, .report-textarea {
      width: 100%;
      padding: 0.6rem 0.85rem;
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      color: var(--text-primary);
      font-family: inherit;
      font-size: 0.82rem;
      outline: none;
      &:focus {
        border-color: var(--accent-primary);
      }
    }

    .mobile-back-btn {
      display: none;
      align-items: center;
      gap: 0.35rem;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid var(--border-color);
      color: #818cf8;
      font-size: 0.8rem;
      font-weight: 700;
      padding: 0.45rem 0.75rem;
      border-radius: var(--radius-full);
      cursor: pointer;
      min-height: 40px;
      margin-right: 0.35rem;
      transition: all 0.15s ease;

      &:active {
        transform: scale(0.92);
      }
    }

    @media (max-width: 860px) {
      .chat-workspace {
        grid-template-columns: 1fr;
        height: calc(100vh - 140px);
        min-height: 520px;
        border-radius: var(--radius-md);
      }

      .mobile-back-btn {
        display: inline-flex;
        min-height: 44px;
      }

      /* On mobile: full-screen conversation list when no chat is open */
      .chat-workspace:not(.session-active) .no-selection-panel {
        display: none !important;
      }
      .chat-workspace:not(.session-active) .sessions-sidebar {
        width: 100%;
        height: 100%;
        display: flex;
        flex-direction: column;
      }

      /* On mobile: true full-screen WhatsApp/iMessage chat window when conversation is open */
      .chat-workspace.session-active {
        position: fixed;
        inset: 0;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        width: 100vw;
        height: 100dvh;
        height: 100vh;
        border-radius: 0;
        border: none;
        z-index: 1000;
        background: var(--bg-main);
        display: flex;
        flex-direction: column;
        margin: 0;
        box-shadow: none;
      }

      .chat-workspace.session-active .sessions-sidebar {
        display: none !important;
      }

      .chat-workspace.session-active .chat-main {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100%;
        min-height: 0;
        overflow: hidden;
      }

      .chat-workspace.session-active .chat-header {
        padding: calc(0.65rem + var(--safe-top)) 0.85rem 0.65rem 0.85rem;
      }

      .chat-workspace.session-active .chat-messages {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        -webkit-overflow-scrolling: touch;
      }

      .chat-workspace.session-active .chat-input-bar {
        padding: 0.65rem 0.85rem calc(0.75rem + var(--safe-bottom));
      }

      .page-container {
        padding: 1rem 0.85rem calc(76px + var(--safe-bottom));
      }

      .page-title {
        font-size: 1.35rem;
      }

      .header-actions {
        width: 100%;
        flex-wrap: wrap;
        gap: 0.5rem;
      }

      .header-actions .btn {
        flex: 1 1 auto;
        min-height: 44px;
      }

      .chat-header {
        padding: 0.65rem 0.85rem;
        flex-wrap: wrap;
        gap: 0.5rem;
      }

      .chat-customer-info {
        flex: 1;
        min-width: 0;
      }

      .chat-actions {
        width: 100%;
        justify-content: flex-start;
        gap: 0.4rem;
        overflow-x: auto;
        padding-top: 0.25rem;
        scrollbar-width: none;
      }

      .chat-actions .btn, .chat-actions .assign-select {
        min-height: 44px;
        font-size: 0.775rem;
        padding: 0.45rem 0.75rem;
        flex-shrink: 0;
      }

      .chat-input-bar {
        padding: 0.65rem 0.75rem calc(0.65rem + var(--safe-bottom));
      }

      .message-input {
        min-height: 44px;
        font-size: 16px; /* 16px prevents iOS Safari auto-zoom */
      }

      .send-btn {
        width: 44px;
        height: 44px;
        min-width: 44px;
        min-height: 44px;
        flex-shrink: 0;
      }

      .message-bubble {
        max-width: 88%;
        font-size: 0.85rem;
        padding: 0.65rem 0.85rem;
      }

      .quick-replies-bar {
        padding: 0.45rem 0.75rem;
      }

      .quick-reply-chip {
        min-height: 40px;
        display: inline-flex;
        align-items: center;
      }

      .diag-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class AtendimentosComponent implements OnInit, OnDestroy {
  private readonly adminService = inject(AppointmentService);
  private readonly chatService = inject(ChatService);
  readonly authService = inject(AuthService);
  private readonly professionalService = inject(ProfessionalService);

  @ViewChild('scrollContainer') scrollContainer?: ElementRef<HTMLDivElement>;

  readonly sessions = signal<ConversationSession[]>([]);
  readonly professionals = signal<Professional[]>([]);
  readonly selectedSession = signal<ConversationSession | null>(null);
  readonly messages = signal<ChatMessage[]>([]);

  readonly loadingSessions = signal<boolean>(false);
  readonly loadingMessages = signal<boolean>(false);
  readonly sending = signal<boolean>(false);
  readonly botOnline = signal<boolean>(true);
  readonly botHealthDetails = signal<BotStatus | null>(null);
  readonly reconnectingBot = signal<boolean>(false);
  readonly reportingIssue = signal<boolean>(false);

  readonly stateFilter = signal<'ALL' | 'TRANSBORDO_HUMANO' | 'BOT'>('ALL');
  readonly artistFilter = signal<string>('ALL');
  readonly searchQuerySignal = signal<string>('');
  searchQuery = '';
  replyText = '';

  readonly aiCardExpanded = signal<boolean>(true);
  readonly unreadCounts = signal<Record<string, number>>({});

  // Modals state
  readonly showImageModal = signal<boolean>(false);
  readonly zoomImageUrl = signal<string | null>(null);
  readonly zoomImageTitle = signal<string>('Imagem de Referência');
  readonly showResetModal = signal<boolean>(false);
  readonly showDiagnosticsModal = signal<boolean>(false);

  issueCategory = 'WHATSAPP_DELAY';
  issueDescription = '';

  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');

  readonly quickReplies = [
    {
      label: '👋 Saudação Inicial',
      text: 'Olá! Tudo bem? Vi sua referência aqui no sistema do Vício Tattoo e já vou te ajudar com os detalhes!'
    },
    {
      label: '📅 Confirmar Horário',
      text: 'Temos disponibilidade na agenda nessa semana! Qual período fica melhor para você (manhã ou tarde)?'
    },
    {
      label: '💳 Instrução Sinal PIX',
      text: 'Assim que realizar o pagamento do sinal via PIX, sua vaga fica 100% garantida na nossa agenda!'
    },
    {
      label: '✨ Cuidados Pré-Tattoo',
      text: 'Lembre-se de vir bem alimentado(a), hidratado(a) e evitar consumo de álcool nas 24h anteriores à sessão!'
    }
  ];

  private sseSubscription: Subscription | null = null;
  private globalSseSubscription: Subscription | null = null;
  private pollInterval: any = null;

  readonly humanCount = computed(() =>
    this.sessions().filter(s => s.currentState === 'TRANSBORDO_HUMANO').length
  );

  readonly botCount = computed(() =>
    this.sessions().filter(s => s.currentState !== 'TRANSBORDO_HUMANO').length
  );

  readonly filteredSessions = computed(() => {
    let list = this.sessions();
    const sf = this.stateFilter();
    if (sf === 'TRANSBORDO_HUMANO') {
      list = list.filter(s => s.currentState === 'TRANSBORDO_HUMANO');
    } else if (sf === 'BOT') {
      list = list.filter(s => s.currentState !== 'TRANSBORDO_HUMANO');
    }

    const af = this.artistFilter();
    if (af && af !== 'ALL') {
      if (af === 'UNASSIGNED') {
        list = list.filter(s => !s.assignedUserId && !s.assignedArtistName);
      } else {
        const selectedProf = this.professionals().find(p => p.id === af);
        list = list.filter(s =>
          s.assignedUserId === af ||
          (selectedProf && s.assignedArtistName && s.assignedArtistName.toLowerCase().includes(selectedProf.name.toLowerCase()))
        );
      }
    }

    const q = (this.searchQuerySignal() || this.searchQuery).trim().toLowerCase();
    if (q) {
      list = list.filter(s =>
        (s.customerName && s.customerName.toLowerCase().includes(q)) ||
        (s.customerPhone && s.customerPhone.includes(q)) ||
        (s.assignedArtistName && s.assignedArtistName.toLowerCase().includes(q))
      );
    }
    return list;
  });

  readonly parsedAiAnalysis = computed<AiAnalysisParsed | null>(() => {
    const active = this.selectedSession();
    if (!active || !active.contextData) return null;
    try {
      const parsed = JSON.parse(active.contextData) as AiAnalysisParsed;
      if (!parsed || typeof parsed !== 'object') return null;
      const hasSummaryField = !!(
        parsed.style ||
        parsed.sizeCategory ||
        parsed.estimatedSizeCm ||
        parsed.isFechamento ||
        parsed.priceDisplay ||
        (parsed.minPrice !== undefined && parsed.maxPrice !== undefined)
      );
      return hasSummaryField ? parsed : null;
    } catch {
      return null;
    }
  });

  readonly sessionReferenceImage = computed<string | null>(() => {
    const art = this.parsedAiAnalysis();
    if (art?.referenceImageUrl && this.isRenderableImage(art.referenceImageUrl)) {
      return art.referenceImageUrl;
    }
    const msgs = this.messages();
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (this.isRenderableImage(msgs[i].mediaUrl)) {
        return msgs[i].mediaUrl!;
      }
    }
    return null;
  });

  ngOnInit(): void {
    this.loadSessions();
    this.loadProfessionals();
    this.checkBotHealth();
    this.startGlobalStream();

    // Polling leve de segurança a cada 10s para sincronizar conversas na sidebar
    this.pollInterval = setInterval(() => {
      this.refreshSessionsSilently();
    }, 10000);
  }

  ngOnDestroy(): void {
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
    }
    if (this.globalSseSubscription) {
      this.globalSseSubscription.unsubscribe();
    }
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
    }
  }

  onSearchChange(val: string): void {
    this.searchQuery = val;
    this.searchQuerySignal.set(val);
  }

  setArtistFilter(artistId: string): void {
    this.artistFilter.set(artistId || 'ALL');
  }

  loadSessions(): void {
    this.loadingSessions.set(true);
    this.adminService.getConversations().subscribe({
      next: (list) => {
        this.sessions.set(list || []);
        this.loadingSessions.set(false);
        const current = this.selectedSession();
        if (current) {
          const fresh = (list || []).find(s => s.id === current.id);
          if (fresh) this.selectedSession.set(fresh);
        }
      },
      error: () => {
        this.loadingSessions.set(false);
        this.showToast('Erro ao carregar atendimentos.', 'error');
      }
    });
  }

  private refreshSessionsSilently(): void {
    this.adminService.getConversations().subscribe({
      next: (list) => {
        this.sessions.set(list || []);
        const current = this.selectedSession();
        if (current) {
          const fresh = (list || []).find(s => s.id === current.id);
          if (fresh) this.selectedSession.set(fresh);
        }
      },
      error: () => {}
    });
  }

  loadProfessionals(): void {
    if (!this.authService.isOwner()) return;
    this.professionalService.listAll(true).subscribe({
      next: (artists: Professional[]) => this.professionals.set(artists || []),
      error: () => {}
    });
  }

  checkBotHealth(notifyToast: boolean = false): void {
    this.chatService.getBotStatus().subscribe({
      next: (res) => {
        this.botOnline.set(res.status === 'CONNECTED');
        this.botHealthDetails.set(res);
        if (notifyToast) {
          this.showToast('Diagnóstico concluído: todos os serviços operacionais!');
        }
      },
      error: () => {
        this.botOnline.set(false);
        if (notifyToast) {
          this.showToast('Falha ao verificar status do bot.', 'error');
        }
      }
    });
  }

  openDiagnosticsModal(): void {
    this.checkBotHealth(false);
    this.showDiagnosticsModal.set(true);
  }

  reconnectWhatsAppInstance(): void {
    this.reconnectingBot.set(true);
    this.chatService.reconnectBot().subscribe({
      next: (res) => {
        this.reconnectingBot.set(false);
        this.botOnline.set(true);
        this.checkBotHealth(false);
        this.showToast(res.message || 'Instância do WhatsApp reiniciada com sucesso!');
      },
      error: () => {
        this.reconnectingBot.set(false);
        this.showToast('Erro ao tentar reiniciar instância do WhatsApp.', 'error');
      }
    });
  }

  submitIssueReport(): void {
    const desc = this.issueDescription.trim();
    if (!desc) return;

    this.reportingIssue.set(true);
    this.chatService.reportIssue({
      description: desc,
      category: this.issueCategory,
      sessionId: this.selectedSession()?.id
    }).subscribe({
      next: (res) => {
        this.reportingIssue.set(false);
        this.issueDescription = '';
        this.showDiagnosticsModal.set(false);
        this.showToast(res.message || `Problema reportado (${res.protocol}).`);
      },
      error: () => {
        this.reportingIssue.set(false);
        this.showToast('Erro ao registrar chamado de diagnóstico.', 'error');
      }
    });
  }

  setFilter(f: 'ALL' | 'TRANSBORDO_HUMANO' | 'BOT'): void {
    this.stateFilter.set(f);
  }

  selectSession(s: ConversationSession): void {
    this.selectedSession.set(s);
    this.aiCardExpanded.set(true);
    this.clearUnread(s.id);
    this.loadMessages(s.id);
  }

  clearSelectedSession(): void {
    this.selectedSession.set(null);
  }

  loadMessages(sessionId: string): void {
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
      this.sseSubscription = null;
    }

    this.loadingMessages.set(true);
    this.chatService.getMessages(sessionId).subscribe({
      next: (msgs) => {
        this.messages.set(msgs || []);
        this.loadingMessages.set(false);
        this.scrollToBottom();
        this.startSseStream(sessionId);
      },
      error: () => {
        this.loadingMessages.set(false);
        this.messages.set([]);
        this.startSseStream(sessionId);
      }
    });
  }

  private startSseStream(sessionId: string): void {
    this.sseSubscription = this.chatService.streamMessages(sessionId).subscribe({
      next: (incomingMsg) => {
        if (!incomingMsg || !incomingMsg.id) return;
        const currentList = this.messages();
        const existingIdx = currentList.findIndex(m => m.id === incomingMsg.id);
        if (existingIdx >= 0) {
          const updated = [...currentList];
          updated[existingIdx] = incomingMsg;
          this.messages.set(updated);
        } else {
          this.messages.set([...currentList, incomingMsg]);
          this.scrollToBottom();
        }
      },
      error: (e) => console.warn('SSE stream error:', e)
    });
  }

  private startGlobalStream(): void {
    this.globalSseSubscription = this.chatService.streamAllMessages().subscribe({
      next: (msg) => {
        if (!msg || !msg.sessionId) return;
        const active = this.selectedSession();
        if (!active || active.id !== msg.sessionId) {
          if (!msg.isFromMe) {
            const counts = { ...this.unreadCounts() };
            counts[msg.sessionId] = (counts[msg.sessionId] || 0) + 1;
            this.unreadCounts.set(counts);
          }
        }
        this.refreshSessionsSilently();
      },
      error: () => {}
    });
  }

  getUnreadCount(sessionId: string): number {
    return this.unreadCounts()[sessionId] || 0;
  }

  private clearUnread(sessionId: string): void {
    const counts = { ...this.unreadCounts() };
    if (counts[sessionId]) {
      delete counts[sessionId];
      this.unreadCounts.set(counts);
    }
  }

  sendMessage(): void {
    const active = this.selectedSession();
    const text = this.replyText.trim();
    if (!active || !text) return;

    this.sending.set(true);
    this.chatService.sendMessage(active.id, text).subscribe({
      next: (created) => {
        this.sending.set(false);
        this.replyText = '';
        const currentList = this.messages();
        if (!currentList.some(m => m.id === created.id)) {
          this.messages.set([...currentList, created]);
        }
        this.scrollToBottom();
        if (active.currentState !== 'TRANSBORDO_HUMANO') {
          active.currentState = 'TRANSBORDO_HUMANO';
          this.selectedSession.set({ ...active });
          this.refreshSessionsSilently();
        }
      },
      error: (err) => {
        this.sending.set(false);
        this.showToast(err.error?.message || 'Erro ao enviar mensagem via WhatsApp.', 'error');
      }
    });
  }

  applyQuickReply(text: string): void {
    this.replyText = text;
  }

  toggleAiCard(): void {
    this.aiCardExpanded.set(!this.aiCardExpanded());
  }

  formatArtSize(art: AiAnalysisParsed): string {
    if (art.estimatedSizeCm) {
      if (art.sizeCategory) {
        const normCm = art.estimatedSizeCm.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
        const normCat = art.sizeCategory.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
        if (!normCm.includes(normCat)) {
          return `${art.estimatedSizeCm} (${art.sizeCategory})`;
        }
      }
      return art.estimatedSizeCm;
    }
    return art.sizeCategory || 'Proporcional';
  }

  isRenderableImage(mediaUrl?: string): boolean {
    if (!mediaUrl) return false;
    return mediaUrl.startsWith('data:image/') || mediaUrl.startsWith('http://') || mediaUrl.startsWith('https://');
  }

  openImageZoom(url: string, title: string = 'Imagem de Referência'): void {
    this.zoomImageUrl.set(url);
    this.zoomImageTitle.set(title);
    this.showImageModal.set(true);
  }

  canQuickAssignRecommended(recommended?: string[]): boolean {
    if (!recommended || recommended.length === 0) return false;
    const active = this.selectedSession();
    if (!active) return false;
    const match = this.findMatchingProfessional(recommended[0]);
    return !!match && active.assignedUserId !== match.id;
  }

  assignRecommendedArtist(recommended?: string[]): void {
    if (!recommended || recommended.length === 0) return;
    const match = this.findMatchingProfessional(recommended[0]);
    if (match && match.id) {
      this.onAssignArtist(match.id);
    }
  }

  private findMatchingProfessional(rawName: string): Professional | undefined {
    if (!rawName) return undefined;
    const firstToken = rawName.split(' ')[0].toLowerCase();
    return this.professionals().find(p => p.name.toLowerCase().includes(firstToken));
  }

  onAssignArtist(artistId: string | null): void {
    const active = this.selectedSession();
    if (!active) return;

    this.chatService.assignArtist(active.id, artistId).subscribe({
      next: () => {
        const found = this.professionals().find(p => p.id === artistId);
        active.assignedUserId = artistId || undefined;
        active.assignedArtistName = found ? found.name : undefined;
        this.selectedSession.set({ ...active });
        this.loadSessions();
        this.showToast(`Conversa atribuída a ${found ? found.name : 'nenhum profissional'}.`);
      },
      error: () => this.showToast('Erro ao atribuir profissional.', 'error')
    });
  }

  promptResetBot(active: ConversationSession): void {
    this.selectedSession.set(active);
    this.showResetModal.set(true);
  }

  confirmResetBot(): void {
    const active = this.selectedSession();
    if (!active) return;
    this.showResetModal.set(false);
    this.executeResetBot(active);
  }

  resetBot(active: ConversationSession): void {
    this.executeResetBot(active);
  }

  private executeResetBot(active: ConversationSession): void {
    this.chatService.resetBotSession(active.id).subscribe({
      next: () => {
        active.currentState = 'MENU';
        active.contextData = undefined;
        this.selectedSession.set({ ...active });
        this.loadSessions();
        this.loadMessages(active.id);
        this.showToast('Sessão reiniciada com sucesso! Cliente retornou ao Menu do Bot.');
      },
      error: () => this.showToast('Erro ao reiniciar bot.', 'error')
    });
  }

  changeState(session: ConversationSession, state: ConversationState): void {
    this.adminService.updateConversationState(session.id, state, true).subscribe({
      next: (updated) => {
        this.selectedSession.set(updated);
        this.loadSessions();
        this.loadMessages(session.id);
        this.showToast(
          state === 'MENU'
            ? 'Cliente devolvido para o menu automático do Bot!'
            : 'Atendimento humano assumido com sucesso!'
        );
      },
      error: () => {
        this.showToast('Erro ao atualizar estado da conversa.', 'error');
      }
    });
  }

  formatState(state: ConversationState): string {
    switch (state) {
      case 'TRANSBORDO_HUMANO': return 'Em Atendimento Humano';
      case 'ORCAMENTO_AGUARDANDO_FOTO': return 'Orçamento IA';
      case 'AGENDAMENTO_SELECAO_DATA': return 'Agendando Data';
      case 'MENU': return 'No Menu Bot';
      default: return state;
    }
  }

  formatStateIndicator(state: ConversationState): string {
    switch (state) {
      case 'TRANSBORDO_HUMANO': return '🟢 Atendimento Humano';
      case 'ORCAMENTO_AGUARDANDO_FOTO': return '🔵 Bot: Orçamento IA';
      case 'AGENDAMENTO_SELECAO_DATA': return '🔵 Bot: Agendando Data';
      case 'MENU': return '🔵 Bot: Menu Inicial';
      default: return state;
    }
  }

  getStateClass(state: ConversationState): string {
    switch (state) {
      case 'TRANSBORDO_HUMANO': return 'state-human';
      case 'ORCAMENTO_AGUARDANDO_FOTO': return 'state-orcamento';
      case 'AGENDAMENTO_SELECAO_DATA': return 'state-agendamento';
      default: return 'state-menu';
    }
  }

  formatTime(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  formatClock(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  formatMessageText(text: string): string {
    if (!text) return '';
    const escaped = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
    return escaped
      .replace(/\*(.*?)\*/g, '<strong>$1</strong>')
      .replace(/_(.*?)_/g, '<em>$1</em>');
  }

  scrollToBottom(): void {
    setTimeout(() => {
      const el = this.scrollContainer?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    }, 30);
  }

  private showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => this.feedbackMessage.set(null), 5000);
  }
}
