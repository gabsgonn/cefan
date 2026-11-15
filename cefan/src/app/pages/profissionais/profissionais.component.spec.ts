import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ProfissionaisComponent } from './profissionais.component';
import { ProfessionalService } from '../../core/services/professional.service';
import { Professional } from '../../core/models/professional.model';

describe('ProfissionaisComponent - Gestão da Equipe de 11 Especialistas', () => {
  let fixture: ComponentFixture<ProfissionaisComponent>;
  let component: ProfissionaisComponent;

  const mockProfessionals: Professional[] = [
    {
      id: 'prof-1',
      name: 'Carlos Silveira',
      email: 'carlos@viciotattoo.com',
      phone: '5541999990001',
      role: 'ARTIST',
      specialties: 'Realismo Preto e Cinza, Retratos',
      artistRateType: 'PERCENTAGE',
      artistRateValue: 60,
      reputation: 4.9,
      isActive: true
    },
    {
      id: 'prof-2',
      name: 'Mariana Rocha',
      email: 'mariana@viciotattoo.com',
      phone: '5541999990002',
      role: 'ARTIST',
      specialties: 'Fineline, Traço Fino, Florais',
      artistRateType: 'PERCENTAGE',
      artistRateValue: 60,
      reputation: 5.0,
      isActive: true
    },
    {
      id: 'prof-3',
      name: 'Larissa Piercing',
      email: 'larissa@viciotattoo.com',
      phone: '5541999990009',
      role: 'ARTIST',
      specialties: 'Body Piercing, Perfurações Corporais, Microdermal',
      artistRateType: 'HOURLY',
      artistRateValue: 80,
      reputation: 5.0,
      isActive: true
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfissionaisComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ProfessionalService,
          useValue: {
            getProfessionals: () => of(mockProfessionals),
            create: (payload: Partial<Professional>) =>
              of({
                id: 'prof-new',
                name: payload.name || 'Novo Artista',
                email: payload.email || 'novo@viciotattoo.com',
                role: 'ARTIST',
                specialties: payload.specialties || 'Old School',
                artistRateType: payload.artistRateType || 'PERCENTAGE',
                artistRateValue: payload.artistRateValue || 60,
                reputation: payload.reputation || 5.0,
                isActive: true
              }),
            update: (id: string, payload: Partial<Professional>) =>
              of({
                ...mockProfessionals[0],
                id,
                ...payload
              })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProfissionaisComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load professionals and compute tattoo artists vs body piercers metrics', () => {
    expect(component.professionals().length).toBe(3);
    expect(component.activeCount()).toBe(3);
    expect(component.tattooArtistsCount()).toBe(2);
    expect(component.piercersCount()).toBe(1);
  });

  it('should filter professionals by search query and active status', () => {
    component.onSearchChange('fineline');
    expect(component.filteredProfessionals().length).toBe(1);
    expect(component.filteredProfessionals()[0].name).toBe('Mariana Rocha');

    component.onSearchChange('');
    expect(component.filteredProfessionals().length).toBe(3);
  });

  it('should open create modal, create a new professional and add to list', () => {
    component.openCreateModal();
    expect(component.showModal()).toBe(true);
    expect(component.isEditing()).toBe(false);

    component.currentForm.name = 'Gabriel Oriental';
    component.currentForm.email = 'gabriel@viciotattoo.com';
    component.currentForm.specialties = 'Oriental, Irezumi';
    component.currentForm.artistRateType = 'PERCENTAGE';
    component.currentForm.artistRateValue = 65;

    component.saveProfessional();
    expect(component.showModal()).toBe(false);
    expect(component.professionals().length).toBe(4);
    expect(component.feedbackMessage()).toContain('cadastrado com sucesso');
  });

  it('should open edit modal, update artist rate and toggle status', () => {
    component.openEditModal(mockProfessionals[0]);
    expect(component.showModal()).toBe(true);
    expect(component.isEditing()).toBe(true);

    component.currentForm.artistRateValue = 65;
    component.saveProfessional();
    expect(component.showModal()).toBe(false);
    expect(component.professionals().find(p => p.id === 'prof-1')?.artistRateValue).toBe(65);

    component.toggleStatus(mockProfessionals[0]);
    expect(component.professionals().find(p => p.id === 'prof-1')?.isActive).toBe(false);
  });
});
