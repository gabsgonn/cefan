import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TenantConfig } from '../models/tenant-config.model';

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

  getConfig(): Observable<TenantConfig> {
    return this.http.get<TenantConfig>(this.baseUrl);
  }

  updateConfig(config: TenantConfig): Observable<TenantConfig> {
    return this.http.put<TenantConfig>(this.baseUrl, config);
  }
}
