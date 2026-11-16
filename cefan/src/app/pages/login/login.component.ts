import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

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
          <p>Acesso ao painel administrativo e agenda de profissionais</p>
        </div>

        <div *ngIf="errorMsg()" class="login-error">{{ errorMsg() }}</div>

        <div class="input-group">
          <label>E-mail</label>
          <input
            type="email"
            [(ngModel)]="email"
            placeholder="seu.email@viciotattoo.com"
            (keyup.enter)="submitLogin()"
          />
        </div>

        <div class="input-group">
          <label>Senha</label>
          <input
            type="password"
            [(ngModel)]="password"
            placeholder="••••••••"
            (keyup.enter)="submitLogin()"
          />
        </div>

        <div class="quick-credentials">
          <span class="quick-title">Credenciais de teste:</span>
          <div class="quick-buttons">
            <button type="button" class="quick-btn" (click)="setEmail('admin@viciotattoo.com')">Dono</button>
            <button type="button" class="quick-btn" (click)="setEmail('carlos@viciotattoo.com')">Carlos (Realismo)</button>
            <button type="button" class="quick-btn" (click)="setEmail('mariana@viciotattoo.com')">Mariana (Fineline)</button>
          </div>
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
      padding: 1.5rem 1rem calc(40px + var(--safe-bottom));
    }

    .login-card {
      width: 100%;
      max-width: 440px;
      background: rgba(18, 24, 38, 0.88);
      backdrop-filter: var(--glass-blur);
      -webkit-backdrop-filter: var(--glass-blur);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: var(--radius-xl);
      padding: 2.5rem 2rem;
      box-shadow: var(--shadow-lg);
    }

    .login-brand {
      text-align: center;
      margin-bottom: 2rem;

      .logo-icon {
        width: 52px;
        height: 52px;
        margin: 0 auto 0.75rem auto;
        border-radius: 14px;
        background: linear-gradient(135deg, #6366f1, #8b5cf6);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 1.75rem;
        box-shadow: 0 8px 24px var(--accent-glow);
      }

      h2 {
        font-size: 1.45rem;
        font-weight: 800;
        letter-spacing: -0.03em;
        color: var(--text-primary);
      }

      p {
        font-size: 0.85rem;
        color: var(--text-secondary);
        margin-top: 0.35rem;
        line-height: 1.4;
      }
    }

    .input-group {
      margin-bottom: 1.25rem;

      label {
        display: block;
        font-size: 0.825rem;
        font-weight: 600;
        margin-bottom: 0.45rem;
        color: var(--text-primary);
        letter-spacing: -0.01em;
      }

      input {
        width: 100%;
        min-height: 48px;
        padding: 0.75rem 1rem;
        background-color: rgba(12, 16, 26, 0.85);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-size: 16px; /* 16px prevents iOS Safari auto-zoom */
        transition: border-color 0.2s, box-shadow 0.2s;

        &:focus {
          outline: none;
          border-color: var(--accent-primary);
          box-shadow: 0 0 0 3px var(--accent-glow);
        }
      }
    }

    .quick-credentials {
      margin: 1.25rem 0;
      padding: 0.85rem 1rem;
      background-color: rgba(12, 16, 26, 0.6);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      font-size: 0.75rem;

      .quick-title {
        color: var(--text-muted);
        display: block;
        margin-bottom: 0.5rem;
        font-weight: 600;
      }

      .quick-buttons {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
      }

      .quick-btn {
        background: rgba(255, 255, 255, 0.06);
        border: 1px solid rgba(255, 255, 255, 0.08);
        color: #818cf8;
        font-size: 0.775rem;
        font-weight: 600;
        padding: 0.45rem 0.85rem;
        min-height: 42px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--radius-full);
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover {
          background: rgba(99, 102, 241, 0.15);
          border-color: var(--accent-primary);
          color: #ffffff;
        }

        &:active {
          transform: scale(0.94);
        }
      }
    }

    .login-error {
      background-color: var(--color-danger-bg);
      color: var(--color-danger);
      border: 1px solid rgba(255, 69, 58, 0.3);
      padding: 0.75rem 1rem;
      border-radius: var(--radius-md);
      font-size: 0.825rem;
      margin-bottom: 1.25rem;
    }

    .login-btn {
      width: 100%;
      min-height: 50px;
      margin-top: 0.75rem;
      padding: 0.85rem;
      font-size: 0.95rem;
      font-weight: 700;
      letter-spacing: -0.01em;
      background: linear-gradient(135deg, #6366f1, #4f46e5);
      color: white;
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: var(--radius-md);
      cursor: pointer;
      box-shadow: 0 4px 16px var(--accent-glow);
      transition: transform 100ms ease, box-shadow 0.2s;

      &:hover:not(:disabled) {
        box-shadow: 0 6px 20px var(--accent-glow);
      }

      &:active:not(:disabled) {
        transform: scale(0.98);
      }

      &:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
    }

    @media (max-width: 480px) {
      .login-card {
        padding: 1.75rem 1.25rem;
        border-radius: var(--radius-lg);
      }

      .login-brand h2 {
        font-size: 1.25rem;
      }
    }
  `]
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  email = 'admin@viciotattoo.com';
  password = 'cefan123';
  readonly loading = signal<boolean>(false);
  readonly errorMsg = signal<string | null>(null);

  setEmail(userEmail: string): void {
    this.email = userEmail;
    this.password = 'cefan123';
  }

  submitLogin(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.authService.login(this.email, this.password).subscribe({
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
