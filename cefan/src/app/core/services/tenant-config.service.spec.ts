import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TenantConfigService } from './tenant-config.service';
import { TenantConfig } from '../models/tenant-config.model';

describe('TenantConfigService', () => {
  let service: TenantConfigService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TenantConfigService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(TenantConfigService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should fetch studio configuration', () => {
    const mockConfig: TenantConfig = {
      studioName: 'Vício Tattoo',
      defaultHourlyRate: 150.00,
      fixedSetupCost: 50.00,
      absorbCreditCardFees: true
    };

    service.getConfig().subscribe(config => {
      expect(config.studioName).toBe('Vício Tattoo');
      expect(config.defaultHourlyRate).toBe(150.00);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/tenant-configs');
    expect(req.request.method).toBe('GET');
    req.flush(mockConfig);
    httpMock.verify();
  });

  it('should update studio configuration', () => {
    const updatedConfig: TenantConfig = {
      studioName: 'Vício Tattoo Pro',
      defaultHourlyRate: 180.00,
      fixedSetupCost: 60.00,
      absorbCreditCardFees: false
    };

    service.updateConfig(updatedConfig).subscribe(res => {
      expect(res.studioName).toBe('Vício Tattoo Pro');
      expect(res.defaultHourlyRate).toBe(180.00);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/tenant-configs');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.studioName).toBe('Vício Tattoo Pro');
    req.flush(updatedConfig);
    httpMock.verify();
  });
});
