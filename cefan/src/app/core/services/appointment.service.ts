import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router, CanActivateFn } from '@angular/router';
import { Observable, tap } from 'rxjs';
import {
  Appointment,
  AppointmentAuditLog,
  AuthUser,
  ConversationSession,
  ConversationState,
  CreateAppointmentRequest,
  NotificationEvent,
  NotificationPreferences,
  UpdateAppointmentRequest
} from '../models/appointment.model';

const AUTH_STORAGE_KEY = 'cefan_auth_user';

@Injectable({
  providedIn: 'root'
})
export class AppointmentService {
  private readonly http = inject(HttpClient);
  private readonly apiRoot = 'http://localhost:8082/api/v1';
  private readonly baseUrl = 'http://localhost:8082/api/v1/appointments';

  readonly currentUser = signal<AuthUser | null>(this.loadStoredUser());

  private loadStoredUser(): AuthUser | null {
    try {
      const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(AUTH_STORAGE_KEY) : null;
      if (raw) {
        return JSON.parse(raw) as AuthUser;
      }
    } catch {
      // ignore storage errors in test environments
    }
    return {
      token: 'demo-session-token',
      name: 'Dono - Vício Tattoo',
      email: 'admin@viciotattoo.com',
      role: 'ADMIN',
      studioName: 'Vício Tattoo'
    };
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http.post<AuthUser>(`${this.apiRoot}/auth/login`, { email, password }).pipe(
      tap(user => {
        this.currentUser.set(user);
        try {
          if (typeof localStorage !== 'undefined') {
            localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
          }
        } catch {
          // ignore
        }
      })
    );
  }

  logout(): void {
    this.currentUser.set(null);
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.removeItem(AUTH_STORAGE_KEY);
      }
    } catch {
      // ignore
    }
  }

  isAuthenticated(): boolean {
    return this.currentUser() !== null;
  }

  getAppointments(status?: string, start?: string, end?: string): Observable<Appointment[]> {
    let params = new HttpParams();
    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }
    if (start) {
      params = params.set('start', start);
    }
    if (end) {
      params = params.set('end', end);
    }
    return this.http.get<Appointment[]>(this.baseUrl, { params });
  }

  getById(id: string): Observable<Appointment> {
    return this.http.get<Appointment>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateAppointmentRequest): Observable<Appointment> {
    return this.http.post<Appointment>(this.baseUrl, request);
  }

  update(id: string, request: UpdateAppointmentRequest): Observable<Appointment> {
    return this.http.patch<Appointment>(`${this.baseUrl}/${id}`, request);
  }

  cancel(id: string, reason: string = 'Cancelado pelo estúdio'): Observable<Appointment> {
    return this.http.patch<Appointment>(`${this.baseUrl}/${id}`, {
      status: 'CANCELADO',
      reasonForChange: reason
    });
  }

  getAuditLogs(appointmentId: string): Observable<AppointmentAuditLog[]> {
    return this.http.get<AppointmentAuditLog[]>(`${this.baseUrl}/${appointmentId}/audit-logs`);
  }

  getConversations(state?: ConversationState | 'ALL'): Observable<ConversationSession[]> {
    let params = new HttpParams();
    if (state && state !== 'ALL') {
      params = params.set('state', state);
    }
    return this.http.get<ConversationSession[]>(`${this.apiRoot}/conversations`, { params });
  }

  sendHumanMessage(sessionId: string, message: string): Observable<ConversationSession> {
    return this.http.post<ConversationSession>(`${this.apiRoot}/conversations/${sessionId}/messages`, {
      message
    });
  }

  updateConversationState(
    sessionId: string,
    state: ConversationState,
    notifyCustomer: boolean = true
  ): Observable<ConversationSession> {
    return this.http.patch<ConversationSession>(`${this.apiRoot}/conversations/${sessionId}/state`, {
      state,
      notifyCustomer
    });
  }

  getNotifications(): Observable<NotificationEvent[]> {
    return this.http.get<NotificationEvent[]>(`${this.apiRoot}/notifications`);
  }

  getNotificationPreferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(`${this.apiRoot}/notifications/preferences`);
  }

  updateNotificationPreferences(prefs: NotificationPreferences): Observable<NotificationPreferences> {
    return this.http.put<NotificationPreferences>(`${this.apiRoot}/notifications/preferences`, prefs);
  }
}

export const authGuard: CanActivateFn = () => {
  const service = inject(AppointmentService);
  const router = inject(Router);
  if (service.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};
