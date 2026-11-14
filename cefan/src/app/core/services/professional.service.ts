import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Professional } from '../models/professional.model';

@Injectable({
  providedIn: 'root'
})
export class ProfessionalService {
  private readonly http = inject(HttpClient);
  private readonly isBrowser = typeof window !== 'undefined' && !!window.location;
  private readonly apiRoot =
    this.isBrowser && window.location.port === '4200'
      ? '/api/v1'
      : (this.isBrowser && window.location.hostname ? `http://${window.location.hostname}:8082/api/v1` : 'http://localhost:8082/api/v1');

  private readonly baseUrl = `${this.apiRoot}/professionals`;

  getProfessionals(all: boolean = true): Observable<Professional[]> {
    let params = new HttpParams();
    if (all) {
      params = params.set('all', 'true');
    }
    return this.http.get<Professional[]>(this.baseUrl, { params });
  }

  listAll(onlyActive: boolean = true): Observable<Professional[]> {
    return this.getProfessionals(!onlyActive);
  }

  getById(id: string): Observable<Professional> {
    return this.http.get<Professional>(`${this.baseUrl}/${id}`);
  }

  create(prof: Partial<Professional>): Observable<Professional> {
    return this.http.post<Professional>(this.baseUrl, prof);
  }

  update(id: string, prof: Partial<Professional>): Observable<Professional> {
    return this.http.put<Professional>(`${this.baseUrl}/${id}`, prof);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
