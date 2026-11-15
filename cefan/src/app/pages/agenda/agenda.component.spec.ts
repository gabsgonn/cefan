import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AgendaComponent } from './agenda.component';
import { AppointmentService } from '../../core/services/appointment.service';
import { Appointment } from '../../core/models/appointment.model';
import { of } from 'rxjs';

describe('AgendaComponent - ViewMode Persistence', () => {
  const STORAGE_KEY = 'app_view_mode';

  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should initialize viewMode to LIST by default when storage is empty', async () => {
    await TestBed.configureTestingModule({
      imports: [AgendaComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AppointmentService,
          useValue: {
            getAppointments: () => of([]),
            update: () => of({}),
            create: () => of({}),
            cancel: () => of({})
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AgendaComponent);
    const component = fixture.componentInstance;

    expect(component.viewMode()).toBe('LIST');
  });

  it('should initialize viewMode to CALENDAR when saved in localStorage', async () => {
    localStorage.setItem(STORAGE_KEY, 'CALENDAR');

    await TestBed.configureTestingModule({
      imports: [AgendaComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AppointmentService,
          useValue: {
            getAppointments: () => of([]),
            update: () => of({}),
            create: () => of({}),
            cancel: () => of({})
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AgendaComponent);
    const component = fixture.componentInstance;

    expect(component.viewMode()).toBe('CALENDAR');
  });

  it('should persist viewMode changes to localStorage', async () => {
    await TestBed.configureTestingModule({
      imports: [AgendaComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AppointmentService,
          useValue: {
            getAppointments: () => of([]),
            update: () => of({}),
            create: () => of({}),
            cancel: () => of({})
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AgendaComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.viewMode.set('CALENDAR');
    TestBed.flushEffects();

    expect(localStorage.getItem(STORAGE_KEY)).toBe('CALENDAR');

    component.viewMode.set('LIST');
    TestBed.flushEffects();

    expect(localStorage.getItem(STORAGE_KEY)).toBe('LIST');
  });
});

describe('AgendaComponent - Partitioned Schedules and Overlap Logic', () => {
  let fixture: ComponentFixture<AgendaComponent>;
  let component: AgendaComponent;

  const createMockAppointment = (overrides: Partial<Appointment> = {}): Appointment => ({
    id: 'apt-test-1',
    customerName: 'Cliente Teste',
    customerPhone: '5511999999999',
    scheduledStart: '2026-11-16T12:30:00',
    scheduledEnd: '2026-11-16T14:30:00',
    status: 'CONFIRMADO',
    isTouchup: false,
    totalPrice: 350,
    depositAmount: 105,
    ...overrides
  });

  beforeEach(async () => {
    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [AgendaComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AppointmentService,
          useValue: {
            getAppointments: () => of([]),
            update: () => of({}),
            create: () => of({}),
            cancel: () => of({}),
            getAuditLogs: () => of([])
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AgendaComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should span an appointment from 12:30 to 14:30 across slots 12:00, 13:00, and 14:00', () => {
    const apt = createMockAppointment({
      id: 'spanning-apt',
      customerName: 'Mariana Rios',
      scheduledStart: '2026-11-16T12:30:00',
      scheduledEnd: '2026-11-16T14:30:00'
    });
    component.appointments.set([apt]);

    const dateKey = '2026-11-16';

    // Hour 11: 11:00 - 12:00 -> should not intersect (12:30 is not < 12:00)
    expect(component.getSlotAppointments(dateKey, 11)).toEqual([]);

    // Hour 12: 12:00 - 13:00 -> intersects (12:30 < 13:00 && 14:30 > 12:00)
    const slot12 = component.getSlotAppointments(dateKey, 12);
    expect(slot12.length).toBe(1);
    expect(slot12[0].id).toBe('spanning-apt');

    // Hour 13: 13:00 - 14:00 -> intersects (12:30 < 14:00 && 14:30 > 13:00)
    const slot13 = component.getSlotAppointments(dateKey, 13);
    expect(slot13.length).toBe(1);
    expect(slot13[0].id).toBe('spanning-apt');

    // Hour 14: 14:00 - 15:00 -> intersects (12:30 < 15:00 && 14:30 > 14:00)
    const slot14 = component.getSlotAppointments(dateKey, 14);
    expect(slot14.length).toBe(1);
    expect(slot14[0].id).toBe('spanning-apt');

    // Hour 15: 15:00 - 16:00 -> should not intersect (14:30 is not > 15:00)
    expect(component.getSlotAppointments(dateKey, 15)).toEqual([]);
  });

  it('should return multiple partitioned appointments in the same slot sorted chronologically', () => {
    const apt1 = createMockAppointment({
      id: 'apt-part-1',
      customerName: 'Sessão Manhã Piercing',
      scheduledStart: '2026-11-16T12:00:00',
      scheduledEnd: '2026-11-16T12:30:00'
    });
    const apt2 = createMockAppointment({
      id: 'apt-part-2',
      customerName: 'Sessão Tatuagem Flash',
      scheduledStart: '2026-11-16T12:30:00',
      scheduledEnd: '2026-11-16T13:00:00'
    });

    // Provide them out of order in state to verify chronological sorting
    component.appointments.set([apt2, apt1]);

    const dateKey = '2026-11-16';
    const slot12 = component.getSlotAppointments(dateKey, 12);

    expect(slot12.length).toBe(2);
    expect(slot12[0].id).toBe('apt-part-1');
    expect(slot12[1].id).toBe('apt-part-2');

    // Hour 13 should not include apt2 because it ended exactly at 13:00:00
    expect(component.getSlotAppointments(dateKey, 13)).toEqual([]);
  });

  it('should respect slot boundary rules strictly', () => {
    const aptEndingAt12 = createMockAppointment({
      id: 'apt-end-12',
      scheduledStart: '2026-11-16T11:00:00',
      scheduledEnd: '2026-11-16T12:00:00'
    });
    const aptStartingAt13 = createMockAppointment({
      id: 'apt-start-13',
      scheduledStart: '2026-11-16T13:00:00',
      scheduledEnd: '2026-11-16T14:00:00'
    });

    component.appointments.set([aptEndingAt12, aptStartingAt13]);

    const dateKey = '2026-11-16';
    // Hour 12 (12:00 to 13:00) should NOT contain either of these boundary-adjacent appointments
    expect(component.getSlotAppointments(dateKey, 12)).toEqual([]);
  });

  it('should fallback to 1-hour duration when scheduledEnd is missing', () => {
    const aptNoEnd = createMockAppointment({
      id: 'apt-no-end',
      scheduledStart: '2026-11-16T12:15:00',
      scheduledEnd: ''
    });

    component.appointments.set([aptNoEnd]);

    const dateKey = '2026-11-16';
    // Defaults to 12:15 + 1h = 13:15
    // Should be in slot 12 (12:00 - 13:00) and slot 13 (13:00 - 14:00)
    expect(component.getSlotAppointments(dateKey, 12).length).toBe(1);
    expect(component.getSlotAppointments(dateKey, 13).length).toBe(1);
    expect(component.getSlotAppointments(dateKey, 14).length).toBe(0);
  });

  it('should pre-fill fractional start time in openCreateModalForSlot when slot has partitioned appointment', () => {
    const apt = createMockAppointment({
      id: 'apt-half-hour',
      scheduledStart: '2026-11-16T12:00:00',
      scheduledEnd: '2026-11-16T12:30:00'
    });
    component.appointments.set([apt]);

    const testDate = new Date(2026, 10, 16); // 2026-11-16
    component.openCreateModalForSlot(testDate, 12);

    expect(component.showCreateModal()).toBe(true);
    // Because an appointment ends at 12:30, pre-fill should detect :30 as next free start
    expect(component.createForm.scheduledStart).toContain('T12:30');
    expect(component.createForm.scheduledEnd).toContain('T14:30');
  });

  it('should pre-fill standard hour start time in openCreateModalForSlot for empty slot', () => {
    component.appointments.set([]);

    const testDate = new Date(2026, 10, 16);
    component.openCreateModalForSlot(testDate, 12);

    expect(component.showCreateModal()).toBe(true);
    expect(component.createForm.scheduledStart).toContain('T12:00');
    expect(component.createForm.scheduledEnd).toContain('T14:00');
  });

  it('should render "+ Adicionar" and appointment cards for occupied slots and "+ Livre" for empty slots in CALENDAR view', () => {
    fixture.detectChanges(); // triggers ngOnInit and initial load
    component.viewMode.set('CALENDAR');

    const firstColumn = component.weekColumns()[0];
    const dateKey = firstColumn.dateKey;

    const apt = createMockAppointment({
      id: 'apt-visual-1',
      customerName: 'Carlos Tatuado',
      scheduledStart: `${dateKey}T12:30:00`,
      scheduledEnd: `${dateKey}T14:30:00`,
      status: 'CONFIRMADO'
    });

    component.appointments.set([apt]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Slot at 12:00 on first column should render appointment card with customer name & time badge
    const slotCards = compiled.querySelectorAll('.slot-card');
    expect(slotCards.length).toBeGreaterThanOrEqual(1);

    const firstCard = slotCards[0];
    expect(firstCard.textContent).toContain('Carlos Tatuado');
    expect(firstCard.querySelector('.slot-time-badge')?.textContent).toContain('12:30 - 14:30');

    // Occupied slots must render "+ Adicionar" button
    const addMiniBtns = compiled.querySelectorAll('.slot-add-mini-btn');
    expect(addMiniBtns.length).toBeGreaterThanOrEqual(1);
    expect(addMiniBtns[0].textContent?.trim()).toContain('+ Adicionar');

    // Empty slots must render "+ Livre" button
    const freeBtns = compiled.querySelectorAll('.slot-free-btn');
    expect(freeBtns.length).toBeGreaterThan(0);
    expect(freeBtns[0].textContent?.trim()).toContain('+ Livre');
  });

  it('should render multiple cards and "+ Adicionar" when slot has multiple partitioned appointments', () => {
    fixture.detectChanges(); // triggers ngOnInit
    component.viewMode.set('CALENDAR');

    const firstColumn = component.weekColumns()[0];
    const dateKey = firstColumn.dateKey;

    const apt1 = createMockAppointment({
      id: 'apt-multi-1',
      customerName: 'Cliente Sessão 1',
      scheduledStart: `${dateKey}T12:00:00`,
      scheduledEnd: `${dateKey}T12:30:00`,
      status: 'CONFIRMADO'
    });
    const apt2 = createMockAppointment({
      id: 'apt-multi-2',
      customerName: 'Cliente Sessão 2',
      scheduledStart: `${dateKey}T12:30:00`,
      scheduledEnd: `${dateKey}T13:00:00`,
      status: 'AGUARDANDO_PAGAMENTO'
    });

    component.appointments.set([apt1, apt2]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.slot-card');

    expect(cards.length).toBe(2);
    expect(cards[0].textContent).toContain('Cliente Sessão 1');
    expect(cards[0].classList.contains('CONFIRMADO')).toBe(true);
    expect(cards[1].textContent).toContain('Cliente Sessão 2');
    expect(cards[1].classList.contains('AGUARDANDO_PAGAMENTO')).toBe(true);

    const addMiniBtns = compiled.querySelectorAll('.slot-add-mini-btn');
    expect(addMiniBtns.length).toBe(1);
  });

  it('should render EXPIRADO and CANCELADO cards with their respective CSS classes', () => {
    fixture.detectChanges(); // triggers ngOnInit
    component.viewMode.set('CALENDAR');

    const firstColumn = component.weekColumns()[0];
    const dateKey = firstColumn.dateKey;

    const aptExpired = createMockAppointment({
      id: 'apt-exp',
      customerName: 'Cliente Expirado',
      scheduledStart: `${dateKey}T10:00:00`,
      scheduledEnd: `${dateKey}T11:00:00`,
      status: 'EXPIRADO'
    });
    const aptCanceled = createMockAppointment({
      id: 'apt-canc',
      customerName: 'Cliente Cancelado',
      scheduledStart: `${dateKey}T11:00:00`,
      scheduledEnd: `${dateKey}T12:00:00`,
      status: 'CANCELADO'
    });

    component.appointments.set([aptExpired, aptCanceled]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.slot-card');

    expect(cards.length).toBe(2);
    expect(cards[0].classList.contains('EXPIRADO')).toBe(true);
    expect(cards[1].classList.contains('CANCELADO')).toBe(true);
  });

  it('should gracefully ignore appointments with invalid or corrupted dates', () => {
    const invalidApt = createMockAppointment({
      id: 'invalid-apt',
      scheduledStart: 'not-a-valid-date',
      scheduledEnd: 'also-invalid'
    });

    component.appointments.set([invalidApt]);
    expect(component.getSlotAppointments('2026-11-16', 12)).toEqual([]);
  });

  it('should display "Retoque (R$ 0)" for touchup appointments in the slot card', () => {
    fixture.detectChanges();
    component.viewMode.set('CALENDAR');

    const firstColumn = component.weekColumns()[0];
    const dateKey = firstColumn.dateKey;

    const touchupApt = createMockAppointment({
      id: 'apt-touchup',
      customerName: 'Cliente Retoque',
      scheduledStart: `${dateKey}T14:00:00`,
      scheduledEnd: `${dateKey}T15:00:00`,
      isTouchup: true,
      totalPrice: 0,
      depositAmount: 0
    });

    component.appointments.set([touchupApt]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const slotCard = compiled.querySelector('.slot-card');
    expect(slotCard?.textContent).toContain('Retoque (R$ 0)');
  });

  it('should select latest fractional end minute when multiple appointments end in the hour', () => {
    const apt1 = createMockAppointment({
      id: 'apt-slot-15',
      scheduledStart: '2026-11-16T12:00:00',
      scheduledEnd: '2026-11-16T12:15:00'
    });
    const apt2 = createMockAppointment({
      id: 'apt-slot-45',
      scheduledStart: '2026-11-16T12:15:00',
      scheduledEnd: '2026-11-16T12:45:00'
    });

    component.appointments.set([apt1, apt2]);

    const testDate = new Date(2026, 10, 16);
    component.openCreateModalForSlot(testDate, 12);

    expect(component.showCreateModal()).toBe(true);
    expect(component.createForm.scheduledStart).toContain('T12:45');
    expect(component.createForm.scheduledEnd).toContain('T14:45');
  });

  it('should not pick an occupied fractional minute when slot is fully covered by contiguous appointments', () => {
    const apt1 = createMockAppointment({
      id: 'apt-full-1',
      scheduledStart: '2026-11-16T12:00:00',
      scheduledEnd: '2026-11-16T12:30:00',
      status: 'CONFIRMADO'
    });
    const apt2 = createMockAppointment({
      id: 'apt-full-2',
      scheduledStart: '2026-11-16T12:30:00',
      scheduledEnd: '2026-11-16T13:00:00',
      status: 'AGUARDANDO_PAGAMENTO'
    });

    component.appointments.set([apt1, apt2]);

    const testDate = new Date(2026, 10, 16);
    component.openCreateModalForSlot(testDate, 12);

    expect(component.showCreateModal()).toBe(true);
    // Because 12:30 is occupied by apt2, it must not pre-fill 12:30; falls back to 12:00
    expect(component.createForm.scheduledStart).toContain('T12:00');
    expect(component.createForm.scheduledEnd).toContain('T14:00');
  });

  it('should treat CANCELADO and EXPIRADO appointments as physically freeing the slot for openCreateModalForSlot', () => {
    const aptCancelled = createMockAppointment({
      id: 'apt-canc-1',
      scheduledStart: '2026-11-16T12:00:00',
      scheduledEnd: '2026-11-16T12:30:00',
      status: 'CANCELADO'
    });

    component.appointments.set([aptCancelled]);

    const testDate = new Date(2026, 10, 16);
    component.openCreateModalForSlot(testDate, 12);

    // Physical slot is free starting at 12:00
    expect(component.createForm.scheduledStart).toContain('T12:00');

    const aptExpired = createMockAppointment({
      id: 'apt-exp-1',
      scheduledStart: '2026-11-16T12:00:00',
      scheduledEnd: '2026-11-16T12:30:00',
      status: 'EXPIRADO'
    });

    component.appointments.set([aptExpired]);
    component.openCreateModalForSlot(testDate, 12);

    expect(component.createForm.scheduledStart).toContain('T12:00');
  });

  it('should normalize inverted scheduledEnd (scheduledEnd <= scheduledStart) to 1 hour duration', () => {
    const aptInverted = createMockAppointment({
      id: 'apt-inverted',
      scheduledStart: '2026-11-16T12:45:00',
      scheduledEnd: '2026-11-16T12:15:00'
    });

    component.appointments.set([aptInverted]);

    const dateKey = '2026-11-16';
    // Normalized to 12:45 + 1h = 13:45, so it intersects slot 12 and slot 13
    expect(component.getSlotAppointments(dateKey, 12).length).toBe(1);
    expect(component.getSlotAppointments(dateKey, 13).length).toBe(1);
    expect(component.getSlotAppointments(dateKey, 14).length).toBe(0);
  });

  it('should safely format time range and dates without Invalid Date when dates are corrupt', () => {
    expect(component.formatTimeRange('invalid-start', '2026-11-16T13:00:00')).toBe('');
    expect(component.formatTimeRange('2026-11-16T12:00:00', 'invalid-end')).toContain('12:00');
    expect(component.formatTimeRange('2026-11-16T12:00:00', 'invalid-end')).not.toContain('Invalid Date');
    expect(component.formatTimeRange('', '')).toBe('');

    expect(component.formatDate('invalid-date')).toBe('');
  });

  it('should identify today column using local date consistently', () => {
    const now = new Date();
    const pad = (n: number) => (n < 10 ? '0' + n : n);
    const expectedTodayKey = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;

    const todayCol = component.weekColumns().find(c => c.isToday);
    expect(todayCol).toBeDefined();
    expect(todayCol?.dateKey).toBe(expectedTodayKey);
  });
});
