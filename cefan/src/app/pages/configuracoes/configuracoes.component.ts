import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenantConfigService } from '../../core/services/tenant-config.service';
import { AppointmentService } from '../../core/services/appointment.service';
import { TenantConfig } from '../../core/models/tenant-config.model';
import { NotificationPreferences } from '../../core/models/appointment.model';

@Component({
  selector: 'app-configuracoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Configurações & Parâmetros do Estúdio</h1>
          <p class="page-subtitle">
            Gestão financeira, percentuais de sinal e personalização completa das mensagens automáticas do WhatsApp
          </p>
        </div>
      </div>

      <!-- Feedback Banner -->
      <div *ngIf="feedbackMessage()" class="alert-banner" [ngClass]="feedbackType()">
        <span>{{ feedbackMessage() }}</span>
        <button class="alert-close" (click)="feedbackMessage.set(null)">×</button>
      </div>

      <!-- Navigation Tabs -->
      <div class="tabs-nav">
        <button
          class="tab-btn"
          [class.active]="activeTab() === 'FINANCIAL'"
          (click)="activeTab.set('FINANCIAL')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="1" x2="12" y2="23"/>
            <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
          </svg>
          <span>Tabela de Preços & Operação</span>
        </button>
        <button
          class="tab-btn"
          [class.active]="activeTab() === 'MESSAGES'"
          (click)="activeTab.set('MESSAGES')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <span>Mensagens Padrão do Bot (WhatsApp)</span>
        </button>
      </div>

      <!-- TAB 1: FINANCIAL & OPERATION -->
      <div class="config-grid" *ngIf="activeTab() === 'FINANCIAL'">
        <div class="card form-card">
          <div class="card-header">
            <h3>Tabela de Preços & Operação</h3>
            <p>Parâmetros financeiros consumidos diretamente pelo Gemini Vision ao gerar orçamentos automáticos.</p>
          </div>

          <div class="input-group">
            <label>Nome do Estúdio / Tenant</label>
            <input type="text" [(ngModel)]="config.studioName" placeholder="Ex: Vício Tattoo CWB" />
            <span class="hint">Nome exibido nas mensagens automáticas de WhatsApp enviadas pelo bot.</span>
          </div>

          <div class="form-row">
            <div class="input-group">
              <label>Valor da Hora do Tatuador (R$)</label>
              <input type="number" step="10.00" [(ngModel)]="config.defaultHourlyRate" />
              <span class="hint">Custo base por hora de agulha estimada pela IA.</span>
            </div>

            <div class="input-group">
              <label>Custo Fixo de Montagem / Setup (R$)</label>
              <input type="number" step="5.00" [(ngModel)]="config.fixedSetupCost" />
              <span class="hint">Taxa de biossegurança, materiais descartáveis e agulhas.</span>
            </div>
          </div>

          <div class="form-row">
            <div class="input-group">
              <label>Sinal de Reserva (% do Orçamento)</label>
              <input type="number" step="5.00" [(ngModel)]="config.signalPercentage" placeholder="30" />
              <span class="hint">Percentual cobrado via PIX para garantir o agendamento (Padrão: 30%).</span>
            </div>

            <div class="input-group">
              <label>Valor Mínimo do Sinal (R$)</label>
              <input type="number" step="10.00" [(ngModel)]="config.minDepositAmount" placeholder="50.00" />
              <span class="hint">Piso mínimo do valor do sinal (Padrão: R$ 50,00).</span>
            </div>
          </div>

          <div class="toggle-container">
            <div class="toggle-text">
              <span class="toggle-title">Absorver Taxas do Gateway (Mercado Pago)</span>
              <span class="toggle-desc">
                Se ativado, o estúdio arca com as tarifas de transação e o cliente paga exatamente o valor orçado.
              </span>
            </div>
            <label class="switch">
              <input type="checkbox" [(ngModel)]="config.absorbCreditCardFees" />
              <span class="slider round"></span>
            </label>
          </div>

          <div class="card-footer">
            <button class="btn btn-primary" [disabled]="saving()" (click)="saveConfig()">
              {{ saving() ? 'Salvando...' : 'Salvar Parâmetros Financeiros' }}
            </button>
          </div>
        </div>

        <!-- Simulator & Notification Preferences -->
        <div class="card preview-card">
          <div class="card-header">
            <h3>Simulador de Estimativas IA</h3>
            <p>Prévia em tempo real de como os orçamentos serão calculados para os clientes:</p>
          </div>

          <div class="simulation-list">
            <div class="simulation-item">
              <div class="sim-header">
                <span class="sim-badge">Tatuagem Pequena (1 hora)</span>
                <span class="sim-price">R$ {{ (config.fixedSetupCost + config.defaultHourlyRate * 1) | number:'1.2-2' }}</span>
              </div>
              <p class="sim-details">
                Setup (R$ {{ config.fixedSetupCost | number:'1.2-2' }}) + 1h (R$ {{ config.defaultHourlyRate | number:'1.2-2' }})
                <br>Sinal exigido ({{ config.signalPercentage || 30 }}%):
                <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 1) * ((config.signalPercentage || 30) / 100)) | number:'1.2-2' }}</strong>
              </p>
            </div>

            <div class="simulation-item">
              <div class="sim-header">
                <span class="sim-badge">Tatuagem Média (2,5 horas)</span>
                <span class="sim-price">R$ {{ (config.fixedSetupCost + config.defaultHourlyRate * 2.5) | number:'1.2-2' }}</span>
              </div>
              <p class="sim-details">
                Setup (R$ {{ config.fixedSetupCost | number:'1.2-2' }}) + 2.5h (R$ {{ (config.defaultHourlyRate * 2.5) | number:'1.2-2' }})
                <br>Sinal exigido ({{ config.signalPercentage || 30 }}%):
                <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 2.5) * ((config.signalPercentage || 30) / 100)) | number:'1.2-2' }}</strong>
              </p>
            </div>
          </div>

          <div class="card-header" style="margin-top: 1.5rem;">
            <h3>Preferências de Alertas em Tempo Real</h3>
            <p>Notificações sonoras e visuais na barra superior do sistema:</p>
          </div>

          <div class="toggle-container">
            <div class="toggle-text">
              <span class="toggle-title">Alerta de Atendimento Humano (Opção 3)</span>
              <span class="toggle-desc">Notificar quando o cliente solicitar falar com o tatuador.</span>
            </div>
            <label class="switch">
              <input type="checkbox" [(ngModel)]="notifPrefs.notifyHumanRequest" (change)="saveNotificationPrefs()" />
              <span class="slider round"></span>
            </label>
          </div>

          <div class="toggle-container">
            <div class="toggle-text">
              <span class="toggle-title">Confirmação de Sinal PIX Pago</span>
              <span class="toggle-desc">Notificar imediatamente quando um PIX for aprovado pelo Mercado Pago.</span>
            </div>
            <label class="switch">
              <input type="checkbox" [(ngModel)]="notifPrefs.notifyPaymentReceived" (change)="saveNotificationPrefs()" />
              <span class="slider round"></span>
            </label>
          </div>
        </div>
      </div>

      <!-- TAB 2: BOT MESSAGES PARAMETERIZATION -->
      <div class="card messages-card" *ngIf="activeTab() === 'MESSAGES'">
        <div class="card-header">
          <h3>Mensagens Padrão do WhatsApp (Totalmente Parametrizáveis)</h3>
          <p>
            Personalize os textos automáticos enviados pelo bot. As alterações entram em vigor imediatamente para todos os clientes sem necessidade de reiniciar o servidor.
          </p>
        </div>

        <div class="template-group">
          <label>1. Mensagem de Boas-Vindas (Menu Inicial)</label>
          <textarea rows="5" [(ngModel)]="config.welcomeMessage" class="template-textarea"></textarea>
          <span class="hint">Enviada na primeira interação ou quando o cliente envia "0" ou "menu".</span>
        </div>

        <div class="template-group">
          <label>2. Instruções para Envio de Foto de Orçamento (Opção 1)</label>
          <textarea rows="4" [(ngModel)]="config.orcamentoIntroMessage" class="template-textarea"></textarea>
          <span class="hint">Orientação sobre envio de foto, tamanho em cm e local do corpo.</span>
        </div>

        <div class="template-group">
          <label>3. Início de Agendamento (Opção 2)</label>
          <textarea rows="4" [(ngModel)]="config.agendamentoIntroMessage" class="template-textarea"></textarea>
          <span class="hint">Solicita a preferência de profissional e dias/períodos desejados.</span>
        </div>

        <div class="template-group">
          <label>4. Mensagem de Transbordo Humano (Opção 3)</label>
          <textarea rows="3" [(ngModel)]="config.humanTransferMessage" class="template-textarea"></textarea>
          <span class="hint">Informa que a equipe foi notificada e vai assumir o chat.</span>
        </div>

        <div class="template-group">
          <label>5. Confirmação de Sinal Pago via PIX</label>
          <textarea rows="3" [(ngModel)]="config.pixConfirmedMessage" class="template-textarea"></textarea>
          <span class="hint">Enviada automaticamente quando o webhook do Mercado Pago acusa pagamento aprovado.</span>
        </div>

        <div class="template-group">
          <label>6. Mensagem de Reinício de Conversa (Reset Bot)</label>
          <textarea rows="2" [(ngModel)]="config.botResetMessage" class="template-textarea"></textarea>
          <span class="hint">Enviada quando o operador clica no botão "Reset Bot".</span>
        </div>

        <div class="card-footer">
          <button class="btn btn-secondary" (click)="restoreDefaultTemplates()">
            Restaurar Textos Sugeridos
          </button>
          <button class="btn btn-primary" [disabled]="saving()" (click)="saveConfig()">
            {{ saving() ? 'Salvando...' : 'Salvar Todas as Mensagens no Banco' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
    }

    .page-header {
      margin-bottom: 1.5rem;
    }

    .page-title {
      font-size: 1.75rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: var(--text-primary);
    }

    .page-subtitle {
      font-size: 0.925rem;
      color: var(--text-secondary);
      margin-top: 0.25rem;
    }

    .tabs-nav {
      display: flex;
      gap: 0.75rem;
      margin-bottom: 1.5rem;
      border-bottom: 1px solid var(--border-color);
      padding-bottom: 0.5rem;
    }

    .tab-btn {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.65rem 1.25rem;
      background: none;
      border: 1px solid transparent;
      border-radius: var(--radius-md);
      color: var(--text-secondary);
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        color: var(--text-primary);
        background-color: var(--bg-card);
      }

      &.active {
        color: #fff;
        background-color: var(--accent-primary);
        box-shadow: 0 4px 12px var(--accent-glow);
      }
    }

    .config-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 1.5rem;

      @media (min-width: 992px) {
        grid-template-columns: 3fr 2fr;
      }
    }

    .card {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 1.75rem;
      box-shadow: var(--shadow-sm);
    }

    .card-header {
      margin-bottom: 1.5rem;

      h3 {
        font-size: 1.15rem;
        font-weight: 700;
        color: var(--text-primary);
      }

      p {
        font-size: 0.85rem;
        color: var(--text-secondary);
        margin-top: 0.2rem;
      }
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
    }

    .input-group {
      margin-bottom: 1.25rem;

      label {
        display: block;
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--text-primary);
        margin-bottom: 0.4rem;
      }

      input {
        width: 100%;
        padding: 0.65rem 0.85rem;
        background-color: var(--bg-surface);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-size: 0.9rem;
        outline: none;
        transition: border-color 0.2s;

        &:focus {
          border-color: var(--accent-primary);
        }
      }

      .hint {
        display: block;
        font-size: 0.75rem;
        color: var(--text-muted);
        margin-top: 0.35rem;
      }
    }

    .template-group {
      margin-bottom: 1.5rem;

      label {
        display: block;
        font-size: 0.9rem;
        font-weight: 700;
        color: var(--text-primary);
        margin-bottom: 0.4rem;
      }

      .template-textarea {
        width: 100%;
        padding: 0.75rem 1rem;
        background-color: var(--bg-surface);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-family: inherit;
        font-size: 0.875rem;
        line-height: 1.5;
        outline: none;
        resize: vertical;
        transition: border-color 0.2s;

        &:focus {
          border-color: var(--accent-primary);
        }
      }

      .hint {
        display: block;
        font-size: 0.75rem;
        color: var(--text-muted);
        margin-top: 0.35rem;
      }
    }

    .toggle-container {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      margin-bottom: 1rem;
      gap: 1rem;
    }

    .toggle-text {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .toggle-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-primary);
    }

    .toggle-desc {
      font-size: 0.775rem;
      color: var(--text-muted);
    }

    /* Switch */
    .switch {
      position: relative;
      display: inline-block;
      width: 44px;
      height: 24px;
      flex-shrink: 0;

      input { opacity: 0; width: 0; height: 0; }
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0; left: 0; right: 0; bottom: 0;
      background-color: #334155;
      transition: .3s;

      &:before {
        position: absolute;
        content: "";
        height: 18px;
        width: 18px;
        left: 3px;
        bottom: 3px;
        background-color: white;
        transition: .3s;
      }

      &.round {
        border-radius: 24px;
        &:before { border-radius: 50%; }
      }
    }

    input:checked + .slider {
      background-color: var(--accent-primary);
    }

    input:checked + .slider:before {
      transform: translateX(20px);
    }

    .card-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--border-color);
    }

    .simulation-list {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }

    .simulation-item {
      padding: 1rem;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);

      .sim-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 0.35rem;
      }

      .sim-badge {
        font-size: 0.8rem;
        font-weight: 700;
        color: var(--text-primary);
      }

      .sim-price {
        font-size: 0.95rem;
        font-weight: 800;
        color: #34d399;
      }

      .sim-details {
        font-size: 0.775rem;
        color: var(--text-secondary);
        line-height: 1.4;
      }
    }

    .alert-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.85rem 1.25rem;
      border-radius: var(--radius-md);
      margin-bottom: 1.5rem;
      font-size: 0.9rem;
      font-weight: 500;

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
  `]
})
export class ConfiguracoesComponent implements OnInit {
  private readonly configService = inject(TenantConfigService);
  private readonly adminService = inject(AppointmentService);

  readonly activeTab = signal<'FINANCIAL' | 'MESSAGES'>('FINANCIAL');
  readonly saving = signal<boolean>(false);
  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');

  config: TenantConfig = {
    studioName: 'Vício Tattoo CWB',
    defaultHourlyRate: 150.00,
    fixedSetupCost: 50.00,
    absorbCreditCardFees: true,
    signalPercentage: 30,
    minDepositAmount: 50.00,
    welcomeMessage: '👋 Olá! Seja muito bem-vindo(a) ao *Vício Tattoo CWB*!\nEu sou a assistente virtual e estou aqui para agilizar seu atendimento:\n\n1️⃣ *Solicitar Orçamento com IA* (Envie foto da ideia)\n2️⃣ *Agendar Horário / Ver Disponibilidade*\n3️⃣ *Falar com um Tatuador / Dúvidas*\n4️⃣ *Nossos Artistas e Portfólio*\n\nDigite o número da opção desejada:',
    orcamentoIntroMessage: '📸 *Envio de Referência para Orçamento*\n\nPor favor, envie uma *foto ou imagem de referência* da tatuagem que você deseja fazer.\n\nSe preferir, adicione também:\n- Tamanho aproximado em centímetros (ex: 10cm)\n- Local do corpo pretendido (ex: antebraço, ombro)',
    agendamentoIntroMessage: '📅 *Agendamento de Sessão*\n\nNossa equipe conta com 11 artistas especialistas. Para verificar a melhor data:\nQual dia da semana ou período você tem preferência? (ex: próxima sexta à tarde, sábado pela manhã)',
    humanTransferMessage: '💬 *Transbordo para Atendimento Humano*\n\nUm dos nossos tatuadores foi notificado e já vai assumir seu atendimento por aqui em instantes. Aguarde só um momento!',
    pixConfirmedMessage: '🎉 *Agendamento Confirmado com Sucesso!*\n\nSeu pagamento do sinal foi aprovado pelo estúdio. Nos vemos no Vício Tattoo! ⚡',
    botResetMessage: '🔄 Conversa reiniciada. Como podemos te ajudar agora?'
  };

  notifPrefs: NotificationPreferences = {
    notifyNewLead: true,
    notifyPaymentReceived: true,
    notifyHumanRequest: true
  };

  ngOnInit(): void {
    this.loadConfig();
    this.loadNotificationPrefs();
  }

  loadNotificationPrefs(): void {
    this.adminService.getNotificationPreferences().subscribe({
      next: (prefs) => {
        if (prefs) {
          this.notifPrefs = {
            notifyNewLead: prefs.notifyNewLead ?? true,
            notifyPaymentReceived: prefs.notifyPaymentReceived ?? true,
            notifyHumanRequest: prefs.notifyHumanRequest ?? true
          };
        }
      },
      error: () => {}
    });
  }

  saveNotificationPrefs(): void {
    this.adminService.updateNotificationPreferences(this.notifPrefs).subscribe({
      next: (updated) => {
        this.notifPrefs = updated;
        this.showToast('Preferências de notificação atualizadas!');
      },
      error: () => {
        this.showToast('Erro ao salvar preferências de notificação.', 'error');
      }
    });
  }

  loadConfig(): void {
    this.configService.getConfig().subscribe({
      next: (data) => {
        if (data) {
          this.config = {
            id: data.id,
            studioName: data.studioName || 'Vício Tattoo CWB',
            defaultHourlyRate: Number(data.defaultHourlyRate) || 150.00,
            fixedSetupCost: Number(data.fixedSetupCost) || 50.00,
            absorbCreditCardFees: data.absorbCreditCardFees ?? true,
            signalPercentage: Number(data.signalPercentage) || 30,
            minDepositAmount: Number(data.minDepositAmount) || 50.00,
            welcomeMessage: data.welcomeMessage || this.config.welcomeMessage,
            orcamentoIntroMessage: data.orcamentoIntroMessage || this.config.orcamentoIntroMessage,
            agendamentoIntroMessage: data.agendamentoIntroMessage || this.config.agendamentoIntroMessage,
            humanTransferMessage: data.humanTransferMessage || this.config.humanTransferMessage,
            pixConfirmedMessage: data.pixConfirmedMessage || this.config.pixConfirmedMessage,
            botResetMessage: data.botResetMessage || this.config.botResetMessage
          };
        }
      },
      error: (err) => {
        console.warn('Erro ao carregar configurações do backend. Usando padrão local.', err);
      }
    });
  }

  saveConfig(): void {
    this.saving.set(true);
    this.configService.updateConfig(this.config).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.config = {
          ...this.config,
          ...res,
          defaultHourlyRate: Number(res.defaultHourlyRate),
          fixedSetupCost: Number(res.fixedSetupCost),
          signalPercentage: Number(res.signalPercentage),
          minDepositAmount: Number(res.minDepositAmount)
        };
        this.showToast('Configurações salvas no banco com sucesso!');
      },
      error: (err) => {
        this.saving.set(false);
        this.showToast(err.error?.message || 'Erro ao persistir configurações no servidor.', 'error');
      }
    });
  }

  restoreDefaultTemplates(): void {
    this.config.welcomeMessage = '👋 Olá! Seja muito bem-vindo(a) ao *Vício Tattoo CWB*!\nEu sou a assistente virtual e estou aqui para agilizar seu atendimento:\n\n1️⃣ *Solicitar Orçamento com IA* (Envie foto da ideia)\n2️⃣ *Agendar Horário / Ver Disponibilidade*\n3️⃣ *Falar com um Tatuador / Dúvidas*\n4️⃣ *Nossos Artistas e Portfólio*\n\nDigite o número da opção desejada:';
    this.config.orcamentoIntroMessage = '📸 *Envio de Referência para Orçamento*\n\nPor favor, envie uma *foto ou imagem de referência* da tatuagem que você deseja fazer.\n\nSe preferir, adicione também:\n- Tamanho aproximado em centímetros (ex: 10cm)\n- Local do corpo pretendido (ex: antebraço, ombro)';
    this.config.agendamentoIntroMessage = '📅 *Agendamento de Sessão*\n\nNossa equipe conta com 11 artistas especialistas. Para verificar a melhor data:\nQual dia da semana ou período você tem preferência? (ex: próxima sexta à tarde, sábado pela manhã)';
    this.config.humanTransferMessage = '💬 *Transbordo para Atendimento Humano*\n\nUm dos nossos tatuadores foi notificado e já vai assumir seu atendimento por aqui em instantes. Aguarde só um momento!';
    this.config.pixConfirmedMessage = '🎉 *Agendamento Confirmado com Sucesso!*\n\nSeu pagamento do sinal foi aprovado pelo estúdio. Nos vemos no Vício Tattoo! ⚡';
    this.config.botResetMessage = '🔄 Conversa reiniciada. Como podemos te ajudar agora?';
    this.showToast('Textos restaurados para os padrões sugeridos. Clique em "Salvar" para confirmar.');
  }

  private showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => this.feedbackMessage.set(null), 5000);
  }
}
