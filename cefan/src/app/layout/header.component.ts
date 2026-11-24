import { Component, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AppointmentService } from '../core/services/appointment.service';
import { AuthService } from '../core/services/auth.service';
import { TenantConfigService } from '../core/services/tenant-config.service';
import { NotificationEvent } from '../core/models/appointment.model';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <!-- Top Header Bar -->
    <header class="main-header">
      <div class="header-container">
        <div class="brand" [routerLink]="authService.isAuthenticated() ? '/agenda' : null" [style.cursor]="authService.isAuthenticated() ? 'pointer' : 'default'">
          <div class="logo-icon" [class.has-logo]="!!studioLogo()">
            @if (studioLogo()) {
              <img [src]="studioLogo()" [alt]="studioName() || 'Logo do estúdio'" class="logo-img" />
            } @else {
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
              </svg>
            }
          </div>
          @if (studioName()) {
            <div class="brand-text">
              <span class="brand-title" [style.font-size]="brandTitleFontSize()" [title]="studioName()">{{ studioName() }}</span>
            </div>
          }
        </div>

        <!-- Desktop Navigation Links (Hidden on Mobile) -->
        <nav class="nav-links desktop-nav" *ngIf="authService.isAuthenticated()">
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

          <!-- Gestão de Profissionais e Taxas Individuais (Exclusivo Dono) -->
          <a *ngIf="authService.isOwner()" routerLink="/profissionais" routerLinkActive="active" class="nav-item">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            <span>Profissionais</span>
          </a>

          <!-- Configurações do Estúdio visíveis estritamente para o Dono (OWNER / ADMIN) -->
          <a *ngIf="authService.isOwner()" routerLink="/configuracoes" routerLinkActive="active" class="nav-item">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="3"></circle>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
            </svg>
            <span>Configurações</span>
          </a>
        </nav>

        <div class="header-right">
          <!-- Status do Bot WhatsApp -->
          <div class="system-status" title="Status da Conexão com Evolution API WhatsApp">
            <span class="status-dot"></span>
            <span class="status-label">Bot WhatsApp</span>
          </div>

          <!-- Notificações em Tempo Real -->
          <div class="notif-wrapper" *ngIf="authService.isAuthenticated()">
            <button class="notif-btn" (click)="toggleNotifications()" title="Alertas em Tempo Real" aria-label="Notificações">
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

          <!-- Perfil do Usuário & Logout -->
          <div class="user-profile" *ngIf="authService.isAuthenticated()">
            <div class="user-avatar-initial" [title]="authService.currentUser()?.name || ''">
              {{ (authService.currentUser()?.name || 'U').charAt(0).toUpperCase() }}
            </div>
            <div class="user-meta desktop-only">
              <span class="user-name">{{ authService.currentUser()?.name }}</span>
              <span class="user-badge" [ngClass]="authService.isOwner() ? 'badge-owner' : 'badge-artist'">
                {{ authService.isOwner() ? 'Dono' : 'Tatuador' }}
              </span>
            </div>
          </div>

          <button
            *ngIf="authService.isAuthenticated()"
            class="logout-btn"
            (click)="logout()"
            title="Sair do Painel"
            aria-label="Sair">
            <svg class="mobile-only-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
            <span class="desktop-only">Sair</span>
          </button>
        </div>
      </div>
    </header>

    <!-- Apple iOS Bottom Navigation Bar (Mobile First) -->
    <nav class="mobile-tab-bar" *ngIf="authService.isAuthenticated()">
      <a routerLink="/agenda" routerLinkActive="active" class="tab-item">
        <div class="tab-icon-wrapper">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
            <line x1="16" y1="2" x2="16" y2="6"></line>
            <line x1="8" y1="2" x2="8" y2="6"></line>
            <line x1="3" y1="10" x2="21" y2="10"></line>
          </svg>
        </div>
        <span class="tab-label">Agenda</span>
      </a>

      <a routerLink="/atendimentos" routerLinkActive="active" class="tab-item">
        <div class="tab-icon-wrapper">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
          </svg>
          <span *ngIf="humanQueueCount() > 0" class="tab-badge">{{ humanQueueCount() }}</span>
        </div>
        <span class="tab-label">Chat</span>
      </a>

      <a *ngIf="authService.isOwner()" routerLink="/profissionais" routerLinkActive="active" class="tab-item">
        <div class="tab-icon-wrapper">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
        </div>
        <span class="tab-label">Equipe</span>
      </a>

      <a *ngIf="authService.isOwner()" routerLink="/configuracoes" routerLinkActive="active" class="tab-item">
        <div class="tab-icon-wrapper">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3"></circle>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
          </svg>
        </div>
        <span class="tab-label">Ajustes</span>
      </a>
    </nav>
  `,
  styles: [`
    .main-header {
      background: rgba(10, 14, 23, 0.82);
      backdrop-filter: blur(20px) saturate(190%);
      -webkit-backdrop-filter: blur(20px) saturate(190%);
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .header-container {
      max-width: 1280px;
      margin: 0 auto;
      padding: 0 1.25rem;
      min-height: 64px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      min-width: 0;
      max-width: 240px;
      flex-shrink: 1;
      user-select: none;
      transition: opacity 0.2s;

      &:active {
        opacity: 0.8;
      }
    }

    .logo-icon {
      width: 38px;
      height: 38px;
      flex-shrink: 0;
      border-radius: 10px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      box-shadow: 0 4px 12px var(--accent-glow);
      overflow: hidden;

      &.has-logo {
        background: transparent;
        box-shadow: none;
      }
    }

    .logo-img {
      width: 100%;
      height: 100%;
      object-fit: contain;
      border-radius: 10px;
      display: block;
    }

    .brand-text {
      display: flex;
      flex-direction: column;
      min-width: 0;
      overflow: hidden;
    }

    .brand-title {
      font-size: 1.1rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: var(--text-primary);
      line-height: 1.15;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: font-size 0.2s ease;
    }

    .studio-badge {
      font-size: 0.68rem;
      font-weight: 600;
      color: var(--accent-primary);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    /* Desktop Navigation */
    .desktop-nav {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.55rem 0.9rem;
      border-radius: var(--radius-md);
      color: var(--text-secondary);
      font-size: 0.875rem;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.15s ease;
      min-height: 40px;

      &:hover {
        color: var(--text-primary);
        background-color: rgba(255, 255, 255, 0.05);
      }

      &:active {
        transform: scale(0.97);
      }

      &.active {
        color: #ffffff;
        background: rgba(99, 102, 241, 0.2);
        border: 1px solid rgba(99, 102, 241, 0.4);
        box-shadow: 0 2px 10px rgba(99, 102, 241, 0.2);
      }
    }

    .nav-badge {
      background-color: var(--color-danger);
      color: #fff;
      font-size: 0.65rem;
      font-weight: 800;
      padding: 0.1rem 0.4rem;
      border-radius: var(--radius-full);
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 0.65rem;
    }

    .system-status {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.35rem 0.65rem;
      background-color: var(--color-success-bg);
      border: 1px solid rgba(48, 209, 88, 0.25);
      border-radius: var(--radius-full);
      font-size: 0.725rem;
      font-weight: 600;
      color: var(--color-success);
    }

    .status-dot {
      width: 7px;
      height: 7px;
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
      background-color: rgba(255, 255, 255, 0.06);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      width: 44px;
      height: 44px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.15s ease;

      &:hover {
        background-color: rgba(255, 255, 255, 0.1);
      }

      &:active {
        transform: scale(0.92);
      }
    }

    .notif-count {
      position: absolute;
      top: -3px;
      right: -3px;
      background-color: var(--color-danger);
      color: white;
      font-size: 0.65rem;
      font-weight: 800;
      padding: 0.1rem 0.35rem;
      border-radius: var(--radius-full);
      border: 2px solid var(--bg-surface);
    }

    .notif-dropdown {
      position: absolute;
      right: 0;
      top: 50px;
      width: min(320px, calc(100vw - 2rem));
      background-color: rgba(18, 24, 36, 0.96);
      backdrop-filter: var(--glass-blur);
      -webkit-backdrop-filter: var(--glass-blur);
      border: 1px solid rgba(255, 255, 255, 0.12);
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
      font-size: 1.25rem;
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

    .user-profile {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.35rem 0.65rem;
      background-color: rgba(255, 255, 255, 0.04);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
    }

    .user-avatar-initial {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: linear-gradient(135deg, #4f46e5, #06b6d4);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 0.75rem;
      color: #ffffff;
      flex-shrink: 0;
    }

    .user-meta {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .user-name {
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--text-primary);
    }

    .user-badge {
      font-size: 0.68rem;
      font-weight: 700;
      padding: 0.12rem 0.4rem;
      border-radius: var(--radius-xs);
      text-transform: uppercase;
      letter-spacing: 0.04em;

      &.badge-owner {
        background-color: rgba(99, 102, 241, 0.2);
        color: #818cf8;
        border: 1px solid rgba(99, 102, 241, 0.4);
      }

      &.badge-artist {
        background-color: rgba(48, 209, 88, 0.2);
        color: #30d158;
        border: 1px solid rgba(48, 209, 88, 0.4);
      }
    }

    .logout-btn {
      background-color: transparent;
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      min-height: 44px;
      min-width: 44px;
      padding: 0.4rem 0.75rem;
      border-radius: var(--radius-md);
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;

      &:hover {
        border-color: var(--color-danger);
        color: var(--color-danger);
        background-color: rgba(255, 69, 58, 0.08);
      }

      &:active {
        transform: scale(0.92);
      }
    }

    .mobile-only-icon {
      display: none;
    }

    /* Apple iOS Bottom Navigation Bar */
    .mobile-tab-bar {
      display: none;
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      height: calc(64px + var(--safe-bottom));
      padding-bottom: var(--safe-bottom);
      background: rgba(13, 17, 26, 0.88);
      backdrop-filter: blur(24px) saturate(190%);
      -webkit-backdrop-filter: blur(24px) saturate(190%);
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      z-index: 990;
      justify-content: space-around;
      align-items: center;
      box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.35);
    }

    .tab-item {
      flex: 1;
      height: 100%;
      min-height: 48px;
      min-width: 48px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 3px;
      color: var(--text-muted);
      text-decoration: none;
      transition: transform 100ms ease, color 0.15s ease;
      user-select: none;

      &:active {
        transform: scale(0.90);
      }

      &.active {
        color: #818cf8;

        .tab-icon-wrapper svg {
          stroke: #818cf8;
          filter: drop-shadow(0 0 6px rgba(99, 102, 241, 0.5));
        }

        .tab-label {
          color: #ffffff;
          font-weight: 700;
        }
      }
    }

    .tab-icon-wrapper {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
    }

    .tab-badge {
      position: absolute;
      top: -3px;
      right: -6px;
      background-color: var(--color-danger);
      color: #fff;
      font-size: 0.6rem;
      font-weight: 800;
      padding: 0.05rem 0.32rem;
      border-radius: var(--radius-full);
      border: 1px solid #131824;
    }

    .tab-label {
      font-size: 0.68rem;
      font-weight: 500;
      letter-spacing: -0.01em;
      transition: color 0.15s ease;
    }

    @keyframes pulse {
      0% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.35; transform: scale(0.85); }
      100% { opacity: 1; transform: scale(1); }
    }

    @media (max-width: 768px) {
      .desktop-nav {
        display: none !important;
      }

      .mobile-tab-bar {
        display: flex;
      }

      .desktop-only {
        display: none !important;
      }

      .mobile-only-icon {
        display: inline-block;
      }

      .header-container {
        padding: 0 0.75rem;
        min-height: 56px;
        gap: 0.5rem;
      }

      .brand {
        max-width: none;
        flex: 1;
        min-width: 0;
        gap: 0.55rem;
      }

      .logo-icon {
        width: 34px;
        height: 34px;
      }

      .header-right {
        gap: 0.4rem;
        flex-shrink: 0;
      }

      .status-label {
        display: none;
      }

      .system-status {
        padding: 0.45rem;
      }

      .notif-wrapper {
        position: static;
      }

      .notif-btn {
        width: 38px;
        height: 38px;
      }

      .notif-dropdown {
        position: fixed;
        top: calc(60px + var(--safe-top));
        left: 0.75rem;
        right: 0.75rem;
        width: auto;
        max-width: none;
      }

      .notif-clear {
        width: 36px;
        height: 36px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }

      .user-profile {
        padding: 0.15rem;
        border: none;
        background: transparent;
      }

      .logout-btn {
        padding: 0;
        width: 38px;
        height: 38px;
        min-width: 38px;
        min-height: 38px;
      }
    }
  `]
})
export class HeaderComponent {
  readonly adminService = inject(AppointmentService);
  readonly authService = inject(AuthService);
  readonly tenantConfigService = inject(TenantConfigService);
  private readonly router = inject(Router);

  readonly studioName = computed(() => {
    if (!this.authService.isAuthenticated()) return '';
    return this.tenantConfigService.branding()?.studioName || this.authService.currentUser()?.studioName || '';
  });
  readonly studioLogo = computed(() => {
    if (!this.authService.isAuthenticated()) return null;
    return this.tenantConfigService.branding()?.logoBase64 || null;
  });
  readonly brandTitleFontSize = computed(() => {
    const len = this.studioName().trim().length;
    if (len <= 14) return '1.1rem';
    if (len <= 22) return '0.95rem';
    if (len <= 30) return '0.85rem';
    return '0.78rem';
  });

  readonly notifications = signal<NotificationEvent[]>([]);
  readonly humanQueueCount = signal<number>(0);
  readonly showNotifDropdown = signal<boolean>(false);

  constructor() {
    effect(() => {
      const user = this.authService.currentUser();
      if (user && user.token) {
        this.tenantConfigService.loadBranding();
        this.refreshAlerts();
      } else {
        this.tenantConfigService.clearBranding();
        this.notifications.set([]);
        this.humanQueueCount.set(0);
      }
    });
  }

  toggleNotifications(): void {
    const next = !this.showNotifDropdown();
    this.showNotifDropdown.set(next);
    if (next) {
      this.refreshAlerts();
    }
  }

  refreshAlerts(): void {
    if (!this.authService.isAuthenticated()) return;
    this.adminService.getNotifications().subscribe({
      next: (list) => {
        this.notifications.set(list || []);
        this.humanQueueCount.set((list || []).filter(n => n.type === 'HUMAN_REQUEST').length);
      },
      error: () => {}
    });
  }

  logout(): void {
    this.authService.logout();
  }


}
