import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AppointmentService } from '../core/services/appointment.service';
import { NotificationEvent } from '../core/models/appointment.model';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <header class="main-header">
      <div class="header-container">
        <div class="brand">
          <div class="logo-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
            </svg>
          </div>
          <div class="brand-text">
            <span class="brand-title">CEFAN</span>
            <span class="studio-badge">Vício Tattoo</span>
          </div>
        </div>

        <nav class="nav-links" *ngIf="adminService.isAuthenticated()">
          <a routerLink="/agenda" routerLinkActive="active" class="nav-item">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
            <span>Agenda Visual</span>
          </a>

          <a routerLink="/atendimentos" routerLinkActive="active" class="nav-item">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
            </svg>
            <span>Atendimentos (Live Chat)</span>
            <span *ngIf="humanQueueCount() > 0" class="nav-badge">{{ humanQueueCount() }}</span>
          </a>

          <a routerLink="/configuracoes" routerLinkActive="active" class="nav-item">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="3"></circle>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
            </svg>
            <span>Configurações do Estúdio</span>
          </a>
        </nav>

        <div class="header-right">
          <div class="system-status">
            <span class="status-dot"></span>
            <span class="status-label">Bot WhatsApp Ativo</span>
          </div>

          <!-- Notificações em Tempo Real -->
          <div class="notif-wrapper" *ngIf="adminService.isAuthenticated()">
            <button class="notif-btn" (click)="toggleNotifications()" title="Notificações em Tempo Real">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
              </svg>
              <span *ngIf="notifications().length > 0" class="notif-count">{{ notifications().length }}</span>
            </button>

            <div *ngIf="showNotifDropdown()" class="notif-dropdown">
              <div class="notif-header">
                <span>Alertas em Tempo Real</span>
                <button class="notif-clear" (click)="showNotifDropdown.set(false)">×</button>
              </div>
              <div *ngIf="notifications().length === 0" class="notif-empty">
                Nenhum alerta pendente no momento.
              </div>
              <div class="notif-list" *ngIf="notifications().length > 0">
                <div *ngFor="let item of notifications()" class="notif-item">
                  <div class="notif-title">{{ item.title }}</div>
                  <div class="notif-msg">{{ item.message }}</div>
                </div>
              </div>
            </div>
          </div>

          <button
            *ngIf="adminService.isAuthenticated()"
            class="logout-btn"
            (click)="logout()"
            title="Sair do Painel">
            Sair
          </button>
        </div>
      </div>
    </header>
  `,
  styles: [`
    .main-header {
      background-color: var(--bg-surface);
      border-bottom: 1px solid var(--border-color);
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .header-container {
      max-width: 1280px;
      margin: 0 auto;
      padding: 0 1.5rem;
      height: 70px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .logo-icon {
      width: 40px;
      height: 40px;
      border-radius: var(--radius-md);
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      box-shadow: 0 4px 10px var(--accent-glow);
    }

    .brand-text {
      display: flex;
      flex-direction: column;
    }

    .brand-title {
      font-size: 1.15rem;
      font-weight: 800;
      letter-spacing: -0.02em;
      color: var(--text-primary);
    }

    .studio-badge {
      font-size: 0.7rem;
      font-weight: 600;
      color: var(--accent-primary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .nav-links {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.6rem 1rem;
      border-radius: var(--radius-md);
      color: var(--text-secondary);
      font-size: 0.875rem;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.2s ease;

      &:hover {
        color: var(--text-primary);
        background-color: var(--bg-card);
      }

      &.active {
        color: #ffffff;
        background-color: var(--accent-primary);
        box-shadow: 0 4px 12px var(--accent-glow);
      }
    }

    .nav-badge {
      background-color: var(--color-danger);
      color: #fff;
      font-size: 0.7rem;
      font-weight: 800;
      padding: 0.1rem 0.45rem;
      border-radius: var(--radius-full);
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .system-status {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.35rem 0.75rem;
      background-color: var(--color-success-bg);
      border: 1px solid rgba(16, 185, 129, 0.25);
      border-radius: var(--radius-full);
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--color-success);
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: var(--color-success);
      box-shadow: 0 0 8px var(--color-success);
      animation: pulse 2s infinite;
    }

    .notif-wrapper {
      position: relative;
    }

    .notif-btn {
      position: relative;
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      width: 38px;
      height: 38px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }

    .notif-count {
      position: absolute;
      top: -4px;
      right: -4px;
      background-color: var(--color-danger);
      color: white;
      font-size: 0.65rem;
      font-weight: 800;
      padding: 0.1rem 0.35rem;
      border-radius: var(--radius-full);
    }

    .notif-dropdown {
      position: absolute;
      right: 0;
      top: 46px;
      width: 320px;
      background-color: var(--bg-surface);
      border: 1px solid var(--border-light);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-lg);
      z-index: 200;
      overflow: hidden;
    }

    .notif-header {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.85rem;
      font-weight: 700;
    }

    .notif-clear {
      background: none;
      border: none;
      color: var(--text-secondary);
      font-size: 1.2rem;
      cursor: pointer;
    }

    .notif-empty {
      padding: 1.25rem;
      text-align: center;
      font-size: 0.8rem;
      color: var(--text-muted);
    }

    .notif-list {
      max-height: 260px;
      overflow-y: auto;
    }

    .notif-item {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid var(--border-color);
      font-size: 0.8rem;
    }

    .notif-title {
      font-weight: 700;
      color: var(--text-primary);
      margin-bottom: 0.2rem;
    }

    .notif-msg {
      color: var(--text-secondary);
    }

    .logout-btn {
      background-color: transparent;
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0.4rem 0.75rem;
      border-radius: var(--radius-md);
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        border-color: var(--color-danger);
        color: var(--color-danger);
      }
    }

    @keyframes pulse {
      0% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.4; transform: scale(0.85); }
      100% { opacity: 1; transform: scale(1); }
    }
  `]
})
export class HeaderComponent implements OnInit {
  readonly adminService = inject(AppointmentService);
  private readonly router = inject(Router);

  readonly notifications = signal<NotificationEvent[]>([]);
  readonly humanQueueCount = signal<number>(0);
  readonly showNotifDropdown = signal<boolean>(false);

  ngOnInit(): void {
    this.refreshAlerts();
  }

  toggleNotifications(): void {
    const next = !this.showNotifDropdown();
    this.showNotifDropdown.set(next);
    if (next) {
      this.refreshAlerts();
    }
  }

  refreshAlerts(): void {
    if (!this.adminService.isAuthenticated()) return;
    this.adminService.getNotifications().subscribe({
      next: (list) => {
        this.notifications.set(list || []);
        this.humanQueueCount.set((list || []).filter(n => n.type === 'HUMAN_REQUEST').length);
      },
      error: () => {}
    });
  }

  logout(): void {
    this.adminService.logout();
    this.router.navigate(['/login']);
  }
}
