import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthUser } from '../models/appointment.model';

const AUTH_STORAGE_KEY = 'cefan_auth_user';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly isBrowser = typeof window !== 'undefined' && !!window.location;
  private readonly apiRoot =
    this.isBrowser && window.location.port === '4200'
      ? '/api/v1'
      : (this.isBrowser && window.location.hostname ? `http://${window.location.hostname}:8082/api/v1` : 'http://localhost:8082/api/v1');

  readonly currentUser = signal<AuthUser | null>(this.loadStoredUser());
  readonly token = computed(() => this.currentUser()?.token ?? null);
  readonly isAuthenticated = computed(() => !!this.currentUser()?.token);
  readonly isOwner = computed(() => {
    const role = this.currentUser()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });
  readonly userRole = computed(() => this.currentUser()?.role ?? null);

  private loadStoredUser(): AuthUser | null {
    try {
      if (typeof localStorage !== 'undefined') {
        const raw = localStorage.getItem(AUTH_STORAGE_KEY);
        if (raw) {
          return JSON.parse(raw) as AuthUser;
        }
      }
    } catch {
      // Storage não disponível no ambiente
    }
    return null;
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http.post<AuthUser>(`${this.apiRoot}/auth/login`, { email, password }).pipe(
      tap((user) => {
        this.currentUser.set(user);
        if (typeof localStorage !== 'undefined') {
          localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
        }
      })
    );
  }

  logout(): void {
    this.currentUser.set(null);
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
    this.router.navigate(['/login']);
  }

  hasRole(roles: string[]): boolean {
    const current = this.userRole();
    if (!current) return false;
    return roles.includes(current);
  }
}
