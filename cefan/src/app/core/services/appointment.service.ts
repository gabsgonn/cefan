import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Appointment, CreateAppointmentRequest, UpdateAppointmentRequest } from '../models/appointment.model';

@Injectable({
  providedIn: 'root'
})
export class AppointmentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api/v1/appointments';

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
}
