import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AppointmentService } from '../../core/services/appointment.service';
import { ConversationSession, ConversationState } from '../../core/models/appointment.model';

@Component({
  selector: 'app-atendimentos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Painel de Atendimentos (Live Chat)</h1>
          <p class="page-subtitle">Clientes em Transbordo Humano e sessões ativas no WhatsApp via Evolution API</p>
        </div>
        <div class="header-actions">
          <button class="btn btn-secondary" (click)="loadSessions()">
            Atualizar Conversas
          </button>
        </div>
      </div>

      <div *ngIf="feedbackMessage()" class="alert-banner" [ngClass]="feedbackType()">
        <span>{{ feedbackMessage() }}</span>
        <button class="alert-close" (click)="feedbackMessage.set(null)">×</button>
      </div>

      <div class="chat-workspace">
        <!-- Left Sidebar: Session List -->
        <div class="sessions-sidebar">
          <div class="sidebar-filters">
            <button
              class="filter-tab"
              [class.active]="stateFilter() === 'TRANSBORDO_HUMANO'"
              (click)="setFilter('TRANSBORDO_HUMANO')">
              Aguardando Humano ({{ humanCount() }})
            </button>
            <button
              class="filter-tab"
              [class.active]="stateFilter() === 'ALL'"
              (click)="setFilter('ALL')">
              Todas ({{ sessions().length }})
            </button>
          </div>

          <div class="session-list">
            <div *ngIf="filteredSessions().length === 0" class="empty-sessions">
              Nenhuma conversa encontrada neste filtro.
            </div>

            <div
              *ngFor="let s of filteredSessions()"
              class="session-card"
              [class.selected]="selectedSession()?.id === s.id"
              (click)="selectSession(s)">
              <div class="session-top">
                <span class="customer-name">{{ s.customerName }}</span>
                <span class="state-pill" [ngClass]="s.currentState">{{ formatState(s.currentState) }}</span>
              </div>
              <div class="session-meta">
                <span>📱 {{ s.customerPhone }}</span>
                <span>🕒 {{ formatTime(s.lastInteractionAt) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Panel: Active Conversation -->
        <div class="conversation-panel">
          <div *ngIf="!selectedSession()" class="empty-conversation">
            <h3>Selecione um atendimento ao lado</h3>
            <p>Visualize os dados do orçamento gerado pela IA, responda diretamente pelo WhatsApp ou devolva o cliente para o menu automático do bot.</p>
          </div>

          <ng-container *ngIf="selectedSession() as active">
            <div class="conv-header">
              <div>
                <h3>{{ active.customerName }}</h3>
                <span class="conv-phone">WhatsApp: {{ active.customerPhone }} · Estado: <strong>{{ formatState(active.currentState) }}</strong></span>
              </div>
              <div class="conv-actions">
                <button
                  *ngIf="active.currentState !== 'TRANSBORDO_HUMANO'"
                  class="btn btn-secondary"
                  (click)="changeState(active, 'TRANSBORDO_HUMANO')">
                  Assumir Atendimento Humano
                </button>
                <button
                  *ngIf="active.currentState === 'TRANSBORDO_HUMANO'"
                  class="btn btn-primary"
                  (click)="changeState(active, 'MENU')">
                  Devolver para o Bot (Menu)
                </button>
              </div>
            </div>

            <div class="conv-body">
              <div class="context-box" *ngIf="active.contextData">
                <h4>🤖 Contexto / Orçamento IA Registrado na Sessão</h4>
                <pre>{{ formatContextData(active.contextData) }}</pre>
              </div>

              <div class="info-box">
                <p>
                  <strong>Status do Bot:</strong>
                  <span *ngIf="active.currentState === 'TRANSBORDO_HUMANO'">
                    🔇 Bot silenciado para este cliente (Modo Humano ativo). Suas respostas abaixo são enviadas diretamente no WhatsApp do cliente via Evolution API.
                  </span>
                  <span *ngIf="active.currentState !== 'TRANSBORDO_HUMANO'">
                    🤖 O cliente está sendo atendido automaticamente pelo Bot. Ao enviar uma mensagem abaixo, você assume o atendimento humano automaticamente.
                  </span>
                </p>
              </div>
            </div>

            <div class="conv-composer">
              <textarea
                rows="3"
                [(ngModel)]="replyText"
                placeholder="Digite sua mensagem para enviar ao WhatsApp do cliente..."
              ></textarea>
              <button
                class="btn btn-primary"
                [disabled]="sending() || !replyText.trim()"
                (click)="sendMessage()">
                {{ sending() ? 'Enviando...' : 'Enviar no WhatsApp' }}
              </button>
            </div>
          </ng-container>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      max-width: 1280px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    .page-title {
      font-size: 1.75rem;
      font-weight: 800;
      color: var(--text-primary);
    }
    .page-subtitle {
      font-size: 0.925rem;
      color: var(--text-secondary);
      margin-top: 0.25rem;
    }
    .alert-banner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.85rem 1.25rem;
      border-radius: var(--radius-md);
      margin-bottom: 1.25rem;
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
    .chat-workspace {
      display: grid;
      grid-template-columns: 360px 1fr;
      gap: 1.5rem;
      min-height: 540px;
    }
    .sessions-sidebar, .conversation-panel {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .sidebar-filters {
      display: flex;
      border-bottom: 1px solid var(--border-color);
      background-color: var(--bg-surface);
    }
    .filter-tab {
      flex: 1;
      padding: 0.85rem;
      background: none;
      border: none;
      color: var(--text-secondary);
      font-weight: 700;
      font-size: 0.8rem;
      cursor: pointer;
      border-bottom: 2px solid transparent;
      &.active {
        color: var(--accent-primary);
        border-bottom-color: var(--accent-primary);
      }
    }
    .session-list {
      flex: 1;
      overflow-y: auto;
    }
    .empty-sessions {
      padding: 2rem 1rem;
      text-align: center;
      color: var(--text-muted);
      font-size: 0.85rem;
    }
    .session-card {
      padding: 1rem;
      border-bottom: 1px solid var(--border-color);
      cursor: pointer;
      transition: background 0.2s;
      &:hover, &.selected {
        background-color: var(--bg-card-hover);
      }
    }
    .session-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.35rem;
    }
    .customer-name {
      font-weight: 700;
      color: var(--text-primary);
    }
    .state-pill {
      font-size: 0.68rem;
      font-weight: 700;
      padding: 0.15rem 0.5rem;
      border-radius: var(--radius-full);
      background-color: var(--bg-surface);
      color: var(--text-secondary);
      &.TRANSBORDO_HUMANO {
        background-color: var(--color-danger-bg);
        color: var(--color-danger);
      }
    }
    .session-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .empty-conversation {
      margin: auto;
      text-align: center;
      padding: 2rem;
      color: var(--text-secondary);
      max-width: 420px;
    }
    .conv-header {
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid var(--border-color);
      background-color: var(--bg-surface);
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
    }
    .conv-phone {
      font-size: 0.8rem;
      color: var(--text-secondary);
    }
    .conv-body {
      flex: 1;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .context-box {
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 1rem;
      h4 {
        font-size: 0.85rem;
        margin-bottom: 0.5rem;
        color: var(--accent-primary);
      }
      pre {
        font-size: 0.8rem;
        color: var(--text-secondary);
        white-space: pre-wrap;
      }
    }
    .info-box {
      background-color: rgba(99, 102, 241, 0.08);
      border: 1px solid rgba(99, 102, 241, 0.25);
      border-radius: var(--radius-md);
      padding: 1rem;
      font-size: 0.85rem;
      color: var(--text-secondary);
    }
    .conv-composer {
      padding: 1.25rem 1.5rem;
      border-top: 1px solid var(--border-color);
      display: flex;
      gap: 1rem;
      align-items: flex-end;
      textarea {
        flex: 1;
        background-color: var(--bg-surface);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        padding: 0.75rem;
        color: var(--text-primary);
        font-family: inherit;
      }
    }
  `]
})
export class AtendimentosComponent implements OnInit {
  private readonly adminService = inject(AppointmentService);

  readonly sessions = signal<ConversationSession[]>([]);
  readonly stateFilter = signal<'TRANSBORDO_HUMANO' | 'ALL'>('ALL');
  readonly selectedSession = signal<ConversationSession | null>(null);
  readonly sending = signal<boolean>(false);
  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');

  replyText = '';

  readonly humanCount = computed(() =>
    this.sessions().filter(s => s.currentState === 'TRANSBORDO_HUMANO').length
  );

  readonly filteredSessions = computed(() => {
    const list = this.sessions();
    if (this.stateFilter() === 'TRANSBORDO_HUMANO') {
      return list.filter(s => s.currentState === 'TRANSBORDO_HUMANO');
    }
    return list;
  });

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.adminService.getConversations().subscribe({
      next: (data) => {
        this.sessions.set(data || []);
        if (!this.selectedSession() && data && data.length > 0) {
          this.selectedSession.set(data[0]);
        }
      },
      error: () => {
        this.showToast('Erro ao carregar conversas do backend.', 'error');
      }
    });
  }

  setFilter(filter: 'TRANSBORDO_HUMANO' | 'ALL'): void {
    this.stateFilter.set(filter);
  }

  selectSession(session: ConversationSession): void {
    this.selectedSession.set(session);
  }

  sendMessage(): void {
    const active = this.selectedSession();
    if (!active || !this.replyText.trim()) return;

    this.sending.set(true);
    this.adminService.sendHumanMessage(active.id, this.replyText).subscribe({
      next: (updated) => {
        this.sending.set(false);
        this.replyText = '';
        this.selectedSession.set(updated);
        this.loadSessions();
        this.showToast('Mensagem enviada com sucesso para o WhatsApp do cliente!');
      },
      error: (err) => {
        this.sending.set(false);
        this.showToast(err.error?.message || 'Erro ao enviar mensagem via WhatsApp.', 'error');
      }
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
      case 'TRANSBORDO_HUMANO': return 'Atendimento Humano';
      case 'ORCAMENTO_AGUARDANDO_FOTO': return 'Orçamento IA';
      case 'AGENDAMENTO_SELECAO_DATA': return 'Escolhendo Horário';
      case 'MENU': return 'No Menu do Bot';
      default: return state;
    }
  }

  formatTime(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatContextData(raw: string): string {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }

  private showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => this.feedbackMessage.set(null), 5000);
  }
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-wrapper">
      <div class="login-card">
        <div class="login-brand">
          <div class="logo-icon">⚡</div>
          <h2>CEFAN · Vício Tattoo</h2>
          <p>Acesso administrativo do estúdio</p>
        </div>

        <div *ngIf="errorMsg()" class="login-error">{{ errorMsg() }}</div>

        <div class="input-group">
          <label>E-mail do Administrador</label>
          <input type="email" [(ngModel)]="email" placeholder="admin@viciotattoo.com" />
        </div>

        <div class="input-group">
          <label>Senha de Acesso</label>
          <input type="password" [(ngModel)]="password" placeholder="••••••••" (keyup.enter)="submitLogin()" />
        </div>

        <button class="btn btn-primary login-btn" [disabled]="loading()" (click)="submitLogin()">
          {{ loading() ? 'Autenticando...' : 'Entrar no Painel' }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .login-wrapper {
      min-height: calc(100vh - 70px);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .login-card {
      width: 100%;
      max-width: 420px;
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 2rem;
      box-shadow: var(--shadow-lg);
    }
    .login-brand {
      text-align: center;
      margin-bottom: 1.5rem;
      .logo-icon {
        font-size: 2rem;
        margin-bottom: 0.5rem;
      }
      h2 {
        font-size: 1.35rem;
        font-weight: 800;
      }
      p {
        font-size: 0.85rem;
        color: var(--text-secondary);
      }
    }
    .login-error {
      background-color: var(--color-danger-bg);
      color: var(--color-danger);
      border: 1px solid rgba(239, 68, 68, 0.3);
      padding: 0.75rem;
      border-radius: var(--radius-md);
      font-size: 0.825rem;
      margin-bottom: 1rem;
    }
    .login-btn {
      width: 100%;
      margin-top: 0.5rem;
    }
  `]
})
export class LoginComponent {
  private readonly adminService = inject(AppointmentService);
  private readonly router = inject(Router);

  email = 'admin@viciotattoo.com';
  password = 'cefan123';
  readonly loading = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);

  submitLogin(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.adminService.login(this.email, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/agenda']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMsg.set(err.error?.message || 'Credenciais inválidas.');
      }
    });
  }
}
