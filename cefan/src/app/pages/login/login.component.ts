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
      padding: 1.5rem;
    }
    .login-card {
      width: 100%;
      max-width: 440px;
      background-color: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 2.5rem 2rem;
      box-shadow: var(--shadow-lg);
    }
    .login-brand {
      text-align: center;
      margin-bottom: 1.75rem;
      .logo-icon {
        font-size: 2.2rem;
        margin-bottom: 0.5rem;
      }
      h2 {
        font-size: 1.4rem;
        font-weight: 800;
        color: var(--text-primary);
      }
      p {
        font-size: 0.85rem;
        color: var(--text-secondary);
        margin-top: 0.25rem;
      }
    }
    .input-group {
      margin-bottom: 1.25rem;
      label {
        display: block;
        font-size: 0.825rem;
        font-weight: 600;
        margin-bottom: 0.4rem;
        color: var(--text-secondary);
      }
      input {
        width: 100%;
        padding: 0.75rem 1rem;
        background-color: var(--bg-surface);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        color: var(--text-primary);
        font-size: 0.9rem;
        transition: border-color 0.2s;
        &:focus {
          outline: none;
          border-color: var(--accent-primary);
          box-shadow: 0 0 0 2px var(--accent-glow);
        }
      }
    }
    .quick-credentials {
      margin: 1rem 0;
      padding: 0.75rem;
      background-color: var(--bg-surface);
      border: 1px dashed var(--border-color);
      border-radius: var(--radius-md);
      font-size: 0.75rem;
      .quick-title {
        color: var(--text-muted);
        display: block;
        margin-bottom: 0.35rem;
      }
      .quick-buttons {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
      .quick-btn {
        background: var(--bg-card);
        border: 1px solid var(--border-color);
        color: var(--accent-primary);
        font-size: 0.75rem;
        padding: 0.2rem 0.5rem;
        border-radius: var(--radius-sm);
        cursor: pointer;
        &:hover {
          border-color: var(--accent-primary);
        }
      }
    }
    .login-error {
      background-color: var(--color-danger-bg);
      color: var(--color-danger);
      border: 1px solid rgba(239, 68, 68, 0.3);
      padding: 0.75rem;
      border-radius: var(--radius-md);
      font-size: 0.825rem;
      margin-bottom: 1.25rem;
    }
    .login-btn {
      width: 100%;
      margin-top: 0.75rem;
      padding: 0.85rem;
      font-size: 0.95rem;
      font-weight: 700;
      background-color: var(--accent-primary);
      color: white;
      border: none;
      border-radius: var(--radius-md);
      cursor: pointer;
      box-shadow: 0 4px 14px var(--accent-glow);
      transition: opacity 0.2s;
      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
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
