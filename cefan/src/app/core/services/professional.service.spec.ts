import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProfessionalService } from './professional.service';
import { Professional } from '../models/professional.model';

describe('ProfessionalService', () => {
  let service: ProfessionalService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProfessionalService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ProfessionalService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch all professionals', () => {
    const mockList: Professional[] = [
      {
        id: '1',
        name: 'Carlos Eduardo',
        email: 'carlos@viciotattoo.com',
        role: 'ARTIST',
        specialties: 'Fineline, Minimalista',
        artistRateType: 'PERCENTAGE',
        artistRateValue: 60,
        reputation: 4.9,
        isActive: true
      }
    ];

    service.getProfessionals(true).subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].name).toBe('Carlos Eduardo');
    });

    const req = httpMock.expectOne('http://localhost:8082/api/v1/professionals?all=true');
    expect(req.request.method).toBe('GET');
    req.flush(mockList);
  });

  it('should create new professional', () => {
    const newProf: Partial<Professional> = {
      name: 'Mariana Rocha',
      email: 'mariana@viciotattoo.com',
      role: 'ARTIST',
      specialties: 'Realismo Preto e Cinza',
      artistRateType: 'PERCENTAGE',
      artistRateValue: 65,
      reputation: 5.0
    };

    service.create(newProf).subscribe((res) => {
      expect(res.name).toBe('Mariana Rocha');
    });

    const req = httpMock.expectOne('http://localhost:8082/api/v1/professionals');
    expect(req.request.method).toBe('POST');
    req.flush({ ...newProf, id: '2', isActive: true });
  });
});
