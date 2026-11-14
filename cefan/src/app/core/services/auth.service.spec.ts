import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthUser } from '../models/appointment.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideRouter([{ path: 'login', component: class {} }]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be initially unauthenticated when storage is empty', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.currentUser()).toBeNull();
  });

  it('should successfully login and persist user', () => {
    const mockUser: AuthUser = {
      token: 'jwt-mock-token-xyz',
      name: 'Dono - Vício Tattoo',
      email: 'admin@viciotattoo.com',
      role: 'OWNER',
      studioName: 'Vício Tattoo'
    };

    service.login('admin@viciotattoo.com', 'cefan123').subscribe((res) => {
      expect(res.email).toBe('admin@viciotattoo.com');
      expect(res.role).toBe('OWNER');
    });

    const req = httpMock.expectOne('http://localhost:8082/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'admin@viciotattoo.com', password: 'cefan123' });
    req.flush(mockUser);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.token()).toBe('jwt-mock-token-xyz');
    expect(service.isOwner()).toBe(true);
    expect(service.userRole()).toBe('OWNER');
  });

  it('should successfully logout and clear state', () => {
    service.currentUser.set({
      token: 'valid-token',
      name: 'Tatuador Teste',
      email: 'artist@viciotattoo.com',
      role: 'ARTIST',
      studioName: 'Vício Tattoo'
    });
    expect(service.isAuthenticated()).toBe(true);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.currentUser()).toBeNull();
  });

  it('should verify roles with hasRole', () => {
    service.currentUser.set({
      token: 'valid-token',
      name: 'Dono',
      email: 'dono@viciotattoo.com',
      role: 'OWNER',
      studioName: 'Vício Tattoo'
    });

    expect(service.hasRole(['OWNER', 'ADMIN'])).toBe(true);
    expect(service.hasRole(['ARTIST'])).toBe(false);
  });
});
