import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ModalComponent } from './modal.component';

describe('ModalComponent', () => {
  let component: ModalComponent;
  let fixture: ComponentFixture<ModalComponent>;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [ModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ModalComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should create the modal component', () => {
    expect(component).toBeTruthy();
  });

  it('should not render backdrop when isOpen is false', () => {
    component.isOpen = false;
    fixture.detectChanges();

    const backdrop = fixture.nativeElement.querySelector('.modal-backdrop');
    expect(backdrop).toBeNull();
  });

  it('should render backdrop and title when isOpen is true', () => {
    component.title = 'Teste de Modal';
    component.isOpen = true;
    fixture.detectChanges();

    const backdrop = fixture.nativeElement.querySelector('.modal-backdrop');
    const titleEl = fixture.nativeElement.querySelector('.modal-header h3');

    expect(backdrop).toBeTruthy();
    expect(titleEl?.textContent).toContain('Teste de Modal');
  });

  it('should close without prompt when isDirty is false', () => {
    const confirmSpy = vi.spyOn(window, 'confirm');
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.isOpen = true;
    component.isDirty = false;
    fixture.detectChanges();

    component.requestClose();
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(component.isClosing).toBe(true);

    vi.advanceTimersByTime(250);
    expect(component.isRendered).toBe(false);
    expect(closedSpy).toHaveBeenCalled();
  });

  it('should prompt confirmation when isDirty is true and abort if cancelled', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.isOpen = true;
    component.isDirty = true;
    fixture.detectChanges();

    component.requestClose();
    expect(confirmSpy).toHaveBeenCalledWith('Tem certeza que deseja fechar?');
    expect(component.isClosing).toBe(false);
    expect(component.isRendered).toBe(true);

    vi.advanceTimersByTime(250);
    expect(closedSpy).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('should prompt confirmation when isDirty is true and close if confirmed', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.isOpen = true;
    component.isDirty = true;
    fixture.detectChanges();

    component.requestClose();
    expect(confirmSpy).toHaveBeenCalledWith('Tem certeza que deseja fechar?');
    expect(component.isClosing).toBe(true);

    vi.advanceTimersByTime(250);
    expect(component.isRendered).toBe(false);
    expect(closedSpy).toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('should close when backdrop is clicked directly', () => {
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.isOpen = true;
    component.isDirty = false;
    fixture.detectChanges();

    const backdrop = fixture.nativeElement.querySelector('.modal-backdrop') as HTMLElement;
    expect(backdrop).toBeTruthy();

    backdrop.dispatchEvent(new MouseEvent('mousedown'));
    backdrop.dispatchEvent(new MouseEvent('click'));

    expect(component.isClosing).toBe(true);
    vi.advanceTimersByTime(250);
    expect(closedSpy).toHaveBeenCalled();
  });

  it('should close on Escape key', () => {
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.isOpen = true;
    component.isDirty = false;
    fixture.detectChanges();

    const event = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
    document.dispatchEvent(event);

    expect(component.isClosing).toBe(true);
    vi.advanceTimersByTime(250);
    expect(closedSpy).toHaveBeenCalled();
  });

  it('should forceClose without prompt even if isDirty is true', () => {
    const confirmSpy = vi.spyOn(window, 'confirm');
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.isOpen = true;
    component.isDirty = true;
    fixture.detectChanges();

    component.forceClose();
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(component.isClosing).toBe(true);

    vi.advanceTimersByTime(250);
    expect(closedSpy).toHaveBeenCalled();
  });
});
