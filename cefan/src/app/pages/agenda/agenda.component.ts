import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AppointmentService } from '../../core/services/appointment.service';
import { Appointment, AppointmentStatus, CreateAppointmentRequest, UpdateAppointmentRequest } from '../../core/models/appointment.model';

@Component({
  selector: 'app-agenda',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <!-- Page Header -->
      <div class="page-header">
        <div>
          <h1 class="page-title">Agenda & Atendimentos</h1>
          <p class="page-subtitle">Gerencie as sessões de tatuagem integradas com o WhatsApp e Mercado Pago</p>
        </div>
        <div class="header-actions">
          <button class="btn btn-secondary" (click)="loadAppointments()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
            </svg>
            Atualizar
          </button>
          <button class="btn btn-primary" (click)="openCreateModal()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            Novo Agendamento
          </button>
        </div>
      </div>

      <!-- Metrics Summary Bar -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-icon total-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
          </div>
          <div class="metric-data">
            <span class="metric-value">{{ appointments().length }}</span>
            <span class="metric-label">Total Agendamentos</span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon confirmed-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
          </div>
          <div class="metric-data">
            <span class="metric-value">{{ confirmedCount() }}</span>
            <span class="metric-label">Confirmados (Sinal Pago)</span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon pending-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
          </div>
          <div class="metric-data">
            <span class="metric-value">{{ pendingCount() }}</span>
            <span class="metric-label">Aguardando PIX</span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon revenue-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="1" x2="12" y2="23"></line>
              <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
            </svg>
          </div>
          <div class="metric-data">
            <span class="metric-value">R$ {{ confirmedRevenue() | number:'1.2-2' }}</span>
            <span class="metric-label">Total em Sinal (30%)</span>
          </div>
        </div>
      </div>

      <!-- Toast Feedback -->
      <div *ngIf="feedbackMessage()" class="alert-banner" [ngClass]="feedbackType()">
        <span>{{ feedbackMessage() }}</span>
        <button class="alert-close" (click)="feedbackMessage.set(null)">×</button>
      </div>

      <!-- Filters & Search -->
      <div class="controls-card">
        <div class="search-box">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <input
            type="text"
            placeholder="Buscar por cliente ou telefone..."
            [ngModel]="searchQuery()"
            (ngModelChange)="searchQuery.set($event)"
          />
        </div>

        <div class="status-filters">
          <button
            class="filter-chip"
            [class.active]="selectedStatus() === 'ALL'"
            (click)="selectedStatus.set('ALL')">
            Todos ({{ appointments().length }})
          </button>
          <button
            class="filter-chip"
            [class.active]="selectedStatus() === 'CONFIRMADO'"
            (click)="selectedStatus.set('CONFIRMADO')">
            Confirmados
          </button>
          <button
            class="filter-chip"
            [class.active]="selectedStatus() === 'AGUARDANDO_PAGAMENTO'"
            (click)="selectedStatus.set('AGUARDANDO_PAGAMENTO')">
            Aguardando PIX
          </button>
          <button
            class="filter-chip"
            [class.active]="selectedStatus() === 'EXPIRADO'"
            (click)="selectedStatus.set('EXPIRADO')">
            Expirados
          </button>
          <button
            class="filter-chip"
            [class.active]="selectedStatus() === 'CANCELADO'"
            (click)="selectedStatus.set('CANCELADO')">
            Cancelados
          </button>
        </div>
      </div>

      <!-- Loading / Empty / Appointments List -->
      <div *ngIf="loading()" class="state-container">
        <div class="spinner"></div>
        <p>Carregando agendamentos...</p>
      </div>

      <div *ngIf="!loading() && filteredAppointments().length === 0" class="state-container">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
          <line x1="16" y1="2" x2="16" y2="6"></line>
          <line x1="8" y1="2" x2="8" y2="6"></line>
          <line x1="3" y1="10" x2="21" y2="10"></line>
        </svg>
        <h3>Nenhum agendamento encontrado</h3>
        <p>Não há sessões com os critérios selecionados.</p>
      </div>

      <div *ngIf="!loading() && filteredAppointments().length > 0" class="appointments-table-container">
        <table class="appointments-table">
          <thead>
            <tr>
              <th>Cliente</th>
              <th>Data & Horário</th>
              <th>Status</th>
              <th>Valores</th>
              <th>Tipo</th>
              <th class="text-right">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let apt of filteredAppointments()">
              <td>
                <div class="customer-info">
                  <span class="customer-name">{{ apt.customerName }}</span>
                  <a [href]="'https://wa.me/' + apt.customerPhone" target="_blank" class="customer-phone">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                    </svg>
                    {{ apt.customerPhone }}
                  </a>
                </div>
              </td>
              <td>
                <div class="date-info">
                  <span class="date-main">{{ formatDate(apt.scheduledStart) }}</span>
                  <span class="time-range">{{ formatTimeRange(apt.scheduledStart, apt.scheduledEnd) }}</span>
                </div>
              </td>
              <td>
                <span [ngClass]="getStatusBadgeClass(apt.status)">
                  <span class="dot"></span>
                  {{ getStatusLabel(apt.status) }}
                </span>
              </td>
              <td>
                <div class="pricing-info">
                  <span class="total-price">Total: R$ {{ (apt.totalPrice || 0) | number:'1.2-2' }}</span>
                  <span class="deposit-price" *ngIf="apt.depositAmount > 0">Sinal: R$ {{ (apt.depositAmount || 0) | number:'1.2-2' }}</span>
                </div>
              </td>
              <td>
                <span *ngIf="apt.isTouchup" class="badge badge-info">Retoque (R$ 0,00)</span>
                <span *ngIf="!apt.isTouchup" class="badge-regular">Tatuagem Normal</span>
              </td>
              <td class="text-right">
                <div class="action-buttons">
                  <button
                    *ngIf="apt.status === 'AGUARDANDO_PAGAMENTO'"
                    class="btn-action btn-pix"
                    title="Ver QR Code / PIX"
                    (click)="openPixModal(apt)">
                    PIX
                  </button>

                  <button
                    class="btn-action btn-edit"
                    title="Reajustar Valor / Sessão"
                    (click)="openEditModal(apt)">
                    Reajustar
                  </button>

                  <button
                    *ngIf="apt.status !== 'CANCELADO' && apt.status !== 'EXPIRADO'"
                    class="btn-action btn-cancel"
                    title="Cancelar Agendamento"
                    (click)="cancelAppointment(apt)">
                    Cancelar
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- MODAL: Reajuste de Valor com Auditoria Obrigatória -->
      <div *ngIf="showEditModal()" class="modal-backdrop">
        <div class="modal-content">
          <div class="modal-header">
            <h3>Reajustar Valor / Sessão</h3>
            <button class="close-btn" (click)="showEditModal.set(false)">×</button>
          </div>
          <div class="modal-body" *ngIf="selectedAppointment() as apt">
            <p class="modal-intro">
              Cliente: <strong>{{ apt.customerName }}</strong>
              <br>Valor atual cadastrado: <strong>R$ {{ apt.totalPrice | number:'1.2-2' }}</strong>
            </p>

            <div class="input-group">
              <label>Novo Valor Total da Tatuagem (R$)</label>
              <input type="number" step="10.00" [(ngModel)]="editForm.newPrice" placeholder="Ex: 450.00" />
            </div>

            <div class="input-group">
              <label>Justificativa da Alteração (Obrigatório para Auditoria)</label>
              <textarea
                rows="3"
                [(ngModel)]="editForm.reason"
                placeholder="Ex: Cliente solicitou aumento das dimensões do desenho no início da sessão"
              ></textarea>
              <span class="hint">Essa justificativa é gravada permanentemente no log de auditoria fiscal e contábil do estúdio.</span>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showEditModal.set(false)">Fechar</button>
            <button
              class="btn btn-primary"
              [disabled]="saving() || !editForm.reason || editForm.newPrice == null"
              (click)="saveAppointmentEdit()">
              {{ saving() ? 'Salvando...' : 'Confirmar Reajuste' }}
            </button>
          </div>
        </div>
      </div>

      <!-- MODAL: Novo Agendamento -->
      <div *ngIf="showCreateModal()" class="modal-backdrop">
        <div class="modal-content">
          <div class="modal-header">
            <h3>Novo Agendamento</h3>
            <button class="close-btn" (click)="showCreateModal.set(false)">×</button>
          </div>
          <div class="modal-body">
            <div class="input-group">
              <label>Nome do Cliente *</label>
              <input type="text" [(ngModel)]="createForm.customerName" placeholder="Ex: João da Silva" />
            </div>

            <div class="input-group">
              <label>Telefone / WhatsApp (com DDD) *</label>
              <input type="text" [(ngModel)]="createForm.customerPhone" placeholder="Ex: 5511999998888" />
            </div>

            <div class="form-row">
              <div class="input-group">
                <label>Data & Início *</label>
                <input type="datetime-local" [(ngModel)]="createForm.scheduledStart" />
              </div>
              <div class="input-group">
                <label>Término Estimado *</label>
                <input type="datetime-local" [(ngModel)]="createForm.scheduledEnd" />
              </div>
            </div>

            <div class="checkbox-group">
              <label class="checkbox-label">
                <input type="checkbox" [(ngModel)]="createForm.isTouchup" (change)="onTouchupToggled()" />
                <span>É sessão de <strong>Retoque Gratuito</strong>? (Regra: R$ 0,00)</span>
              </label>
            </div>

            <div class="form-row" *ngIf="!createForm.isTouchup">
              <div class="input-group">
                <label>Valor Total Estimado (R$)</label>
                <input type="number" step="10" [(ngModel)]="createForm.totalPrice" (input)="onTotalPriceChanged()" />
              </div>
              <div class="input-group">
                <label>Sinal Calculado (30% - R$)</label>
                <input type="number" step="5" [(ngModel)]="createForm.depositAmount" />
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showCreateModal.set(false)">Cancelar</button>
            <button
              class="btn btn-primary"
              [disabled]="saving() || !createForm.customerName || !createForm.customerPhone || !createForm.scheduledStart"
              (click)="saveNewAppointment()">
              {{ saving() ? 'Cadastrando...' : 'Salvar Agendamento' }}
            </button>
          </div>
        </div>
      </div>

      <!-- MODAL: PIX / Pagamento -->
      <div *ngIf="showPixModal()" class="modal-backdrop">
        <div class="modal-content">
          <div class="modal-header">
            <h3>Cobrança PIX Mercado Pago</h3>
            <button class="close-btn" (click)="showPixModal.set(false)">×</button>
          </div>
          <div class="modal-body" *ngIf="selectedAppointment() as apt">
            <p class="modal-intro">
              Sinal pendente para <strong>{{ apt.customerName }}</strong>:
              <br><strong class="pix-amount">R$ {{ (apt.depositAmount || 0) | number:'1.2-2' }}</strong>
            </p>

            <div *ngIf="apt.mpPixCopyPaste" class="input-group">
              <label>Código PIX Copia e Cola:</label>
              <textarea rows="3" readonly [value]="apt.mpPixCopyPaste" class="code-area"></textarea>
              <button class="btn btn-secondary btn-sm" (click)="copyPixCode(apt.mpPixCopyPaste)">
                Copiar Código PIX
              </button>
            </div>

            <div *ngIf="apt.mpPaymentLink" class="payment-link-box">
              <a [href]="apt.mpPaymentLink" target="_blank" class="btn btn-primary">
                Abrir Link Mercado Pago
              </a>
            </div>

            <div *ngIf="!apt.mpPixCopyPaste && !apt.mpPaymentLink" class="empty-pix">
              <p>Nenhuma chave PIX ativa para este agendamento ou pagamento gerado offline.</p>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showPixModal.set(false)">Fechar</button>
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
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
      flex-wrap: wrap;
      gap: 1rem;
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

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    /* Metrics Grid */
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 1.25rem;
      margin-bottom: 2rem;
    }

    .metric-card {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 1.25rem;
      display: flex;
      align-items: center;
      gap: 1.25rem;
      box-shadow: var(--shadow-sm);
    }

    .metric-icon {
      width: 48px;
      height: 48px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;

      &.total-icon {
        background-color: rgba(99, 102, 241, 0.15);
        color: #818cf8;
      }
      &.confirmed-icon {
        background-color: var(--color-success-bg);
        color: var(--color-success);
      }
      &.pending-icon {
        background-color: var(--color-warning-bg);
        color: var(--color-warning);
      }
      &.revenue-icon {
        background-color: var(--color-info-bg);
        color: var(--color-info);
      }
    }

    .metric-data {
      display: flex;
      flex-direction: column;
    }

    .metric-value {
      font-size: 1.35rem;
      font-weight: 800;
      color: var(--text-primary);
    }

    .metric-label {
      font-size: 0.8rem;
      font-weight: 500;
      color: var(--text-muted);
    }

    /* Alert Banner */
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

    /* Controls & Filter Bar */
    .controls-card {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 1.25rem;
      margin-bottom: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;

      @media (min-width: 768px) {
        flex-direction: row;
        align-items: center;
        justify-content: space-between;
      }
    }

    .search-box {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 0.6rem 1rem;
      color: var(--text-secondary);
      flex: 1;
      max-width: 400px;

      input {
        background: none;
        border: none;
        color: var(--text-primary);
        font-family: inherit;
        font-size: 0.875rem;
        outline: none;
        width: 100%;
      }
    }

    .status-filters {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }

    .filter-chip {
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0.45rem 0.85rem;
      border-radius: var(--radius-full);
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;

      &:hover {
        border-color: var(--border-light);
        color: var(--text-primary);
      }

      &.active {
        background-color: var(--accent-primary);
        color: #ffffff;
        border-color: var(--accent-primary);
      }
    }

    /* Table Container */
    .appointments-table-container {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      overflow-x: auto;
      box-shadow: var(--shadow-md);
    }

    .appointments-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.875rem;

      th {
        background-color: var(--bg-surface);
        padding: 1rem 1.25rem;
        font-size: 0.75rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        font-weight: 700;
        color: var(--text-secondary);
        border-bottom: 1px solid var(--border-color);
      }

      td {
        padding: 1rem 1.25rem;
        border-bottom: 1px solid var(--border-color);
        color: var(--text-primary);
      }

      tr:last-child td {
        border-bottom: none;
      }

      tr:hover td {
        background-color: var(--bg-card-hover);
      }
    }

    .text-right {
      text-align: right;
    }

    .customer-info {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .customer-name {
      font-weight: 700;
      color: var(--text-primary);
    }

    .customer-phone {
      font-size: 0.775rem;
      color: var(--color-success);
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }

    .date-info {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }

    .date-main {
      font-weight: 600;
    }

    .time-range {
      font-size: 0.775rem;
      color: var(--text-muted);
    }

    .pricing-info {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }

    .total-price {
      font-weight: 700;
      color: var(--text-primary);
    }

    .deposit-price {
      font-size: 0.775rem;
      color: var(--color-warning);
    }

    .badge-regular {
      font-size: 0.75rem;
      color: var(--text-muted);
    }

    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      display: inline-block;
      background-color: currentColor;
    }

    .action-buttons {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn-action {
      padding: 0.4rem 0.75rem;
      border-radius: var(--radius-sm);
      font-size: 0.75rem;
      font-weight: 700;
      cursor: pointer;
      border: 1px solid transparent;
      transition: all 0.2s;

      &.btn-edit {
        background-color: rgba(99, 102, 241, 0.12);
        color: #818cf8;
        border-color: rgba(99, 102, 241, 0.25);
        &:hover { background-color: var(--accent-primary); color: white; }
      }

      &.btn-pix {
        background-color: var(--color-warning-bg);
        color: var(--color-warning);
        border-color: rgba(245, 158, 11, 0.25);
        &:hover { background-color: var(--color-warning); color: #000; }
      }

      &.btn-cancel {
        background-color: var(--color-danger-bg);
        color: var(--color-danger);
        border-color: rgba(239, 68, 68, 0.25);
        &:hover { background-color: var(--color-danger); color: white; }
      }
    }

    .state-container {
      text-align: center;
      padding: 4rem 2rem;
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1rem;
      color: var(--text-secondary);
    }

    .spinner {
      width: 36px;
      height: 36px;
      border: 3px solid var(--border-color);
      border-top-color: var(--accent-primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* Modal Form specifics */
    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    .checkbox-group {
      margin-bottom: 1.25rem;
    }

    .checkbox-label {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      font-size: 0.875rem;
      cursor: pointer;
      color: var(--text-primary);

      input {
        width: 18px;
        height: 18px;
        accent-color: var(--accent-primary);
      }
    }

    .modal-intro {
      font-size: 0.9rem;
      color: var(--text-secondary);
      margin-bottom: 1.25rem;
      line-height: 1.6;
    }

    .pix-amount {
      font-size: 1.2rem;
      color: var(--color-warning);
    }

    .code-area {
      font-family: monospace;
      font-size: 0.8rem;
      word-break: break-all;
    }

    .payment-link-box {
      margin-top: 1rem;
      text-align: center;
    }
  `]
})
export class AgendaComponent implements OnInit {
  private readonly appointmentService = inject(AppointmentService);

  readonly appointments = signal<Appointment[]>([]);
  readonly loading = signal<boolean>(false);
  readonly saving = signal<boolean>(false);
  readonly searchQuery = signal<string>('');
  readonly selectedStatus = signal<string>('ALL');

  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');

  // Modals state
  readonly showEditModal = signal<boolean>(false);
  readonly showCreateModal = signal<boolean>(false);
  readonly showPixModal = signal<boolean>(false);
  readonly selectedAppointment = signal<Appointment | null>(null);

  editForm = {
    newPrice: 0,
    reason: ''
  };

  createForm = {
    customerName: '',
    customerPhone: '',
    scheduledStart: '',
    scheduledEnd: '',
    isTouchup: false,
    totalPrice: 350,
    depositAmount: 105
  };

  // Computed metrics
  readonly confirmedCount = computed(() =>
    this.appointments().filter(a => a.status === 'CONFIRMADO').length
  );

  readonly pendingCount = computed(() =>
    this.appointments().filter(a => a.status === 'AGUARDANDO_PAGAMENTO').length
  );

  readonly confirmedRevenue = computed(() =>
    this.appointments()
      .filter(a => a.status === 'CONFIRMADO')
      .reduce((sum, a) => sum + (Number(a.depositAmount) || 0), 0)
  );

  readonly filteredAppointments = computed(() => {
    let list = this.appointments();

    if (this.selectedStatus() !== 'ALL') {
      list = list.filter(a => a.status === this.selectedStatus());
    }

    const query = this.searchQuery().toLowerCase().trim();
    if (query) {
      list = list.filter(a =>
        a.customerName.toLowerCase().includes(query) ||
        a.customerPhone.includes(query)
      );
    }

    return list;
  });

  ngOnInit(): void {
    this.loadAppointments();
  }

  loadAppointments(): void {
    this.loading.set(true);
    this.appointmentService.getAppointments().subscribe({
      next: (data) => {
        this.appointments.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erro ao carregar agendamentos:', err);
        this.showToast('Erro ao carregar agendamentos do backend.', 'error');
        this.loading.set(false);
      }
    });
  }

  openEditModal(apt: Appointment): void {
    this.selectedAppointment.set(apt);
    this.editForm = {
      newPrice: apt.totalPrice,
      reason: ''
    };
    this.showEditModal.set(true);
  }

  saveAppointmentEdit(): void {
    const apt = this.selectedAppointment();
    if (!apt) return;

    if (!this.editForm.reason || this.editForm.reason.trim() === '') {
      this.showToast('A justificativa da alteração é obrigatória para auditoria!', 'error');
      return;
    }

    this.saving.set(true);
    const updateReq: UpdateAppointmentRequest = {
      newPrice: Number(this.editForm.newPrice),
      reasonForChange: this.editForm.reason
    };

    this.appointmentService.update(apt.id, updateReq).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.showEditModal.set(false);
        this.showToast(`Valor atualizado com sucesso! Log de auditoria registrado.`);
        this.loadAppointments();
      },
      error: (err) => {
        this.saving.set(false);
        this.showToast(err.error?.message || 'Erro ao atualizar agendamento.', 'error');
      }
    });
  }

  openCreateModal(): void {
    const now = new Date();
    now.setHours(now.getHours() + 2, 0, 0, 0);
    const end = new Date(now.getTime() + 2 * 60 * 60 * 1000);

    const pad = (n: number) => n < 10 ? '0' + n : n;
    const formatLocal = (d: Date) =>
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;

    this.createForm = {
      customerName: '',
      customerPhone: '5511',
      scheduledStart: formatLocal(now),
      scheduledEnd: formatLocal(end),
      isTouchup: false,
      totalPrice: 350,
      depositAmount: 105
    };
    this.showCreateModal.set(true);
  }

  onTouchupToggled(): void {
    if (this.createForm.isTouchup) {
      this.createForm.totalPrice = 0;
      this.createForm.depositAmount = 0;
    } else {
      this.createForm.totalPrice = 350;
      this.createForm.depositAmount = 105;
    }
  }

  onTotalPriceChanged(): void {
    if (!this.createForm.isTouchup) {
      this.createForm.depositAmount = Math.round(this.createForm.totalPrice * 0.3);
    }
  }

  saveNewAppointment(): void {
    this.saving.set(true);
    const req: CreateAppointmentRequest = {
      customerName: this.createForm.customerName,
      customerPhone: this.createForm.customerPhone,
      scheduledStart: new Date(this.createForm.scheduledStart).toISOString(),
      scheduledEnd: new Date(this.createForm.scheduledEnd).toISOString(),
      isTouchup: this.createForm.isTouchup,
      totalPrice: this.createForm.isTouchup ? 0 : this.createForm.totalPrice,
      depositAmount: this.createForm.isTouchup ? 0 : this.createForm.depositAmount
    };

    this.appointmentService.create(req).subscribe({
      next: () => {
        this.saving.set(false);
        this.showCreateModal.set(false);
        this.showToast('Agendamento criado com sucesso!');
        this.loadAppointments();
      },
      error: (err) => {
        this.saving.set(false);
        this.showToast(err.error?.message || 'Erro ao criar agendamento.', 'error');
      }
    });
  }

  cancelAppointment(apt: Appointment): void {
    if (confirm(`Tem certeza que deseja cancelar o agendamento de ${apt.customerName}?`)) {
      this.appointmentService.cancel(apt.id).subscribe({
        next: () => {
          this.showToast('Agendamento cancelado.');
          this.loadAppointments();
        },
        error: (err) => {
          this.showToast('Erro ao cancelar agendamento.', 'error');
        }
      });
    }
  }

  openPixModal(apt: Appointment): void {
    this.selectedAppointment.set(apt);
    this.showPixModal.set(true);
  }

  copyPixCode(code?: string): void {
    if (code) {
      navigator.clipboard.writeText(code);
      this.showToast('Código PIX copiado para a área de transferência!');
    }
  }

  showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => {
      this.feedbackMessage.set(null);
    }, 5000);
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  formatTimeRange(startStr: string, endStr: string): string {
    if (!startStr) return '';
    const s = new Date(startStr);
    const startTime = s.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    if (!endStr) return startTime;
    const e = new Date(endStr);
    const endTime = e.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    return `${startTime} - ${endTime}`;
  }

  getStatusBadgeClass(status: AppointmentStatus): string {
    switch (status) {
      case 'CONFIRMADO': return 'badge badge-success';
      case 'AGUARDANDO_PAGAMENTO': return 'badge badge-warning';
      case 'CANCELADO': return 'badge badge-danger';
      case 'EXPIRADO': return 'badge badge-secondary';
      default: return 'badge';
    }
  }

  getStatusLabel(status: AppointmentStatus): string {
    switch (status) {
      case 'CONFIRMADO': return 'Confirmado';
      case 'AGUARDANDO_PAGAMENTO': return 'Aguardando PIX';
      case 'CANCELADO': return 'Cancelado';
      case 'EXPIRADO': return 'Expirado';
      default: return status;
    }
  }
}
