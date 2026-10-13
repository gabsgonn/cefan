import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TenantConfig } from '../models/tenant-config.model';

@Injectable({
  providedIn: 'root'
})
export class TenantConfigService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api/v1/tenant-configs';

  getConfig(): Observable<TenantConfig> {
    return this.http.get<TenantConfig>(this.baseUrl);
  }

  updateConfig(config: TenantConfig): Observable<TenantConfig> {
    return this.http.put<TenantConfig>(this.baseUrl, config);
  }
}
