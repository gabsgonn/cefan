import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { TenantConfig } from '../models/tenant-config.model';

export interface StudioBranding {
  studioName: string;
  logoBase64?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class TenantConfigService {
  private readonly http = inject(HttpClient);
  private readonly isBrowser = typeof window !== 'undefined' && !!window.location;
  private readonly baseUrl =
    this.isBrowser && window.location.port === '4200'
      ? '/api/v1/tenant-configs'
      : (this.isBrowser && window.location.hostname ? `http://${window.location.hostname}:8082/api/v1/tenant-configs` : 'http://localhost:8082/api/v1/tenant-configs');

  readonly branding = signal<StudioBranding | null>(null);

  getBranding(): Observable<StudioBranding> {
    return this.http.get<StudioBranding>(`${this.baseUrl}/branding`).pipe(
      tap((data) => this.branding.set(data))
    );
  }

  loadBranding(): void {
    this.getBranding().subscribe({
      error: () => {}
    });
  }

  getConfig(): Observable<TenantConfig> {
    return this.http.get<TenantConfig>(this.baseUrl).pipe(
      tap((data) => this.branding.set({ studioName: data.studioName, logoBase64: data.logoBase64 ?? null }))
    );
  }

  updateConfig(config: TenantConfig): Observable<TenantConfig> {
    return this.http.put<TenantConfig>(this.baseUrl, config).pipe(
      tap((updated) => this.branding.set({ studioName: updated.studioName, logoBase64: updated.logoBase64 ?? null }))
    );
  }

  clearBranding(): void {
    this.branding.set(null);
  }
}
