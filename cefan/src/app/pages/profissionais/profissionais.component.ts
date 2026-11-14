import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProfessionalService } from '../../core/services/professional.service';
import { Professional, RateType } from '../../core/models/professional.model';

@Component({
  selector: 'app-profissionais',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Equipe de Profissionais</h1>
          <p class="page-subtitle">
            Gestão dos 11 tatuadores e body piercers do Vício Tattoo, especialidades, reputação e comissões individuais
          </p>
        </div>
        <div class="header-actions">
          <button class="btn btn-primary" (click)="openCreateModal()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <span>Novo Profissional</span>
          </button>
        </div>
      </div>

      <!-- Feedback Banner -->
      <div *ngIf="feedbackMessage()" class="alert-banner" [ngClass]="feedbackType()">
        <span>{{ feedbackMessage() }}</span>
        <button class="alert-close" (click)="feedbackMessage.set(null)">×</button>
      </div>

      <!-- Quick Metrics Bar -->
      <div class="metrics-grid">
        <div class="metric-card">
          <span class="metric-label">Total de Profissionais</span>
          <span class="metric-value">{{ professionals().length }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">Profissionais Ativos</span>
          <span class="metric-value text-success">{{ activeCount() }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">Tatuadores Especialistas</span>
          <span class="metric-value text-accent">{{ tattooArtistsCount() }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">Body Piercers</span>
          <span class="metric-value text-info">{{ piercersCount() }}</span>
        </div>
      </div>

      <!-- Filter Controls -->
      <div class="filter-bar">
        <div class="search-input">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <input
            type="text"
            [(ngModel)]="searchQuery"
            placeholder="Buscar por nome, especialidade ou estilo..."
          />
        </div>

        <div class="filter-tabs">
          <button
            class="tab-btn"
            [class.active]="filterStatus() === 'ACTIVE'"
            (click)="filterStatus.set('ACTIVE')">
            Ativos ({{ activeCount() }})
          </button>
          <button
            class="tab-btn"
            [class.active]="filterStatus() === 'ALL'"
            (click)="filterStatus.set('ALL')">
            Todos ({{ professionals().length }})
          </button>
        </div>
      </div>

      <!-- Loading State -->
      <div *ngIf="loading()" class="loading-state">
        <div class="spinner"></div>
        <p>Carregando equipe de profissionais...</p>
      </div>

      <!-- Professionals Grid -->
      <div *ngIf="!loading() && filteredProfessionals().length > 0" class="professionals-grid">
        <div
          *ngFor="let prof of filteredProfessionals()"
          class="pro-card"
          [class.inactive]="!prof.isActive">
          <div class="pro-header">
            <div class="pro-avatar" [style.background]="getAvatarColor(prof.name)">
              {{ getInitials(prof.name) }}
            </div>
            <div class="pro-meta">
              <div class="pro-name-row">
                <h3>{{ prof.name }}</h3>
                <span class="status-pill" [class.active]="prof.isActive">
                  {{ prof.isActive ? 'Ativo' : 'Inativo' }}
                </span>
              </div>
              <span class="role-badge" [class.piercer]="isPiercer(prof)">
                {{ isPiercer(prof) ? 'Body Piercer' : 'Tatuador(a)' }}
              </span>
            </div>
          </div>

          <div class="pro-body">
            <div class="specialties-container">
              <span class="field-label">Estilos / Especialidades:</span>
              <div class="specialties-badges">
                <span
                  *ngFor="let spec of parseSpecialties(prof.specialties)"
                  class="spec-badge">
                  {{ spec }}
                </span>
              </div>
            </div>

            <div class="stats-row">
              <div class="stat-item">
                <span class="stat-label">Reputação</span>
                <span class="stat-value reputation">★ {{ prof.reputation | number:'1.1-1' }}</span>
              </div>

              <div class="stat-item">
                <span class="stat-label">Taxa / Comissão</span>
                <span class="stat-value rate">
                  <ng-container *ngIf="prof.artistRateType === 'PERCENTAGE'">
                    {{ prof.artistRateValue }}% comissão
                  </ng-container>
                  <ng-container *ngIf="prof.artistRateType === 'HOURLY'">
                    R$ {{ prof.artistRateValue | number:'1.2-2' }}/h
                  </ng-container>
                </span>
              </div>
            </div>

            <div class="contact-info">
              <div class="contact-line">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                  <polyline points="22,6 12,13 2,6"></polyline>
                </svg>
                <span>{{ prof.email }}</span>
              </div>
              <div class="contact-line" *ngIf="prof.phone">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                </svg>
                <span>{{ prof.phone }}</span>
              </div>
            </div>
          </div>

          <div class="pro-actions">
            <button class="btn btn-secondary btn-sm" (click)="openEditModal(prof)">
              Editar Perfil & Taxas
            </button>
            <button
              class="btn btn-sm"
              [ngClass]="prof.isActive ? 'btn-danger-outline' : 'btn-success-outline'"
              (click)="toggleStatus(prof)">
              {{ prof.isActive ? 'Desativar' : 'Ativar' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div *ngIf="!loading() && filteredProfessionals().length === 0" class="empty-state">
        <p>Nenhum profissional encontrado para os filtros selecionados.</p>
      </div>

      <!-- Modal: Criar / Editar Profissional -->
      <div class="modal-backdrop" *ngIf="showModal()" (click)="closeModal()">
        <div class="modal-content" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ isEditing() ? 'Editar Profissional' : 'Novo Profissional' }}</h3>
            <button class="close-btn" (click)="closeModal()">×</button>
          </div>

          <div class="modal-body">
            <div class="form-grid">
              <div class="input-group">
                <label>Nome Completo *</label>
                <input type="text" [(ngModel)]="currentForm.name" placeholder="Ex: Carlos Eduardo" />
              </div>

              <div class="input-group">
                <label>E-mail de Login *</label>
                <input
                  type="email"
                  [(ngModel)]="currentForm.email"
                  [disabled]="isEditing()"
                  placeholder="carlos@viciotattoo.com"
                />
              </div>

              <div class="input-group">
                <label>Senha de Acesso {{ isEditing() ? '(Deixe em branco para manter)' : '*' }}</label>
                <input
                  type="password"
                  [(ngModel)]="currentForm.password"
                  placeholder="••••••••"
                />
              </div>

              <div class="input-group">
                <label>Telefone / WhatsApp</label>
                <input type="text" [(ngModel)]="currentForm.phone" placeholder="5511988880001" />
              </div>

              <div class="input-group full-width">
                <label>Especialidades & Estilos Dominantes *</label>
                <input
                  type="text"
                  [(ngModel)]="currentForm.specialties"
                  placeholder="Ex: Fineline, Traço Fino, Minimalista"
                />
                <span class="hint">Usado pela IA do WhatsApp para recomendar o artista ao cliente pelo estilo da foto.</span>
              </div>

              <div class="input-group">
                <label>Modalidade de Taxa / Comissão</label>
                <select [(ngModel)]="currentForm.artistRateType">
                  <option value="PERCENTAGE">Percentual de Comissão (%)</option>
                  <option value="HOURLY">Valor Fixo por Hora (R$)</option>
                </select>
              </div>

              <div class="input-group">
                <label>{{ currentForm.artistRateType === 'PERCENTAGE' ? 'Percentual (%)' : 'Valor Hora (R$)' }} *</label>
                <input type="number" step="0.5" [(ngModel)]="currentForm.artistRateValue" />
              </div>

              <div class="input-group">
                <label>Reputação Inicial (0.0 a 5.0)</label>
                <input type="number" step="0.1" min="1" max="5" [(ngModel)]="currentForm.reputation" />
              </div>

              <div class="input-group">
                <label>Status do Profissional</label>
                <select [(ngModel)]="currentForm.isActive">
                  <option [ngValue]="true">Ativo na Agenda</option>
                  <option [ngValue]="false">Inativo / Afastado</option>
                </select>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="closeModal()">Cancelar</button>
            <button
              class="btn btn-primary"
              [disabled]="saving() || !currentForm.name || !currentForm.email"
              (click)="saveProfessional()">
              {{ saving() ? 'Salvando...' : 'Salvar Profissional' }}
            </button>
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
      color: var(--text-primary);
      letter-spacing: -0.03em;
    }

    .page-subtitle {
      font-size: 0.925rem;
      color: var(--text-secondary);
      margin-top: 0.25rem;
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-bottom: 2rem;
    }

    .metric-card {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }

    .metric-label {
      font-size: 0.8rem;
      color: var(--text-secondary);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .metric-value {
      font-size: 1.75rem;
      font-weight: 800;
      color: var(--text-primary);
      line-height: 1;

      &.text-success { color: var(--color-success); }
      &.text-accent { color: var(--accent-primary); }
      &.text-info { color: var(--color-info); }
    }

    .filter-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      margin-bottom: 1.5rem;
      flex-wrap: wrap;
    }

    .search-input {
      position: relative;
      flex: 1;
      min-width: 280px;

      svg {
        position: absolute;
        left: 1rem;
        top: 50%;
        transform: translateY(-50%);
        color: var(--text-muted);
      }

      input {
        width: 100%;
        padding: 0.65rem 1rem 0.65rem 2.75rem;
        background-color: var(--bg-card);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-size: 0.875rem;

        &:focus {
          outline: none;
          border-color: var(--accent-primary);
        }
      }
    }

    .filter-tabs {
      display: flex;
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 0.25rem;
      gap: 0.25rem;
    }

    .tab-btn {
      background: transparent;
      border: none;
      color: var(--text-secondary);
      padding: 0.45rem 1rem;
      border-radius: var(--radius-sm);
      font-size: 0.825rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;

      &.active {
        background-color: var(--accent-primary);
        color: #fff;
      }
    }

    .professionals-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 1.5rem;
    }

    .pro-card {
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      transition: transform 0.2s, border-color 0.2s;

      &:hover {
        border-color: var(--border-light);
        transform: translateY(-2px);
      }

      &.inactive {
        opacity: 0.65;
        border-style: dashed;
      }
    }

    .pro-header {
      display: flex;
      gap: 1rem;
      align-items: center;
      margin-bottom: 1.25rem;
    }

    .pro-avatar {
      width: 52px;
      height: 52px;
      border-radius: var(--radius-full);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.2rem;
      font-weight: 800;
      color: #fff;
      box-shadow: var(--shadow-sm);
      flex-shrink: 0;
    }

    .pro-meta {
      flex: 1;
      min-width: 0;
    }

    .pro-name-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;

      h3 {
        font-size: 1.1rem;
        font-weight: 700;
        color: var(--text-primary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .status-pill {
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.15rem 0.5rem;
      border-radius: var(--radius-full);
      background-color: rgba(148, 163, 184, 0.2);
      color: var(--text-secondary);

      &.active {
        background-color: var(--color-success-bg);
        color: var(--color-success);
      }
    }

    .role-badge {
      display: inline-block;
      font-size: 0.75rem;
      font-weight: 600;
      color: #818cf8;
      margin-top: 0.15rem;

      &.piercer {
        color: #38bdf8;
      }
    }

    .pro-body {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      margin-bottom: 1.5rem;
      flex: 1;
    }

    .field-label {
      font-size: 0.75rem;
      color: var(--text-muted);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .specialties-badges {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
      margin-top: 0.35rem;
    }

    .spec-badge {
      font-size: 0.75rem;
      padding: 0.25rem 0.6rem;
      border-radius: var(--radius-sm);
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
    }

    .stats-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.75rem;
      padding: 0.85rem;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
    }

    .stat-item {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .stat-label {
      font-size: 0.7rem;
      color: var(--text-muted);
      font-weight: 600;
    }

    .stat-value {
      font-size: 0.95rem;
      font-weight: 700;

      &.reputation { color: #f59e0b; }
      &.rate { color: var(--text-primary); }
    }

    .contact-info {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }

    .contact-line {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.8rem;
      color: var(--text-secondary);

      svg {
        color: var(--text-muted);
        flex-shrink: 0;
      }
    }

    .pro-actions {
      display: flex;
      gap: 0.5rem;
      border-top: 1px solid var(--border-color);
      padding-top: 1rem;
    }

    .btn-sm {
      padding: 0.45rem 0.85rem;
      font-size: 0.775rem;
      flex: 1;
    }

    .btn-danger-outline {
      background: transparent;
      border: 1px solid rgba(239, 68, 68, 0.4);
      color: var(--color-danger);

      &:hover {
        background-color: var(--color-danger-bg);
      }
    }

    .btn-success-outline {
      background: transparent;
      border: 1px solid rgba(16, 185, 129, 0.4);
      color: var(--color-success);

      &:hover {
        background-color: var(--color-success-bg);
      }
    }

    /* Modal Styling */
    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    .full-width {
      grid-column: span 2;
    }

    .input-group {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;

      label {
        font-size: 0.8rem;
        font-weight: 600;
        color: var(--text-secondary);
      }

      input, select {
        padding: 0.65rem 0.85rem;
        background-color: var(--bg-main);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-size: 0.875rem;

        &:focus {
          outline: none;
          border-color: var(--accent-primary);
        }
      }

      .hint {
        font-size: 0.75rem;
        color: var(--text-muted);
      }
    }

    .loading-state, .empty-state {
      padding: 3rem;
      text-align: center;
      color: var(--text-secondary);
    }

    .spinner {
      width: 32px;
      height: 32px;
      border: 3px solid rgba(99, 102, 241, 0.2);
      border-top-color: var(--accent-primary);
      border-radius: var(--radius-full);
      animation: spin 0.8s linear infinite;
      margin: 0 auto 1rem;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class ProfissionaisComponent implements OnInit {
  private readonly professionalService = inject(ProfessionalService);

  readonly professionals = signal<Professional[]>([]);
  readonly loading = signal<boolean>(true);
  readonly saving = signal<boolean>(false);
  readonly filterStatus = signal<'ACTIVE' | 'ALL'>('ACTIVE');
  searchQuery = '';

  readonly feedbackMessage = signal<string | null>(null);
  readonly feedbackType = signal<'success' | 'error'>('success');

  readonly showModal = signal<boolean>(false);
  readonly isEditing = signal<boolean>(false);
  selectedId: string | null = null;

  currentForm: Partial<Professional> = {
    name: '',
    email: '',
    password: '',
    phone: '',
    role: 'ARTIST',
    specialties: '',
    artistRateType: 'PERCENTAGE',
    artistRateValue: 60,
    reputation: 5.0,
    isActive: true
  };

  readonly activeCount = computed(() => this.professionals().filter(p => p.isActive).length);
  readonly piercersCount = computed(() => this.professionals().filter(p => this.isPiercer(p)).length);
  readonly tattooArtistsCount = computed(() => this.professionals().filter(p => !this.isPiercer(p)).length);

  readonly filteredProfessionals = computed(() => {
    let list = this.professionals();
    if (this.filterStatus() === 'ACTIVE') {
      list = list.filter(p => p.isActive);
    }
    const q = this.searchQuery.trim().toLowerCase();
    if (q) {
      list = list.filter(p =>
        p.name.toLowerCase().includes(q) ||
        (p.specialties && p.specialties.toLowerCase().includes(q)) ||
        p.email.toLowerCase().includes(q)
      );
    }
    return list;
  });

  ngOnInit(): void {
    this.loadProfessionals();
  }

  loadProfessionals(): void {
    this.loading.set(true);
    this.professionalService.getProfessionals(true).subscribe({
      next: (list) => {
        this.professionals.set(list || []);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.showToast('Erro ao carregar lista de profissionais.', 'error');
      }
    });
  }

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedId = null;
    this.currentForm = {
      name: '',
      email: '',
      password: '',
      phone: '',
      role: 'ARTIST',
      specialties: '',
      artistRateType: 'PERCENTAGE',
      artistRateValue: 60,
      reputation: 5.0,
      isActive: true
    };
    this.showModal.set(true);
  }

  openEditModal(prof: Professional): void {
    this.isEditing.set(true);
    this.selectedId = prof.id || null;
    this.currentForm = {
      name: prof.name,
      email: prof.email,
      password: '',
      phone: prof.phone || '',
      role: prof.role,
      specialties: prof.specialties || '',
      artistRateType: prof.artistRateType || 'PERCENTAGE',
      artistRateValue: prof.artistRateValue || 60,
      reputation: prof.reputation || 5.0,
      isActive: prof.isActive
    };
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  saveProfessional(): void {
    this.saving.set(true);

    if (this.isEditing() && this.selectedId) {
      this.professionalService.update(this.selectedId, this.currentForm).subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.closeModal();
          this.professionals.update(list => list.map(p => p.id === updated.id ? updated : p));
          this.showToast('Profissional atualizado com sucesso!');
        },
        error: (err) => {
          this.saving.set(false);
          this.showToast(err.error?.message || 'Erro ao atualizar profissional.', 'error');
        }
      });
    } else {
      this.professionalService.create(this.currentForm).subscribe({
        next: (created) => {
          this.saving.set(false);
          this.closeModal();
          this.professionals.update(list => [...list, created]);
          this.showToast('Profissional cadastrado com sucesso!');
        },
        error: (err) => {
          this.saving.set(false);
          this.showToast(err.error?.message || 'Erro ao cadastrar profissional.', 'error');
        }
      });
    }
  }

  toggleStatus(prof: Professional): void {
    if (!prof.id) return;
    const nextStatus = !prof.isActive;
    this.professionalService.update(prof.id, { isActive: nextStatus }).subscribe({
      next: (updated) => {
        this.professionals.update(list => list.map(p => p.id === updated.id ? updated : p));
        this.showToast(`Profissional ${nextStatus ? 'ativado' : 'desativado'} com sucesso!`);
      },
      error: () => {
        this.showToast('Erro ao alterar status do profissional.', 'error');
      }
    });
  }

  isPiercer(prof: Professional): boolean {
    const spec = (prof.specialties || '').toLowerCase();
    const name = (prof.name || '').toLowerCase();
    return spec.includes('piercing') || name.includes('piercer') || prof.artistRateType === 'HOURLY';
  }

  parseSpecialties(specs: string | undefined): string[] {
    if (!specs) return [];
    return specs.split(',').map(s => s.trim()).filter(s => s.length > 0);
  }

  getInitials(name: string): string {
    if (!name) return 'A';
    const parts = name.trim().split(' ');
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  getAvatarColor(name: string): string {
    const colors = [
      'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)',
      'linear-gradient(135deg, #ec4899 0%, #be185d 100%)',
      'linear-gradient(135deg, #10b981 0%, #047857 100%)',
      'linear-gradient(135deg, #f59e0b 0%, #b45309 100%)',
      'linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%)',
      'linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%)'
    ];
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash) % colors.length;
    return colors[index];
  }

  showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.feedbackMessage.set(msg);
    this.feedbackType.set(type);
    setTimeout(() => {
      this.feedbackMessage.set(null);
    }, 5000);
  }
}
