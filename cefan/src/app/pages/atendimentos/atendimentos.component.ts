import { Component, OnInit, OnDestroy, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AppointmentService } from '../../core/services/appointment.service';
import { ChatService } from '../../core/services/chat.service';
import { AuthService } from '../../core/services/auth.service';
import { ProfessionalService } from '../../core/services/professional.service';
import { ConversationSession, ConversationState, ChatMessage } from '../../core/models/appointment.model';
import { Professional } from '../../core/models/professional.model';

interface AiAnalysisParsed {
  style?: string;
  sizeCategory?: string;
  estimatedSizeCm?: string;
  estimatedHours?: number;
  complexityLevel?: string;
  suggestedBodyLocation?: string;
  technicalNotes?: string;
  recommendedArtists?: string[];
  basePrice?: number;
  minPrice?: number;
  maxPrice?: number;
}

@Component({
  selector: 'app-atendimentos',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
          <div class="bot-badge" [class.online]="botOnline()">
            <span class="status-dot"></span>
            <span>{{ botOnline() ? 'Bot Conectado' : 'Bot Offline' }}</span>
          </div>
          <button class="btn btn-secondary" (click)="loadSessions()">
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
      <div class="chat-workspace">
        <!-- Left Sidebar: Session List -->
        <div class="sessions-sidebar">
          <div class="sidebar-search">
            <input
              type="text"
              [(ngModel)]="searchQuery"
              placeholder="Buscar por cliente, telefone ou tatuador..."
              class="search-input"
            />
          </div>

          <div class="sidebar-filters">
            <button
              class="filter-tab"
              [class.active]="stateFilter() === 'TRANSBORDO_HUMANO'"
              (click)="setFilter('TRANSBORDO_HUMANO')">
              Aguardando ({{ humanCount() }})
            </button>
            <button
              class="filter-tab"
              [class.active]="stateFilter() === 'ALL'"
              (click)="setFilter('ALL')">
              Todas ({{ sessions().length }})
            </button>
          </div>

          <div class="session-list">
            <div *ngIf="loadingSessions()" class="loading-state">
              Carregando conversas...
            </div>

            <div *ngIf="!loadingSessions() && filteredSessions().length === 0" class="empty-state">
              Nenhuma conversa encontrada.
            </div>

            <div
              *ngFor="let s of filteredSessions()"
              class="session-item"
              [class.selected]="selectedSession()?.id === s.id"
              (click)="selectSession(s)">
              <div class="session-avatar">
                {{ (s.customerName || 'C').charAt(0).toUpperCase() }}
              </div>
              <div class="session-info">
                <div class="session-top">
                  <span class="customer-name">{{ s.customerName || 'Cliente' }}</span>
                  <span class="session-time">{{ formatTime(s.lastInteractionAt) }}</span>
                </div>
                <div class="session-middle">
                  <span class="customer-phone">{{ s.customerPhone }}</span>
                  <span class="assigned-pill" *ngIf="s.assignedArtistName">
                    {{ s.assignedArtistName }}
                  </span>
                </div>
                <div class="session-bottom">
                  <span class="state-pill" [ngClass]="getStateClass(s.currentState)">
                    {{ formatState(s.currentState) }}
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
            <div class="chat-customer-info">
              <div class="chat-avatar">
                {{ (active.customerName || 'C').charAt(0).toUpperCase() }}
              </div>
              <div>
                <div class="chat-title-row">
                  <span class="customer-fullname">{{ active.customerName }}</span>
                  <span class="state-pill" [ngClass]="getStateClass(active.currentState)">
                    {{ formatState(active.currentState) }}
                  </span>
                </div>
                <div class="customer-subinfo">
                  <span>{{ active.customerPhone }}</span>
                  <span *ngIf="active.assignedArtistName" class="assigned-tag">
                    • Atribuído a: <strong>{{ active.assignedArtistName }}</strong>
                  </span>
                </div>
              </div>
            </div>

            <div class="chat-actions">
              <!-- Atribuição de Tatuador (Dono ou Tatuador) -->
              <div class="assign-select-wrapper" *ngIf="authService.isOwner()">
                <select
                  class="assign-select"
                  [ngModel]="active.assignedUserId"
                  (ngModelChange)="onAssignArtist($event)">
                  <option [ngValue]="null">Atribuir Tatuador...</option>
                  <option *ngFor="let artist of professionals()" [value]="artist.id">
                    {{ artist.name }} ({{ artist.specialties || 'Tatuador' }})
                  </option>
                </select>
              </div>

              <!-- Reset Bot Button -->
              <button
                class="btn btn-warning-outline"
                title="Reiniciar sessão do bot e enviar mensagem de boas-vindas"
                (click)="resetBot(active)">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M2.5 2v6h6M21.5 22v-6h-6"/>
                  <path d="M22 11.5A10 10 0 0 0 3.2 7.2M2 12.5a10 10 0 0 0 18.8 4.3"/>
                </svg>
                <span>Reset Bot</span>
              </button>

              <!-- Toggle Atendimento Humano -->
              <button
                class="btn btn-primary"
                *ngIf="active.currentState !== 'TRANSBORDO_HUMANO'"
                (click)="changeState(active, 'TRANSBORDO_HUMANO')">
                Assumir Conversa
              </button>
              <button
                class="btn btn-secondary"
                *ngIf="active.currentState === 'TRANSBORDO_HUMANO'"
                (click)="changeState(active, 'MENU')">
                Liberar p/ Bot
              </button>
            </div>
          </div>

          <!-- AI Art Reference Card (Se houver análise de orçamento pela IA) -->
          <div class="ai-art-card" *ngIf="parsedAiAnalysis() as art">
            <div class="ai-card-header">
              <div class="ai-tag">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                </svg>
                <span>Orçamento Estimado com IA</span>
              </div>
              <span class="ai-price" *ngIf="art.minPrice && art.maxPrice">
                R$ {{ art.minPrice }} a R$ {{ art.maxPrice }}
              </span>
            </div>

            <div class="ai-card-body">
              <div class="ai-metric" *ngIf="art.style">
                <span class="ai-label">Estilo:</span>
                <span class="ai-value">{{ art.style }}</span>
              </div>
              <div class="ai-metric" *ngIf="art.estimatedSizeCm">
                <span class="ai-label">Tamanho:</span>
                <span class="ai-value">{{ art.estimatedSizeCm }} ({{ art.sizeCategory || 'Proporcional' }})</span>
              </div>
              <div class="ai-metric" *ngIf="art.estimatedHours">
                <span class="ai-label">Tempo de Agulha:</span>
                <span class="ai-value">~{{ art.estimatedHours }}h</span>
              </div>
              <div class="ai-metric" *ngIf="art.suggestedBodyLocation">
                <span class="ai-label">Local Recomendado:</span>
                <span class="ai-value">{{ art.suggestedBodyLocation }}</span>
              </div>
              <div class="ai-metric" *ngIf="art.recommendedArtists && art.recommendedArtists.length > 0">
                <span class="ai-label">Especialista Indicado:</span>
                <span class="ai-value highlight-artist">{{ art.recommendedArtists.join(', ') }}</span>
              </div>
            </div>

            <div class="ai-notes" *ngIf="art.technicalNotes">
              <strong>Observações Técnicas:</strong> {{ art.technicalNotes }}
            </div>
          </div>

          <!-- Message Bubbles Stream -->
          <div class="chat-messages" #scrollContainer>
            <div *ngIf="loadingMessages()" class="messages-loading">
              Carregando histórico de mensagens...
            </div>

            <div *ngIf="!loadingMessages() && messages().length === 0" class="no-messages-notice">
              Nenhuma mensagem registrada nesta sessão ainda.
            </div>

            <div
              *ngFor="let msg of messages()"
              class="message-row"
              [ngClass]="msg.isFromMe ? 'row-outgoing' : 'row-incoming'">

              <!-- Customer Message Bubble (Incoming) -->
              <div *ngIf="!msg.isFromMe" class="message-bubble bubble-customer">
                <div class="sender-tag">{{ msg.senderName || 'Cliente' }}</div>
                <div class="message-body">{{ msg.content }}</div>
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
              <span>Pressione Enter para enviar diretamente ao WhatsApp do cliente.</span>
              <span class="helper-status" *ngIf="selectedSession()?.currentState === 'TRANSBORDO_HUMANO'">
                ● Transbordo Humano Ativo
              </span>
            </div>
          </div>
        </div>

        <!-- No Selection Placeholder -->
        <ng-template #noSessionSelected>
          <div class="no-selection-panel">
            <div class="placeholder-icon">💬</div>
            <h3>Nenhum Atendimento Selecionado</h3>
            <p>Selecione uma conversa na barra lateral para visualizar o histórico de mensagens e responder ao cliente.</p>
          </div>
        </ng-template>
      </div>
    </div>
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
      padding: 0.35rem 0.75rem;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-full);
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-secondary);

      &.online {
        color: var(--color-success);
        border-color: rgba(16, 185, 129, 0.3);
        background: var(--color-success-bg);
      }

      .status-dot {
        width: 7px;
        height: 7px;
        border-radius: 50%;
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
      grid-template-columns: 340px 1fr;
      gap: 1rem;
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

      .search-input {
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
      gap: 0.4rem;
      border-bottom: 1px solid var(--border-color);
    }

    .filter-tab {
      flex: 1;
      padding: 0.4rem;
      font-size: 0.75rem;
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

    .session-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.85rem 1rem;
      border-bottom: 1px solid var(--border-color);
      cursor: pointer;
      transition: background 0.15s;

      &:hover {
        background-color: var(--bg-card);
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
    }

    .session-info {
      flex: 1;
      min-width: 0;
    }

    .session-top {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      margin-bottom: 0.2rem;
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
    }

    .customer-phone {
      font-size: 0.75rem;
      color: var(--text-secondary);
    }

    .assigned-pill {
      font-size: 0.65rem;
      background: rgba(139, 92, 246, 0.15);
      color: #a78bfa;
      padding: 0.1rem 0.35rem;
      border-radius: var(--radius-sm);
      font-weight: 600;
      white-space: nowrap;
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
      text-transform: uppercase;
      letter-spacing: 0.03em;

      &.state-human {
        background-color: var(--color-danger-bg);
        color: var(--color-danger);
        border: 1px solid rgba(239, 68, 68, 0.3);
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
        background-color: var(--bg-surface);
        color: var(--text-secondary);
        border: 1px solid var(--border-color);
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
      color: var(--accent-primary);
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
      &:hover {
        background: rgba(245, 158, 11, 0.1);
      }
    }

    /* AI Art Preview Card */
    .ai-art-card {
      margin: 0.75rem 1.25rem 0.25rem 1.25rem;
      padding: 0.85rem 1rem;
      background: linear-gradient(145deg, rgba(30, 27, 75, 0.7), rgba(15, 23, 42, 0.9));
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: var(--radius-md);
      flex-shrink: 0;
    }

    .ai-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.5rem;
    }

    .ai-tag {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      color: #818cf8;
      font-size: 0.8rem;
      font-weight: 700;
    }

    .ai-price {
      font-size: 0.95rem;
      font-weight: 800;
      color: #34d399;
      background: rgba(16, 185, 129, 0.15);
      padding: 0.2rem 0.5rem;
      border-radius: var(--radius-sm);
    }

    .ai-card-body {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem 1.25rem;
      font-size: 0.775rem;
    }

    .ai-metric {
      display: flex;
      gap: 0.3rem;
      .ai-label { color: var(--text-muted); }
      .ai-value { color: var(--text-primary); font-weight: 600; }
      .highlight-artist { color: #f59e0b; }
    }

    .ai-notes {
      margin-top: 0.4rem;
      font-size: 0.725rem;
      color: var(--text-secondary);
      line-height: 1.3;
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

    /* Input Bar */
    .chat-input-bar {
      padding: 0.85rem 1.25rem;
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
      padding: 0.75rem 1rem;
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
      width: 44px;
      height: 44px;
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
        color: var(--color-danger);
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
        max-width: 380px;
      }
    }

    @media (max-width: 860px) {
      .chat-workspace {
        grid-template-columns: 1fr;
      }
      .sessions-sidebar {
        display: none; // in a mobile drawer
      }
    }
  `]
})
export class AtendimentosComponent implements OnInit, OnDestroy {
  private readonly adminService = inject(AppointmentService);
  private readonly chatService = inject(ChatService);
  readonly authService = inject(AuthService);
  private readonly professionalService = inject(ProfessionalService);

  readonly sessions = signal<ConversationSession[]>([]);
  readonly professionals = signal<Professional[]>([]);
  readonly selectedSession = signal<ConversationSession | null>(null);
  readonly messages = signal<ChatMessage[]>([]);

  readonly loadingSessions = signal<boolean>(false);
  readonly loadingMessages = signal<boolean>(false);
  readonly sending = signal<boolean>(false);
  readonly botOnline = signal<boolean>(true);

  readonly stateFilter = signal<'ALL' | 'TRANSBORDO_HUMANO'>('ALL');
  searchQuery = '';
  replyText = '';

  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');

  private sseSubscription: Subscription | null = null;
  private pollInterval: any = null;

  readonly humanCount = computed(() =>
    this.sessions().filter(s => s.currentState === 'TRANSBORDO_HUMANO').length
  );

  readonly filteredSessions = computed(() => {
    let list = this.sessions();
    if (this.stateFilter() === 'TRANSBORDO_HUMANO') {
      list = list.filter(s => s.currentState === 'TRANSBORDO_HUMANO');
    }
    const q = this.searchQuery.trim().toLowerCase();
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
      return JSON.parse(active.contextData);
    } catch {
      return null;
    }
  });

  ngOnInit(): void {
    this.loadSessions();
    this.loadProfessionals();
    this.checkBotHealth();

    // Polling leve a cada 10s para novas conversas na sidebar
    this.pollInterval = setInterval(() => {
      this.refreshSessionsSilently();
    }, 10000);
  }

  ngOnDestroy(): void {
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
    }
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
    }
  }

  loadSessions(): void {
    this.loadingSessions.set(true);
    this.adminService.getConversations().subscribe({
      next: (list) => {
        this.sessions.set(list || []);
        this.loadingSessions.set(false);
        // Mantém sessão selecionada sincronizada
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

  checkBotHealth(): void {
    this.chatService.getBotStatus().subscribe({
      next: (res) => this.botOnline.set(res.status === 'CONNECTED'),
      error: () => this.botOnline.set(false)
    });
  }

  setFilter(f: 'ALL' | 'TRANSBORDO_HUMANO'): void {
    this.stateFilter.set(f);
  }

  selectSession(s: ConversationSession): void {
    this.selectedSession.set(s);
    this.loadMessages(s.id);
  }

  loadMessages(sessionId: string): void {
    // Cancela stream anterior
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
      this.sseSubscription = null;
    }

    this.loadingMessages.set(true);
    this.chatService.getMessages(sessionId).subscribe({
      next: (msgs) => {
        this.messages.set(msgs || []);
        this.loadingMessages.set(false);
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
        // Deduplica se já foi inserida localmente
        if (!currentList.some(m => m.id === incomingMsg.id)) {
          this.messages.set([...currentList, incomingMsg]);
        }
      },
      error: (e) => console.warn('SSE stream error:', e)
    });
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
        // Atualiza estado local para TRANSBORDO_HUMANO
        if (active.currentState !== 'TRANSBORDO_HUMANO') {
          active.currentState = 'TRANSBORDO_HUMANO';
          this.selectedSession.set({ ...active });
        }
      },
      error: (err) => {
        this.sending.set(false);
        this.showToast(err.error?.message || 'Erro ao enviar mensagem via WhatsApp.', 'error');
      }
    });
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

  resetBot(active: ConversationSession): void {
    if (!confirm(`Deseja reiniciar a sessão do WhatsApp de ${active.customerName}? O cliente será retornado ao Menu inicial do bot.`)) {
      return;
    }
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
      case 'TRANSBORDO_HUMANO': return 'Aguardando Humano';
      case 'ORCAMENTO_AGUARDANDO_FOTO': return 'Orçamento IA';
      case 'AGENDAMENTO_SELECAO_DATA': return 'Agendando Data';
      case 'MENU': return 'No Menu Bot';
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
    // Formata markdown básico do WhatsApp (*bold* -> <strong>)
    return text
      .replace(/\*(.*?)\*/g, '<strong>$1</strong>')
      .replace(/_(.*?)_/g, '<em>$1</em>');
  }

  private showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => this.feedbackMessage.set(null), 5000);
  }
}
