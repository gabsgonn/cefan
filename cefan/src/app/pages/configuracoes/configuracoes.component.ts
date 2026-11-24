import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenantConfigService } from '../../core/services/tenant-config.service';
import { AppointmentService } from '../../core/services/appointment.service';
import { ChatService, BotStatus } from '../../core/services/chat.service';
import { TenantConfig } from '../../core/models/tenant-config.model';
import { NotificationPreferences } from '../../core/models/appointment.model';
import { DOC_ORIENTATION, NgxImageCompressService } from 'ngx-image-compress';

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

          <div class="input-group" [class.field-changed]="isFieldChanged('studioName')">
            <label>
              Nome do Estúdio / Tenant
              @if (isFieldChanged('studioName')) {
                <span class="changed-badge">Alterado</span>
              }
            </label>
            <input type="text" [(ngModel)]="config.studioName" placeholder="Ex: Nome do seu estúdio" />
            <span class="hint">Nome exibido nas mensagens automáticas de WhatsApp enviadas pelo bot.</span>
          </div>

          <div class="logo-section input-group" [class.field-changed]="isFieldChanged('logoBase64')">
            <h3 class="logo-title">
              Logo do Estúdio
              @if (isFieldChanged('logoBase64')) {
                <span class="changed-badge">Alterado</span>
              }
            </h3>

            <div class="logo-boxes">
              <!-- Caixinha 1: Preview da logo -->
              <div class="logo-box preview-box" [class.field-changed]="isFieldChanged('logoBase64')">
                @if (logoPreview()) {
                  <img [src]="logoPreview()" alt="Logo do estúdio" class="logo-img" />
                  @if (isFieldChanged('logoBase64')) {
                    <span class="logo-unsaved-tag">Prévia não salva</span>
                  }
                } @else {
                  <span class="empty-msg">Nenhuma logo adicionada ainda</span>
                }
              </div>

              <!-- Caixinha 2: Botão/área de upload -->
              <label class="logo-box upload-box">
                <input
                  type="file"
                  accept="image/png, image/jpeg, image/webp"
                  (change)="onFileSelected($event)"
                  hidden
                />
                <span class="upload-icon">
                  <svg width="25px" height="25px" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path fill-rule="evenodd" clip-rule="evenodd" d="M12 4C12.5523 4 13 4.44772 13 5V11H19C19.5523 11 20 11.4477 20 12C20 12.5523 19.5523 13 19 13H13V19C13 19.5523 12.5523 20 12 20C11.4477 20 11 19.5523 11 19V13H5C4.44772 13 4 12.5523 4 12C4 11.4477 4.44772 11 5 11H11V5C11 4.44772 11.4477 4 12 4Z" fill="#FFFFFF"/>
                  </svg>
                </span>
                <span class="upload-text">
                  {{ logoPreview() ? 'Alterar logo' : 'Adicionar logo' }}
                </span>
              </label>
            </div>
          </div>

          <div class="form-row">
            <div class="input-group" [class.field-changed]="isFieldChanged('defaultHourlyRate')">
              <label>
                Valor da Hora do Tatuador (R$)
                @if (isFieldChanged('defaultHourlyRate')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <input type="number" step="10.00" [(ngModel)]="config.defaultHourlyRate" />
              <span class="hint">Custo base por hora de agulha estimada pela IA.</span>
            </div>

            <div class="input-group" [class.field-changed]="isFieldChanged('fixedSetupCost')">
              <label>
                Custo Fixo de Montagem / Setup (R$)
                @if (isFieldChanged('fixedSetupCost')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <input type="number" step="5.00" [(ngModel)]="config.fixedSetupCost" />
              <span class="hint">Taxa de biossegurança, materiais descartáveis e agulhas.</span>
            </div>
          </div>

          <div class="form-row">
            <div class="input-group" [class.field-changed]="isFieldChanged('signalPercentage')">
              <label>
                Sinal de Reserva (% do Orçamento)
                @if (isFieldChanged('signalPercentage')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <input type="number" step="5.00" [(ngModel)]="config.signalPercentage" placeholder="30" />
              <span class="hint">Percentual cobrado via PIX para garantir o agendamento (Padrão: 30%).</span>
            </div>

            <div class="input-group" [class.field-changed]="isFieldChanged('minDepositAmount')">
              <label>
                Valor Mínimo do Sinal (R$)
                @if (isFieldChanged('minDepositAmount')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <input type="number" step="10.00" [(ngModel)]="config.minDepositAmount" placeholder="50.00" />
              <span class="hint">Piso mínimo do valor do sinal (Padrão: R$ 50,00).</span>
            </div>
          </div>

          <div class="form-row">
            <div class="input-group" [class.field-changed]="isFieldChanged('pixExpirationMinutes')">
              <label>
                Expiração do Sinal PIX (Minutos)
                @if (isFieldChanged('pixExpirationMinutes')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <input type="number" step="15" min="15" [(ngModel)]="config.pixExpirationMinutes" placeholder="120" />
              <span class="hint">Tempo limite para pagamento do sinal antes de liberar a vaga (Padrão: 120 min / 2h).</span>
            </div>

            <div class="input-group" [class.field-changed]="isFieldChanged('workingDays')">
              <label>
                Dias de Funcionamento do Estúdio
                @if (isFieldChanged('workingDays')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <select [(ngModel)]="config.workingDays">
                <option value="SEG-SAB">Segunda a Sábado (Seg-Sáb)</option>
                <option value="SEG-SEX">Segunda a Sexta (Seg-Sex)</option>
                <option value="SEG-DOM">Todos os Dias (Seg-Dom)</option>
              </select>
              <span class="hint">Grade de dias considerada pelo bot ao sugerir e validar agendamentos.</span>
            </div>
          </div>

          <div class="form-row">
            <div class="input-group" [class.field-changed]="isFieldChanged('openingTime')">
              <label>
                Horário de Abertura
                @if (isFieldChanged('openingTime')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <input type="time" [(ngModel)]="config.openingTime" />
              <span class="hint">Início do expediente diário (Padrão: 10:00).</span>
            </div>

            <div class="input-group" [class.field-changed]="isFieldChanged('closingTime')">
              <label>
                Horário de Fechamento
                @if (isFieldChanged('closingTime')) {
                  <span class="changed-badge">Alterado</span>
                }
              </label>
              <input type="time" [(ngModel)]="config.closingTime" />
              <span class="hint">Término do expediente diário (Padrão: 20:00).</span>
            </div>
          </div>

          <div class="toggle-container" [class.field-changed]="isFieldChanged('absorbCreditCardFees')">
            <div class="toggle-text">
              <span class="toggle-title">
                Absorver Taxas do Gateway (Mercado Pago)
                @if (isFieldChanged('absorbCreditCardFees')) {
                  <span class="changed-badge">Alterado</span>
                }
              </span>
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
            @if (hasUnsavedChanges()) {
              <button type="button" class="btn btn-secondary" [disabled]="saving()" (click)="discardChanges()">
                Descartar Alterações
              </button>
            }
            <button class="btn btn-primary" [disabled]="saving()" (click)="saveConfig()">
              {{ saving() ? 'Salvando...' : (hasUnsavedChanges() ? 'Salvar Parâmetros (' + changedFieldsCount() + ')' : 'Salvar Parâmetros Financeiros') }}
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
                <span class="sim-badge">A • Flash / Mini (até 5 cm • ~1h)</span>
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
                <span class="sim-badge">B • Pequena (6 a 10 cm • ~1,5h)</span>
                <span class="sim-price">R$ {{ (config.fixedSetupCost + config.defaultHourlyRate * 1.5) | number:'1.2-2' }}</span>
              </div>
              <p class="sim-details">
                Setup (R$ {{ config.fixedSetupCost | number:'1.2-2' }}) + 1,5h (R$ {{ (config.defaultHourlyRate * 1.5) | number:'1.2-2' }})
                <br>Sinal exigido ({{ config.signalPercentage || 30 }}%):
                <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 1.5) * ((config.signalPercentage || 30) / 100)) | number:'1.2-2' }}</strong>
              </p>
            </div>

            <div class="simulation-item">
              <div class="sim-header">
                <span class="sim-badge">C • Média (11 a 18 cm • ~2,5h)</span>
                <span class="sim-price">R$ {{ (config.fixedSetupCost + config.defaultHourlyRate * 2.5) | number:'1.2-2' }}</span>
              </div>
              <p class="sim-details">
                Setup (R$ {{ config.fixedSetupCost | number:'1.2-2' }}) + 2.5h (R$ {{ (config.defaultHourlyRate * 2.5) | number:'1.2-2' }})
                <br>Sinal exigido ({{ config.signalPercentage || 30 }}%):
                <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 2.5) * ((config.signalPercentage || 30) / 100)) | number:'1.2-2' }}</strong>
              </p>
            </div>

            <div class="simulation-item">
              <div class="sim-header">
                <span class="sim-badge">D • Grande (19 a 25 cm • ~4h)</span>
                <span class="sim-price">R$ {{ (config.fixedSetupCost + config.defaultHourlyRate * 4) | number:'1.2-2' }}</span>
              </div>
              <p class="sim-details">
                Setup (R$ {{ config.fixedSetupCost | number:'1.2-2' }}) + 4h (R$ {{ (config.defaultHourlyRate * 4) | number:'1.2-2' }})
                <br>Sinal exigido ({{ config.signalPercentage || 30 }}%):
                <strong>R$ {{ ((config.fixedSetupCost + config.defaultHourlyRate * 4) * ((config.signalPercentage || 30) / 100)) | number:'1.2-2' }}</strong>
              </p>
            </div>

            <div class="simulation-item">
              <div class="sim-header">
                <span class="sim-badge">E • Fechamento (26 cm+ / Projeto Amplo)</span>
                <span class="sim-price">A combinar</span>
              </div>
              <p class="sim-details">
                Encaminhamento automático para o <strong>Tatuador Especialista</strong> recomendado pela IA (Transbordo Humano).
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

          <div class="card-header" style="margin-top: 1.5rem;">
            <h3>Central de Diagnóstico & Resiliência</h3>
            <p>Monitoramento da infraestrutura do bot e conexão com o WhatsApp:</p>
          </div>

          <div class="simulation-list">
            <div class="simulation-item">
              <div class="sim-header">
                <span class="sim-badge">🟢 Backend Spring Boot Core</span>
                <span class="sim-price">{{ botStatus()?.springBootOnline !== false ? 'Online' : 'Offline' }}</span>
              </div>
              <div class="sim-header">
                <span class="sim-badge">🟢 Banco de Dados PostgreSQL</span>
                <span class="sim-price">{{ botStatus()?.postgresOnline !== false ? 'Online' : 'Offline' }}</span>
              </div>
              <div class="sim-header">
                <span class="sim-badge">🟢 Evolution API / Instância ({{ botStatus()?.instance || 'cefan-studio' }})</span>
                <span class="sim-price">{{ botStatus()?.status === 'CONNECTED' ? 'Conectada' : 'Reconectando' }}</span>
              </div>
              <div class="sim-header" style="margin-bottom: 0;">
                <span class="sim-badge">🟢 Mercado Pago Webhook PIX</span>
                <span class="sim-price">{{ botStatus()?.mercadoPagoWebhookActive !== false ? 'Ativo' : 'Inativo' }}</span>
              </div>
            </div>
          </div>

          <div class="card-footer" style="margin-top: 1rem;">
            <button type="button" class="btn btn-secondary" [disabled]="testingConnection()" (click)="testWhatsAppConnection()">
              {{ testingConnection() ? 'Verificando...' : 'Testar Conexão WhatsApp' }}
            </button>
            <button type="button" class="btn btn-primary" [disabled]="restartingBot()" (click)="restartWhatsAppBot()">
              {{ restartingBot() ? 'Reiniciando...' : 'Reiniciar Bot WhatsApp' }}
            </button>
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

        <div class="template-group" [class.field-changed]="isFieldChanged('welcomeMessage')">
          <label>
            1. Mensagem de Boas-Vindas (Menu Inicial)
            @if (isFieldChanged('welcomeMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="5" [(ngModel)]="config.welcomeMessage" class="template-textarea"></textarea>
          <span class="hint">Enviada na primeira interação ou quando o cliente envia "0" ou "menu".</span>
        </div>

        <div class="template-group" [class.field-changed]="isFieldChanged('orcamentoIntroMessage')">
          <label>
            2. Instruções para Envio de Foto de Orçamento (Opção 1)
            @if (isFieldChanged('orcamentoIntroMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="4" [(ngModel)]="config.orcamentoIntroMessage" class="template-textarea"></textarea>
          <span class="hint">Orientação sobre envio de foto, tamanho em cm e local do corpo.</span>
        </div>

        <div class="template-group" [class.field-changed]="isFieldChanged('agendamentoIntroMessage')">
          <label>
            3. Início de Agendamento (Opção 2)
            @if (isFieldChanged('agendamentoIntroMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="4" [(ngModel)]="config.agendamentoIntroMessage" class="template-textarea"></textarea>
          <span class="hint">Solicita a preferência de profissional e dias/períodos desejados.</span>
        </div>

        <div class="template-group" [class.field-changed]="isFieldChanged('humanTransferMessage')">
          <label>
            4. Mensagem de Transbordo Humano (Opção 3)
            @if (isFieldChanged('humanTransferMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="3" [(ngModel)]="config.humanTransferMessage" class="template-textarea"></textarea>
          <span class="hint">Informa que a equipe foi notificada e vai assumir o chat.</span>
        </div>

        <div class="template-group" [class.field-changed]="isFieldChanged('pixPaymentMessage')">
          <label>
            5. Instruções de Pré-Agendamento & Chave PIX
            @if (isFieldChanged('pixPaymentMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="3" [(ngModel)]="config.pixPaymentMessage" class="template-textarea"></textarea>
          <span class="hint">Cabeçalho enviado junto com os dados do pré-agendamento e código PIX Copia e Cola.</span>
        </div>

        <div class="template-group" [class.field-changed]="isFieldChanged('pixConfirmedMessage')">
          <label>
            6. Confirmação de Sinal Pago via PIX
            @if (isFieldChanged('pixConfirmedMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="3" [(ngModel)]="config.pixConfirmedMessage" class="template-textarea"></textarea>
          <span class="hint">Enviada automaticamente quando o webhook do Mercado Pago acusa pagamento aprovado.</span>
        </div>

        <div class="template-group" [class.field-changed]="isFieldChanged('botResetMessage')">
          <label>
            7. Mensagem de Reinício de Conversa (Reset Bot)
            @if (isFieldChanged('botResetMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="2" [(ngModel)]="config.botResetMessage" class="template-textarea"></textarea>
          <span class="hint">Enviada quando o operador clica no botão "Reset Bot".</span>
        </div>

        <div class="template-group" [class.field-changed]="isFieldChanged('absenceMessage')">
          <label>
            8. Mensagem de Ausência / Fora de Expediente
            @if (isFieldChanged('absenceMessage')) {
              <span class="changed-badge">Alterado</span>
            }
          </label>
          <textarea rows="3" [(ngModel)]="config.absenceMessage" class="template-textarea"></textarea>
          <span class="hint">Aviso sobre o horário de funcionamento do estúdio ({{ config.workingDays || 'SEG-SAB' }}, {{ config.openingTime || '10:00' }} às {{ config.closingTime || '20:00' }}).</span>
        </div>

        <div class="card-header" style="margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid var(--border-color);">
          <h3>Campos Visíveis no Orçamento com IA (WhatsApp)</h3>
          <p>
            Escolha quais informações aparecem na mensagem de orçamento enviada ao cliente. As observações técnicas de agulhas e bancada ficam restritas ao painel do tatuador. O aviso de estimativa preliminar (quando o preço estiver ativo) e as 3 opções pós-orçamento são fixos.
          </p>
        </div>

        <div class="quote-customization-grid">
          <div class="quote-toggles-list">
            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowGreeting')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Saudação Inicial ("Olá, Cliente! Analisamos...")
                  @if (isFieldChanged('orcamentoShowGreeting')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Exibe a saudação personalizada antes dos detalhes da arte.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowGreeting" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowStyle')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Estilo Identificado (📌 Estilo)
                  @if (isFieldChanged('orcamentoShowStyle')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Mostra o estilo artístico detectado pela IA (ex: Old School / Tradicional).</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowStyle" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowSize')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Tamanho Estimado (📏 Tamanho)
                  @if (isFieldChanged('orcamentoShowSize')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Mostra a categoria de porte e faixa em centímetros.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowSize" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowDuration')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Tempo Estimado de Sessão (⏱️ Tempo de agulha)
                  @if (isFieldChanged('orcamentoShowDuration')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Exibe a duração aproximada em horas de sessão.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowDuration" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowComplexity')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Nível de Complexidade (🔍 Complexidade)
                  @if (isFieldChanged('orcamentoShowComplexity')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Exibe a classificação técnica de complexidade (BAIXA, MEDIA ou ALTA).</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowComplexity" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowLocation')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Local Detectado / Sugerido (📍 Local)
                  @if (isFieldChanged('orcamentoShowLocation')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Mostra o local do corpo detectado na foto e até 2 sugestões anatômicas.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowLocation" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowArtists')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Tatuador Especialista Recomendado (👨‍🎨 Artista)
                  @if (isFieldChanged('orcamentoShowArtists')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Indica o profissional do estúdio especialista no estilo da referência.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowArtists" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowDescription')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Descrição Simples da Arte (📝 Sobre a sua arte)
                  @if (isFieldChanged('orcamentoShowDescription')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Exibe 1–2 frases em linguagem simples para o cliente (sem notas técnicas de agulhas).</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowDescription" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowPrice')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Faixa de Preço Estimada (💰 Valor + Aviso de Estimativa)
                  @if (isFieldChanged('orcamentoShowPrice')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Se desativado, oculta a faixa de preço no orçamento e o saldo restante no resumo do PIX.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowPrice" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowMaterialsNote')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Nota de Materiais Descartáveis & Biossegurança
                  @if (isFieldChanged('orcamentoShowMaterialsNote')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Exibe a nota sobre materiais 100% descartáveis e esterilizados.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowMaterialsNote" />
                <span class="slider round"></span>
              </label>
            </div>

            <div class="toggle-container" [class.field-changed]="isFieldChanged('orcamentoShowAdjustHint')">
              <div class="toggle-text">
                <span class="toggle-title">
                  Dica de Ajuste de Tamanho (💡 Informe a medida em cm)
                  @if (isFieldChanged('orcamentoShowAdjustHint')) { <span class="changed-badge">Alterado</span> }
                </span>
                <span class="toggle-desc">Orienta o cliente a enviar a medida em cm ou "fechamento" para recalcular.</span>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="config.orcamentoShowAdjustHint" />
                <span class="slider round"></span>
              </label>
            </div>
          </div>

          <div class="quote-live-preview-box">
            <div class="preview-box-header">
              <span>📱 Pré-visualização da Mensagem no WhatsApp</span>
              <span class="preview-tag">Tempo Real</span>
            </div>
            <pre class="whatsapp-preview-bubble">{{ buildQuotePreviewText() }}</pre>
          </div>
        </div>

        <div class="card-footer">
          @if (hasUnsavedChanges()) {
            <button type="button" class="btn btn-secondary" [disabled]="saving()" (click)="discardChanges()">
              Descartar Alterações
            </button>
          }
          <button class="btn btn-secondary" (click)="restoreDefaultTemplates()">
            Restaurar Textos Sugeridos
          </button>
          <button class="btn btn-primary" [disabled]="saving()" (click)="saveConfig()">
            {{ saving() ? 'Salvando...' : (hasUnsavedChanges() ? 'Salvar Mensagens (' + changedFieldsCount() + ')' : 'Salvar Todas as Mensagens no Banco') }}
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

    /* Apple iOS Segmented Control Tabs */
    .tabs-nav {
      display: flex;
      background: rgba(255, 255, 255, 0.07);
      padding: 4px;
      border-radius: var(--radius-full);
      border: 1px solid rgba(255, 255, 255, 0.08);
      gap: 4px;
      margin-bottom: 1.75rem;
      width: 100%;
      max-width: 620px;
    }

    .tab-btn {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      min-height: 44px;
      padding: 0.55rem 1rem;
      background: transparent;
      border: none;
      border-radius: var(--radius-full);
      color: var(--text-secondary);
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      user-select: none;

      &:hover {
        color: var(--text-primary);
      }

      &:active {
        transform: scale(0.97);
      }

      &.active {
        color: #ffffff;
        background-color: var(--accent-primary);
        box-shadow: 0 4px 14px var(--accent-glow);
        font-weight: 700;
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
      background: var(--bg-card);
      backdrop-filter: var(--glass-blur);
      -webkit-backdrop-filter: var(--glass-blur);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-xl);
      padding: 1.75rem;
      box-shadow: var(--shadow-md);
    }

    .card-header {
      margin-bottom: 1.5rem;

      h3 {
        font-size: 1.2rem;
        font-weight: 700;
        letter-spacing: -0.02em;
        color: var(--text-primary);
      }

      p {
        font-size: 0.85rem;
        color: var(--text-secondary);
        margin-top: 0.25rem;
        line-height: 1.4;
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
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--text-primary);
        margin-bottom: 0.45rem;
        letter-spacing: -0.01em;
      }

      input, select {
        width: 100%;
        min-height: 46px;
        padding: 0.65rem 0.95rem;
        background-color: var(--bg-surface);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-size: 16px; /* 16px prevents iOS Safari auto-zoom */
        outline: none;
        transition: border-color 0.2s, box-shadow 0.2s;

        &:focus {
          border-color: var(--accent-primary);
          box-shadow: 0 0 0 3px var(--accent-glow);
        }
      }

      .hint {
        display: block;
        font-size: 0.75rem;
        color: var(--text-muted);
        margin-top: 0.35rem;
        line-height: 1.35;
      }
    }

    .logo-section {
      margin-bottom: 1.5rem;
    }

    .logo-title {
      margin: 0 0 0.65rem 0;
      font-size: 0.85rem;
      font-weight: 600;
      letter-spacing: -0.01em;
      color: var(--text-primary);
    }

    .logo-boxes {
      display: grid;
      grid-template-columns: repeat(2, minmax(150px, 190px));
      gap: 1rem;
    }

    .logo-box {
      height: 136px;
      border-radius: var(--radius-lg);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      text-align: center;
      box-sizing: border-box;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .preview-box {
      position: relative;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      overflow: hidden;

      &.field-changed {
        border-color: #f59e0b;
        box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.16);
      }

      .logo-img {
        max-width: 100%;
        max-height: 100%;
        object-fit: contain;
        border-radius: calc(var(--radius-md) - 2px);
      }

      .logo-unsaved-tag {
        position: absolute;
        bottom: 6px;
        left: 50%;
        transform: translateX(-50%);
        background: rgba(245, 158, 11, 0.92);
        color: #111827;
        font-size: 0.65rem;
        font-weight: 700;
        padding: 2px 7px;
        border-radius: var(--radius-full);
        white-space: nowrap;
        letter-spacing: 0.01em;
      }

      .empty-msg {
        font-size: 0.775rem;
        color: var(--text-muted);
        line-height: 1.4;
      }
    }

    .changed-badge {
      display: inline-flex;
      align-items: center;
      margin-left: 0.45rem;
      padding: 0.1rem 0.45rem;
      border-radius: var(--radius-full);
      background: rgba(245, 158, 11, 0.16);
      border: 1px solid rgba(245, 158, 11, 0.45);
      color: #fbbf24;
      font-size: 0.68rem;
      font-weight: 700;
      letter-spacing: 0.02em;
      vertical-align: middle;
    }

    .field-changed > input,
    .field-changed > select,
    .field-changed > .template-textarea {
      border-color: #f59e0b !important;
      box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.14);
    }

    .toggle-container.field-changed {
      border-color: rgba(245, 158, 11, 0.55);
      box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.14);
    }

    .upload-box {
      background: rgba(255, 255, 255, 0.03);
      border: 1.5px dashed var(--border-color);
      cursor: pointer;
      user-select: none;
      justify-content: center;
      align-items: center;
      display: flex !important;

      .upload-icon {
        width: 36px;
        height: 36px;
        border-radius: var(--radius-full);
        background: rgba(255, 255, 255, 0.06);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 1.35rem;
        line-height: 1;
        margin-bottom: 0.5rem;
        color: var(--text-secondary);
        transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      }

      .upload-text {
        font-size: 0.825rem;
        font-weight: 600;
        color: var(--text-secondary);
        transition: color 0.2s;
      }

      &:hover {
        border-color: var(--accent-primary);
        background: rgba(255, 255, 255, 0.06);
        box-shadow: 0 0 0 3px var(--accent-glow);

        .upload-icon {
          background: var(--accent-primary);
          color: #ffffff;
        }

        .upload-text {
          color: var(--text-primary);
        }
      }

      &:active {
        transform: scale(0.97);
      }
    }

    .template-group {
      margin-bottom: 1.5rem;

      label {
        display: block;
        font-size: 0.875rem;
        font-weight: 700;
        color: var(--text-primary);
        margin-bottom: 0.45rem;
      }

      .template-textarea {
        width: 100%;
        padding: 0.75rem 1rem;
        background-color: var(--bg-surface);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-family: inherit;
        font-size: 16px; /* 16px prevents iOS Safari auto-zoom */
        line-height: 1.5;
        outline: none;
        resize: vertical;
        transition: border-color 0.2s, box-shadow 0.2s;

        &:focus {
          border-color: var(--accent-primary);
          box-shadow: 0 0 0 3px var(--accent-glow);
        }
      }

      .hint {
        display: block;
        font-size: 0.75rem;
        color: var(--text-muted);
        margin-top: 0.35rem;
        line-height: 1.35;
      }
    }

    /* iOS Grouped Inset Toggle */
    .toggle-container {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      background: rgba(16, 21, 34, 0.7);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      margin-bottom: 1.25rem;
      gap: 1rem;
      min-height: 56px;
    }

    .toggle-text {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .toggle-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-primary);
    }

    .toggle-desc {
      font-size: 0.775rem;
      color: var(--text-muted);
      line-height: 1.35;
    }

    /* iOS Native Switch */
    .switch {
      position: relative;
      display: inline-block;
      width: 52px;
      height: 32px;
      flex-shrink: 0;

      input { opacity: 0; width: 0; height: 0; }
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0; left: 0; right: 0; bottom: 0;
      background-color: rgba(255, 255, 255, 0.16);
      transition: background-color 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      border: 1px solid rgba(255, 255, 255, 0.08);

      &:before {
        position: absolute;
        content: "";
        height: 26px;
        width: 26px;
        left: 2px;
        bottom: 2px;
        background-color: white;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
        transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      }

      &.round {
        border-radius: 32px;
        &:before { border-radius: 50%; }
      }
    }

    input:checked + .slider {
      background-color: var(--color-success);
      border-color: rgba(48, 209, 88, 0.4);
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

    .quote-customization-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 1.5rem;
      margin-top: 1rem;

      @media (min-width: 992px) {
        grid-template-columns: 1.2fr 1fr;
        align-items: start;
      }
    }

    .quote-live-preview-box {
      position: sticky;
      top: 1.5rem;
      background: rgba(11, 20, 26, 0.92);
      border: 1px solid rgba(16, 185, 129, 0.3);
      border-radius: var(--radius-lg);
      padding: 1.25rem;
    }

    .preview-box-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.82rem;
      font-weight: 700;
      color: var(--text-primary);
      margin-bottom: 0.85rem;
    }

    .preview-tag {
      padding: 0.15rem 0.55rem;
      border-radius: var(--radius-full);
      background: rgba(16, 185, 129, 0.16);
      color: var(--color-success);
      font-size: 0.7rem;
      font-weight: 700;
    }

    .whatsapp-preview-bubble {
      margin: 0;
      padding: 1rem 1.1rem;
      background: #005c4b;
      color: #e9edef;
      border-radius: 12px;
      font-family: inherit;
      font-size: 0.82rem;
      line-height: 1.5;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .simulation-item {
      padding: 1rem 1.15rem;
      background: rgba(16, 21, 34, 0.75);
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
        color: var(--color-success);
      }

      .sim-details {
        font-size: 0.775rem;
        color: var(--text-secondary);
        line-height: 1.45;
      }
    }

    @media (max-width: 768px) {
      .page-container {
        padding: 1rem 0.85rem calc(76px + var(--safe-bottom));
      }

      .page-title {
        font-size: 1.35rem;
      }

      .tabs-nav {
        max-width: 100%;
      }

      .tab-btn {
        font-size: 0.78rem;
        padding: 0.5rem 0.65rem;
        min-height: 44px;

        span {
          text-align: center;
        }
      }

      .form-row {
        grid-template-columns: 1fr;
        gap: 0;
      }

      .card {
        padding: 1.15rem;
      }

      .card-footer {
        flex-direction: column;

        .btn {
          width: 100%;
          min-height: 48px;
        }
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
      font-size: 1.4rem;
      cursor: pointer;
      min-width: 40px;
      min-height: 40px;
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    /* ===== Responsive polish ===== */
    :host { display: block; max-width: 100%; overflow-x: hidden; }
    .page-container, .card, .config-grid > *, .quote-customization-grid > *, .form-row > * { min-width: 0; }
    .page-title, .page-subtitle, .card-header h3, .card-header p, .toggle-title, .toggle-desc, .hint, .sim-badge, .template-group label, .input-group label {
      overflow-wrap: anywhere;
    }
    .input-group input, .input-group select, .template-textarea { box-sizing: border-box; max-width: 100%; }
    .alert-banner { gap: 0.75rem; span { min-width: 0; overflow-wrap: anywhere; } }
    .toggle-text { min-width: 0; flex: 1; }
    .simulation-item .sim-header { flex-wrap: wrap; gap: 0.25rem 0.75rem; }
    .simulation-item .sim-badge { min-width: 0; flex: 1 1 auto; }
    .simulation-item .sim-price { white-space: nowrap; }
    .card-footer { flex-wrap: wrap; .btn { min-height: 44px; } }
    .preview-box-header { gap: 0.5rem; flex-wrap: wrap; }
    .whatsapp-preview-bubble { overflow-wrap: anywhere; max-width: 100%; }
    .tab-btn svg { flex-shrink: 0; }

    @media (max-width: 991px) {
      .quote-live-preview-box { position: static; }
    }

    @media (max-width: 480px) {
      .page-container { padding-left: max(0.75rem, env(safe-area-inset-left)); padding-right: max(0.75rem, env(safe-area-inset-right)); }
      .page-title { font-size: 1.2rem; }
      .page-subtitle { font-size: 0.85rem; }
      .card { padding: 1rem; border-radius: var(--radius-lg); }
      .card-header h3 { font-size: 1.05rem; }
      .tabs-nav { border-radius: var(--radius-lg); }
      .tab-btn {
        flex-direction: column;
        gap: 0.2rem;
        font-size: 0.72rem;
        line-height: 1.2;
        padding: 0.45rem 0.4rem;
        border-radius: calc(var(--radius-lg) - 4px);
      }
      .logo-boxes { grid-template-columns: 1fr 1fr; gap: 0.75rem; }
      .logo-box { height: 120px; padding: 0.75rem; }
      .toggle-container { padding: 0.85rem; gap: 0.75rem; }
      .quote-live-preview-box { padding: 0.9rem; }
      .whatsapp-preview-bubble { padding: 0.85rem; }
      .simulation-item { padding: 0.85rem; }
    }
  `]
})
export class ConfiguracoesComponent implements OnInit {
  private readonly configService = inject(TenantConfigService);
  private readonly adminService = inject(AppointmentService);
  private readonly chatService = inject(ChatService);
  private readonly imageCompress = inject(NgxImageCompressService);

  readonly activeTab = signal<'FINANCIAL' | 'MESSAGES'>('FINANCIAL');
  readonly saving = signal<boolean>(false);
  readonly testingConnection = signal<boolean>(false);
  readonly restartingBot = signal<boolean>(false);
  readonly botStatus = signal<BotStatus | null>(null);
  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');
  readonly logoPreview = signal<string | null>(null);
  readonly savedConfig = signal<TenantConfig | null>(null);

  private readonly trackedFields: (keyof TenantConfig)[] = [
    'studioName',
    'logoBase64',
    'defaultHourlyRate',
    'fixedSetupCost',
    'absorbCreditCardFees',
    'signalPercentage',
    'minDepositAmount',
    'pixExpirationMinutes',
    'openingTime',
    'closingTime',
    'workingDays',
    'welcomeMessage',
    'orcamentoIntroMessage',
    'agendamentoIntroMessage',
    'pixPaymentMessage',
    'humanTransferMessage',
    'pixConfirmedMessage',
    'botResetMessage',
    'absenceMessage',
    'orcamentoShowGreeting',
    'orcamentoShowStyle',
    'orcamentoShowSize',
    'orcamentoShowDuration',
    'orcamentoShowComplexity',
    'orcamentoShowLocation',
    'orcamentoShowArtists',
    'orcamentoShowDescription',
    'orcamentoShowPrice',
    'orcamentoShowMaterialsNote',
    'orcamentoShowAdjustHint'
  ];

  config: TenantConfig = {
    studioName: '',
    logoBase64: null,
    defaultHourlyRate: 150.00,
    fixedSetupCost: 50.00,
    absorbCreditCardFees: true,
    signalPercentage: 30,
    minDepositAmount: 50.00,
    pixExpirationMinutes: 120,
    openingTime: '10:00',
    closingTime: '20:00',
    workingDays: 'SEG-SAB',
    welcomeMessage: '👋 Olá! Seja muito bem-vindo(a) ao *Vício Tattoo CWB*!\nEu sou a assistente virtual e estou aqui para agilizar seu atendimento:\n\n1️⃣ *Solicitar Orçamento com IA* (Envie foto da ideia)\n2️⃣ *Agendar Horário / Ver Disponibilidade*\n3️⃣ *Falar com um Tatuador / Dúvidas*\n4️⃣ *Nossos Artistas e Portfólio*\n\nDigite o número da opção desejada:',
    orcamentoIntroMessage: '📸 *Envio de Referência para Orçamento*\n\nPor favor, envie uma *foto da tatuagem* (ou referência visual) que você deseja fazer, informe o local do corpo e escolha uma das nossas *opções de tamanho*:\n\n📏 *Opções de Tamanho da Tatuagem:*\n• Digite *A* (ou *Flash*) — *Flash / Mini* (até 5 cm)\n• Digite *B* (ou *Pequena*) — *Pequena* (6 a 10 cm)\n• Digite *C* (ou *Média*) — *Média* (11 a 18 cm)\n• Digite *D* (ou *Grande*) — *Grande* (19 a 25 cm)\n• Digite *E* (ou *Fechamento*) — *Fechamento* (26 cm+ / Projeto Amplo — _Valor a combinar_)',
    agendamentoIntroMessage: '📅 *Agendamento de Sessão*\n\nNossa equipe conta com 11 artistas especialistas. Para verificar a melhor data:\nQual dia da semana ou período você tem preferência? (ex: próxima sexta à tarde, sábado pela manhã)',
    pixPaymentMessage: '🗓️ *Pré-Agendamento Criado!*\n\nBloqueamos seu horário na grade. Para confirmar, efetue o pagamento do sinal de 30% via PIX copia e cola abaixo.',
    humanTransferMessage: '💬 *Transbordo para Atendimento Humano*\n\nUm dos nossos tatuadores foi notificado e já vai assumir seu atendimento por aqui em instantes. Aguarde só um momento!',
    pixConfirmedMessage: '🎉 *Agendamento Confirmado com Sucesso!*\n\nSeu pagamento do sinal foi aprovado pelo estúdio. Nos vemos no Vício Tattoo! ⚡',
    botResetMessage: '🔄 Conversa reiniciada. Como podemos te ajudar agora?',
    absenceMessage: '🌙 *Estúdio Fora do Horário de Expediente*\n\nNosso horário de funcionamento é de Segunda a Sábado, das 10h às 20h.\nSeu atendimento já foi registrado e você pode continuar utilizando nosso assistente virtual para orçamentos e pré-agendamentos normais!',
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

  notifPrefs: NotificationPreferences = {
    notifyNewLead: true,
    notifyPaymentReceived: true,
    notifyHumanRequest: true
  };

  ngOnInit(): void {
    this.loadConfig();
    this.loadNotificationPrefs();
    this.loadBotHealth();
  }

  loadBotHealth(): void {
    this.chatService.getBotStatus().subscribe({
      next: (status) => this.botStatus.set(status),
      error: () => {}
    });
  }

  testWhatsAppConnection(): void {
    this.testingConnection.set(true);
    this.chatService.getBotStatus().subscribe({
      next: (status) => {
        this.testingConnection.set(false);
        this.botStatus.set(status);
        this.showToast(`Conexão WhatsApp (${status.instance}) verificada e operacional!`);
      },
      error: () => {
        this.testingConnection.set(false);
        this.showToast('Falha ao consultar status da conexão WhatsApp.', 'error');
      }
    });
  }

  restartWhatsAppBot(): void {
    this.restartingBot.set(true);
    this.chatService.reconnectBot().subscribe({
      next: (res) => {
        this.restartingBot.set(false);
        this.loadBotHealth();
        this.showToast(res.message || 'Instância do Bot WhatsApp reiniciada com sucesso!');
      },
      error: () => {
        this.restartingBot.set(false);
        this.showToast('Erro ao reiniciar instância do WhatsApp.', 'error');
      }
    });
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
            studioName: data.studioName || '',
            logoBase64: data.logoBase64 || null,
            defaultHourlyRate: data.defaultHourlyRate != null ? Number(data.defaultHourlyRate) : 150.00,
            fixedSetupCost: data.fixedSetupCost != null ? Number(data.fixedSetupCost) : 50.00,
            absorbCreditCardFees: data.absorbCreditCardFees ?? true,
            signalPercentage: data.signalPercentage != null ? Number(data.signalPercentage) : 30,
            minDepositAmount: data.minDepositAmount != null ? Number(data.minDepositAmount) : 50.00,
            pixExpirationMinutes: data.pixExpirationMinutes != null ? Number(data.pixExpirationMinutes) : 120,
            openingTime: data.openingTime || '10:00',
            closingTime: data.closingTime || '20:00',
            workingDays: data.workingDays || 'SEG-SAB',
            welcomeMessage: data.welcomeMessage || this.config.welcomeMessage,
            orcamentoIntroMessage: (data.orcamentoIntroMessage && !data.orcamentoIntroMessage.includes('Tamanho aproximado em centímetros'))
              ? data.orcamentoIntroMessage
              : this.config.orcamentoIntroMessage,
            agendamentoIntroMessage: data.agendamentoIntroMessage || this.config.agendamentoIntroMessage,
            pixPaymentMessage: data.pixPaymentMessage || this.config.pixPaymentMessage,
            humanTransferMessage: data.humanTransferMessage || this.config.humanTransferMessage,
            pixConfirmedMessage: data.pixConfirmedMessage || this.config.pixConfirmedMessage,
            botResetMessage: data.botResetMessage || this.config.botResetMessage,
            absenceMessage: data.absenceMessage || this.config.absenceMessage,
            orcamentoShowGreeting: data.orcamentoShowGreeting ?? true,
            orcamentoShowStyle: data.orcamentoShowStyle ?? true,
            orcamentoShowSize: data.orcamentoShowSize ?? true,
            orcamentoShowDuration: data.orcamentoShowDuration ?? true,
            orcamentoShowComplexity: data.orcamentoShowComplexity ?? true,
            orcamentoShowLocation: data.orcamentoShowLocation ?? true,
            orcamentoShowArtists: data.orcamentoShowArtists ?? true,
            orcamentoShowDescription: data.orcamentoShowDescription ?? true,
            orcamentoShowPrice: data.orcamentoShowPrice ?? true,
            orcamentoShowMaterialsNote: data.orcamentoShowMaterialsNote ?? true,
            orcamentoShowAdjustHint: data.orcamentoShowAdjustHint ?? true
          };
          this.logoPreview.set(data.logoBase64 || null);
          this.savedConfig.set({ ...this.config });
        }
      },
      error: (err) => {
        console.warn('Erro ao carregar configurações do backend. Usando padrão local.', err);
      }
    });
  }

  isFieldChanged(field: keyof TenantConfig): boolean {
    const saved = this.savedConfig();
    if (!saved) return false;
    const currentVal = this.config[field];
    const savedVal = saved[field];

    if (typeof currentVal === 'number' || typeof savedVal === 'number') {
      return Number(currentVal ?? 0) !== Number(savedVal ?? 0);
    }
    return (currentVal ?? '') !== (savedVal ?? '');
  }

  changedFieldsCount(): number {
    return this.trackedFields.filter((f) => this.isFieldChanged(f)).length;
  }

  hasUnsavedChanges(): boolean {
    return this.changedFieldsCount() > 0;
  }

  discardChanges(): void {
    const saved = this.savedConfig();
    if (!saved) return;
    this.config = { ...saved };
    this.logoPreview.set(saved.logoBase64 || null);
    this.showToast('Alterações descartadas.');
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
          minDepositAmount: Number(res.minDepositAmount),
          pixExpirationMinutes: Number(res.pixExpirationMinutes) || 120
        };
        this.logoPreview.set(this.config.logoBase64 || null);
        this.savedConfig.set({ ...this.config });
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
    this.config.orcamentoIntroMessage = '📸 *Envio de Referência para Orçamento*\n\nPor favor, envie uma *foto da tatuagem* (ou referência visual) que você deseja fazer, informe o local do corpo e escolha uma das nossas *opções de tamanho*:\n\n📏 *Opções de Tamanho da Tatuagem:*\n• Digite *A* (ou *Flash*) — *Flash / Mini* (até 5 cm)\n• Digite *B* (ou *Pequena*) — *Pequena* (6 a 10 cm)\n• Digite *C* (ou *Média*) — *Média* (11 a 18 cm)\n• Digite *D* (ou *Grande*) — *Grande* (19 a 25 cm)\n• Digite *E* (ou *Fechamento*) — *Fechamento* (26 cm+ / Projeto Amplo — _Valor a combinar_)';
    this.config.agendamentoIntroMessage = '📅 *Agendamento de Sessão*\n\nNossa equipe conta com 11 artistas especialistas. Para verificar a melhor data:\nQual dia da semana ou período você tem preferência? (ex: próxima sexta à tarde, sábado pela manhã)';
    this.config.pixPaymentMessage = '🗓️ *Pré-Agendamento Criado!*\n\nBloqueamos seu horário na grade. Para confirmar, efetue o pagamento do sinal de 30% via PIX copia e cola abaixo.';
    this.config.humanTransferMessage = '💬 *Transbordo para Atendimento Humano*\n\nUm dos nossos tatuadores foi notificado e já vai assumir seu atendimento por aqui em instantes. Aguarde só um momento!';
    this.config.pixConfirmedMessage = '🎉 *Agendamento Confirmado com Sucesso!*\n\nSeu pagamento do sinal foi aprovado pelo estúdio. Nos vemos no Vício Tattoo! ⚡';
    this.config.botResetMessage = '🔄 Conversa reiniciada. Como podemos te ajudar agora?';
    this.config.absenceMessage = '🌙 *Estúdio Fora do Horário de Expediente*\n\nNosso horário de funcionamento é de Segunda a Sábado, das 10h às 20h.\nSeu atendimento já foi registrado e você pode continuar utilizando nosso assistente virtual para orçamentos e pré-agendamentos normais!';
    this.showToast('Textos restaurados para os padrões sugeridos. Clique em "Salvar" para confirmar.');
  }

  buildQuotePreviewText(): string {
    const blocks: string[] = ['🎨 *Orçamento Estimado com Inteligência Artificial* 🎨'];

    if (this.config.orcamentoShowGreeting !== false) {
      blocks.push('Olá, Cliente! Analisamos os detalhes da sua arte de referência:');
    }

    const metrics: string[] = [];
    if (this.config.orcamentoShowStyle !== false) {
      metrics.push('📌 *Estilo:* Old School / Tradicional');
    }
    if (this.config.orcamentoShowSize !== false) {
      metrics.push('📏 *Tamanho Estimado:* Média (11 a 18 cm)');
    }
    if (this.config.orcamentoShowDuration !== false) {
      metrics.push('⏱️ *Tempo Estimado:* ~2,5h de agulha');
    }
    if (this.config.orcamentoShowComplexity !== false) {
      metrics.push('🔍 *Complexidade:* MEDIA');
    }
    if (this.config.orcamentoShowLocation !== false) {
      metrics.push('📍 *Local Detectado:* Antebraço _(Sugestões: Panturrilha)_');
    }
    if (this.config.orcamentoShowArtists !== false) {
      metrics.push('👨‍🎨 *Tatuador Especialista:* Rodrigo Old School');
    }
    if (metrics.length > 0) {
      blocks.push(metrics.join('\n'));
    }

    if (this.config.orcamentoShowDescription !== false) {
      blocks.push('📝 *Sobre a sua arte:*\nPantera clássica tradicional com traços firmes e cores vibrantes de longa duração.');
    }

    const hourly = Number(this.config.defaultHourlyRate || 150);
    const setup = Number(this.config.fixedSetupCost || 50);
    const base = Math.round(2.5 * hourly * 1.2 + setup);
    const min = Math.round(base * 0.9);
    const max = Math.round(base * 1.15);

    if (this.config.orcamentoShowPrice !== false) {
      let priceBlock = `💰 *Faixa de Preço Estimada:*\n*R$ ${min} a R$ ${max}*\n⚠️ _Estimativa preliminar calculada por IA — o valor final será confirmado pelo tatuador._`;
      if (this.config.orcamentoShowMaterialsNote !== false) {
        priceBlock += '\n_(Inclui materiais 100% descartáveis, esterilizados e biossegurança)_';
      }
      blocks.push(priceBlock);
    } else if (this.config.orcamentoShowMaterialsNote !== false) {
      blocks.push('_(Inclui materiais 100% descartáveis, esterilizados e biossegurança)_');
    }

    if (this.config.orcamentoShowAdjustHint !== false) {
      blocks.push('💡 _Quer ajustar a estimativa? Informe a medida em cm (ex: 10cm) ou "fechamento"._');
    }

    blocks.push(
      '━━━━━━━━━━━━━━━━━━━━\n' +
      '1️⃣ *Agendar sessão*\n' +
      '2️⃣ *Falar com o tatuador*\n' +
      '3️⃣ *Voltar ao menu*\n\n' +
      '👉 _Digite o número da opção desejada._'
    );

    return blocks.join('\n\n');
  }

  private showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => this.feedbackMessage.set(null), 5000);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    const reader = new FileReader();

    reader.onload = () => {
      const originalBase64 = reader.result as string;

      this.imageCompress
        .compressFile(originalBase64, DOC_ORIENTATION.NotDefined, 100, 75, 800, 800)
        .then((compressedBase64: string) => {
          const sizeInBytes = this.imageCompress.byteCount(compressedBase64);

          if (sizeInBytes > 400 * 1024) {
            this.showToast('Mesmo após a compressão, a imagem passou de 400 KB.', 'error');
            input.value = ''; // Permite selecionar outro arquivo (ou tentar novamente)
            return;
          }

          this.logoPreview.set(compressedBase64);
          this.config.logoBase64 = compressedBase64;
          input.value = '';
        });
    };

    reader.readAsDataURL(file);
  }
}
