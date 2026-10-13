import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenantConfigService } from '../../core/services/tenant-config.service';
import { TenantConfig } from '../../core/models/tenant-config.model';

@Component({
  selector: 'app-configuracoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Configurações do Estúdio</h1>
          <p class="page-subtitle">Parâmetros financeiros para a precificação de orçamentos pela IA do WhatsApp</p>
        </div>
      </div>

      <!-- Feedback Banner -->
      <div *ngIf="feedbackMessage()" class="alert-banner" [ngClass]="feedbackType()">
        <span>{{ feedbackMessage() }}</span>
        <button class="alert-close" (click)="feedbackMessage.set(null)">×</button>
      </div>

      <div class="config-grid">
        <!-- Configuration Form Card -->
        <div class="card form-card">
          <div class="card-header">
            <h3>Tabela de Preços & Operação</h3>
            <p>Esses valores são consumidos diretamente pelo modelo Gemini Vision ao gerar orçamentos automáticos.</p>
          </div>

          <div class="input-group">
            <label>Nome do Estúdio / Tenant</label>
            <input type="text" [(ngModel)]="config.studioName" placeholder="Ex: Vício Tattoo" />
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
            <button
              class="btn btn-primary"
              [disabled]="saving()"
              (click)="saveConfig()">
              <svg *ngIf="!saving()" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path>
                <polyline points="17 21 17 13 7 13 7 21"></polyline>
                <polyline points="7 3 7 8 15 8"></polyline>
              </svg>
              {{ saving() ? 'Salvando...' : 'Salvar Configurações' }}
            </button>
          </div>
        </div>

        <!-- Live Price Simulator Card -->
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
                <br>Sinal exigido (30%): <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 1) * 0.3) | number:'1.2-2' }}</strong>
              </p>
            </div>

            <div class="simulation-item highlight">
              <div class="sim-header">
                <span class="sim-badge">Tatuagem Média (2 horas)</span>
                <span class="sim-price">R$ {{ (config.fixedSetupCost + config.defaultHourlyRate * 2) | number:'1.2-2' }}</span>
              </div>
              <p class="sim-details">
                Setup (R$ {{ config.fixedSetupCost | number:'1.2-2' }}) + 2h (R$ {{ (config.defaultHourlyRate * 2) | number:'1.2-2' }})
                <br>Sinal exigido (30%): <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 2) * 0.3) | number:'1.2-2' }}</strong>
              </p>
            </div>

            <div class="simulation-item">
              <div class="sim-header">
                <span class="sim-badge">Tatuagem Grande (4 horas)</span>
                <span class="sim-price">R$ {{ (config.fixedSetupCost + config.defaultHourlyRate * 4) | number:'1.2-2' }}</span>
              </div>
              <p class="sim-details">
                Setup (R$ {{ config.fixedSetupCost | number:'1.2-2' }}) + 4h (R$ {{ (config.defaultHourlyRate * 4) | number:'1.2-2' }})
                <br>Sinal exigido (30%): <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 4) * 0.3) | number:'1.2-2' }}</strong>
              </p>
            </div>

            <div class="simulation-item touchup-item">
              <div class="sim-header">
                <span class="sim-badge badge-touchup">Sessão de Retoque</span>
                <span class="sim-price">R$ 0,00</span>
              </div>
              <p class="sim-details">
                Política Vício Tattoo: Retoques dentro do prazo são 100% gratuitos (Sem cobrança de sinal).
              </p>
            </div>
          </div>
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
      margin-bottom: 2rem;
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

    .config-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 1.5rem;

      @media (min-width: 992px) {
        grid-template-columns: 3fr 2fr;
      }
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

    .toggle-container {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1.25rem;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      margin-bottom: 1.5rem;
      gap: 1rem;
    }

    .toggle-text {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .toggle-title {
      font-size: 0.9rem;
      font-weight: 600;
      color: var(--text-primary);
    }

    .toggle-desc {
      font-size: 0.8rem;
      color: var(--text-muted);
    }

    /* Switch CSS */
    .switch {
      position: relative;
      display: inline-block;
      width: 48px;
      height: 26px;
      flex-shrink: 0;
    }

    .switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: var(--border-light);
      transition: .3s;
      border-radius: 34px;
    }

    .slider:before {
      position: absolute;
      content: "";
      height: 20px;
      width: 20px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: .3s;
      border-radius: 50%;
    }

    input:checked + .slider {
      background-color: var(--accent-primary);
    }

    input:checked + .slider:before {
      transform: translateX(22px);
    }

    .card-footer {
      display: flex;
      justify-content: flex-end;
      margin-top: 1rem;
    }

    /* Simulation list */
    .simulation-list {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .simulation-item {
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 1rem 1.25rem;

      &.highlight {
        border-color: rgba(99, 102, 241, 0.4);
        background: linear-gradient(135deg, rgba(99, 102, 241, 0.05), rgba(16, 24, 39, 0.8));
      }

      &.touchup-item {
        border-color: rgba(14, 165, 233, 0.3);
      }
    }

    .sim-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.4rem;
    }

    .sim-badge {
      font-size: 0.8rem;
      font-weight: 700;
      color: var(--text-primary);

      &.badge-touchup {
        color: var(--color-info);
      }
    }

    .sim-price {
      font-size: 1.1rem;
      font-weight: 800;
      color: var(--color-success);
    }

    .sim-details {
      font-size: 0.8rem;
      color: var(--text-secondary);
      line-height: 1.4;

      strong {
        color: var(--text-primary);
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

  readonly saving = signal<boolean>(false);
  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');

  config: TenantConfig = {
    studioName: 'Vício Tattoo',
    defaultHourlyRate: 150.00,
    fixedSetupCost: 50.00,
    absorbCreditCardFees: true
  };

  ngOnInit(): void {
    this.loadConfig();
  }

  loadConfig(): void {
    this.configService.getConfig().subscribe({
      next: (data) => {
        if (data) {
          this.config = {
            id: data.id,
            studioName: data.studioName || 'Vício Tattoo',
            defaultHourlyRate: Number(data.defaultHourlyRate) || 150.00,
            fixedSetupCost: Number(data.fixedSetupCost) || 50.00,
            absorbCreditCardFees: data.absorbCreditCardFees ?? true
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
        this.config = res;
        this.showToast('Configurações salvas com sucesso!');
      },
      error: (err) => {
        this.saving.set(false);
        this.showToast(err.error?.message || 'Erro ao salvar configurações.', 'error');
      }
    });
  }

  showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => {
      this.feedbackMessage.set(null);
    }, 5000);
  }
}
