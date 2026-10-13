import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AppointmentService } from './appointment.service';
import { Appointment } from '../models/appointment.model';

describe('AppointmentService', () => {
  let service: AppointmentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AppointmentService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(AppointmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should fetch appointments with parameters', () => {
    const mockAppointments: Appointment[] = [
      {
        id: '123e4567-e89b-12d3-a456-426614174000',
        customerName: 'Cliente Teste',
        customerPhone: '5511999998888',
        scheduledStart: '2026-10-15T14:00:00Z',
        scheduledEnd: '2026-10-15T16:00:00Z',
        status: 'CONFIRMADO',
        isTouchup: false,
        totalPrice: 350,
        depositAmount: 105
      }
    ];

    service.getAppointments('CONFIRMADO').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].customerName).toBe('Cliente Teste');
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/appointments?status=CONFIRMADO');
    expect(req.request.method).toBe('GET');
    req.flush(mockAppointments);
    httpMock.verify();
  });

  it('should update appointment with audit reason', () => {
    const mockUpdated: Appointment = {
      id: '123e4567-e89b-12d3-a456-426614174000',
      customerName: 'Cliente Teste',
      customerPhone: '5511999998888',
      scheduledStart: '2026-10-15T14:00:00Z',
      scheduledEnd: '2026-10-15T16:00:00Z',
      status: 'CONFIRMADO',
      isTouchup: false,
      totalPrice: 400,
      depositAmount: 120
    };

    service.update('123e4567-e89b-12d3-a456-426614174000', {
      newPrice: 400,
      reasonForChange: 'Cliente pediu para aumentar tamanho'
    }).subscribe(res => {
      expect(res.totalPrice).toBe(400);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/appointments/123e4567-e89b-12d3-a456-426614174000');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.reasonForChange).toBe('Cliente pediu para aumentar tamanho');
    req.flush(mockUpdated);
    httpMock.verify();
  });
});
