import {
  Component,
  Input,
  Output,
  EventEmitter,
  HostListener,
  OnDestroy,
  ElementRef,
  ViewChild,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      *ngIf="isRendered"
      class="modal-backdrop"
      [class.closing]="isClosing"
      [class.dragging]="isDragging()"
      [style.opacity]="backdropOpacity()"
      (mousedown)="onBackdropMouseDown($event)"
      (click)="onBackdropClick($event)"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="title || 'Modal'"
    >
      <div
        #contentRef
        class="modal-content"
        [class.closing]="isClosing && !isDismissingByDrag()"
        [class.dragging]="isDragging()"
        [class.snapping]="isSnappingBack()"
        [class.dismissing-drag]="isDismissingByDrag()"
        [style.max-width]="maxWidth"
        [style.transform]="sheetTransform()"
        (click)="$event.stopPropagation()"
      >
        <!-- Mobile Bottom-Sheet Drag Handle -->
        <div
          class="modal-sheet-handle"
          (pointerdown)="onSheetPointerDown($event)"
          (pointermove)="onSheetPointerMove($event)"
          (pointerup)="onSheetPointerUp($event)"
          (pointercancel)="onSheetPointerUp($event)"
          aria-hidden="true"
        >
          <span class="modal-sheet-pill"></span>
        </div>

        <div
          class="modal-header"
          *ngIf="title || hasCustomHeader"
          (pointerdown)="onSheetPointerDown($event)"
          (pointermove)="onSheetPointerMove($event)"
          (pointerup)="onSheetPointerUp($event)"
          (pointercancel)="onSheetPointerUp($event)"
        >
          <ng-content select="[modal-header]"></ng-content>
          <h3 *ngIf="title">{{ title }}</h3>
          <button
            *ngIf="showCloseButton"
            type="button"
            class="close-btn"
            (click)="requestClose()"
            aria-label="Fechar"
          >×</button>
        </div>

        <div class="modal-body">
          <ng-content select="[modal-body]"></ng-content>
          <ng-content></ng-content>
        </div>

        <div class="modal-footer" *ngIf="showFooter">
          <ng-content select="[modal-footer]"></ng-content>
        </div>
      </div>
    </div>
  `
})
export class ModalComponent implements OnDestroy {
  @Input() title: string = '';
  @Input() maxWidth: string = '540px';
  @Input() isDirty: boolean = false;
  @Input() confirmMessage: string = 'Tem certeza que deseja fechar?';
  @Input() showCloseButton: boolean = true;
  @Input() closeOnBackdrop: boolean = true;
  @Input() closeOnEsc: boolean = true;
  @Input() showFooter: boolean = true;
  @Input() hasCustomHeader: boolean = false;

  @Output() isOpenChange = new EventEmitter<boolean>();
  @Output() closed = new EventEmitter<void>();

  @ViewChild('contentRef') contentRef?: ElementRef<HTMLDivElement>;

  isRendered = false;
  isClosing = false;

  readonly isDragging = signal(false);
  readonly isSnappingBack = signal(false);
  readonly isDismissingByDrag = signal(false);
  readonly dragOffsetY = signal(0);

  private _isOpen = false;
  private closeTimer: any = null;
  private snapTimer: any = null;
  private backdropClickedDirectly = false;
  private dragStartY = 0;
  private dragStartTime = 0;
  private activePointerId: number | null = null;

  sheetTransform(): string | null {
    if (this.isDismissingByDrag()) {
      return 'translateY(100%)';
    }
    if (this.isDragging() || this.isSnappingBack()) {
      return `translateY(${this.dragOffsetY()}px)`;
    }
    return null;
  }

  backdropOpacity(): number | null {
    if (!this.isDragging()) return null;
    const offset = Math.max(0, this.dragOffsetY());
    return Math.max(0.2, 1 - offset / 420);
  }

  onSheetPointerDown(event: PointerEvent): void {
    // Only enable sheet drag on mobile bottom-sheet layout (<= 640px)
    if (typeof window === 'undefined' || window.innerWidth > 640) return;
    if (this.isClosing) return;

    const target = event.target as HTMLElement | null;
    if (target?.closest('button, a, input, select, textarea')) return;

    if (this.snapTimer) {
      clearTimeout(this.snapTimer);
      this.snapTimer = null;
    }

    this.activePointerId = event.pointerId;
    this.dragStartY = event.clientY;
    this.dragStartTime = performance.now();
    this.isSnappingBack.set(false);
    this.isDragging.set(true);
    this.dragOffsetY.set(0);

    try {
      (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    } catch {
      // Ignore if pointer capture is unavailable
    }
  }

  onSheetPointerMove(event: PointerEvent): void {
    if (!this.isDragging() || event.pointerId !== this.activePointerId) return;

    const rawDelta = event.clientY - this.dragStartY;
    if (rawDelta >= 0) {
      this.dragOffsetY.set(rawDelta);
    } else {
      // Subtle iOS rubber-band resistance when pulling upward
      this.dragOffsetY.set(Math.max(-24, rawDelta * 0.18));
    }
  }

  onSheetPointerUp(event: PointerEvent): void {
    if (!this.isDragging() || event.pointerId !== this.activePointerId) return;

    try {
      (event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId);
    } catch {
      // Ignore
    }

    const deltaY = event.clientY - this.dragStartY;
    const elapsed = Math.max(1, performance.now() - this.dragStartTime);
    const velocity = deltaY / elapsed; // px/ms

    this.activePointerId = null;
    this.isDragging.set(false);

    const shouldClose = deltaY > 110 || (deltaY > 40 && velocity > 0.45);
    if (shouldClose) {
      this.requestClose();
      // If user cancelled dirty confirmation, snap back smoothly
      if (!this.isClosing) {
        this.snapBack();
      } else {
        this.isDismissingByDrag.set(true);
      }
    } else {
      this.snapBack();
    }
  }

  private snapBack(): void {
    this.isSnappingBack.set(true);
    this.dragOffsetY.set(0);
    if (this.snapTimer) {
      clearTimeout(this.snapTimer);
    }
    this.snapTimer = setTimeout(() => {
      this.isSnappingBack.set(false);
      this.snapTimer = null;
    }, 280);
  }

  @Input()
  set isOpen(val: boolean) {
    if (val) {
      this.open();
    } else {
      this.closeProgrammatically();
    }
  }

  get isOpen(): boolean {
    return this._isOpen;
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: Event): void {
    if (this.isRendered && !this.isClosing && this.closeOnEsc) {
      event.preventDefault();
      this.requestClose();
    }
  }

  onBackdropMouseDown(event: MouseEvent): void {
    this.backdropClickedDirectly = (event.target === event.currentTarget);
  }

  onBackdropClick(event: MouseEvent): void {
    if (this.closeOnBackdrop && this.backdropClickedDirectly && event.target === event.currentTarget) {
      this.requestClose();
    }
    this.backdropClickedDirectly = false;
  }

  /**
   * Called when user requests close (e.g. clicking 'X', clicking backdrop, pressing Escape, or clicking Cancel)
   * If isDirty is true, prompts confirmation before closing.
   */
  requestClose(): void {
    if (this.isClosing || !this.isRendered) return;

    if (this.isDirty) {
      const confirmed = window.confirm(this.confirmMessage || 'Tem certeza que deseja fechar?');
      if (!confirmed) {
        return;
      }
    }

    this.animateAndClose(true);
  }

  /**
   * Force closes the modal without checking dirty state (e.g. after successful save)
   */
  forceClose(): void {
    if (!this.isRendered) return;
    this.animateAndClose(true);
  }

  private open(): void {
    if (this.closeTimer) {
      clearTimeout(this.closeTimer);
      this.closeTimer = null;
    }
    this._isOpen = true;
    this.isClosing = false;
    this.isDragging.set(false);
    this.isSnappingBack.set(false);
    this.isDismissingByDrag.set(false);
    this.dragOffsetY.set(0);
    this.isRendered = true;
  }

  private closeProgrammatically(): void {
    if (!this.isRendered || this.isClosing) {
      this._isOpen = false;
      return;
    }
    this._isOpen = false;
    this.animateAndClose(false);
  }

  private animateAndClose(emitChange: boolean): void {
    this.isClosing = true;
    if (this.closeTimer) {
      clearTimeout(this.closeTimer);
    }
    this.closeTimer = setTimeout(() => {
      this.isClosing = false;
      this.isRendered = false;
      this.isDismissingByDrag.set(false);
      this.dragOffsetY.set(0);
      this._isOpen = false;
      this.closeTimer = null;
      if (emitChange) {
        this.isOpenChange.emit(false);
      }
      this.closed.emit();
    }, 200);
  }

  ngOnDestroy(): void {
    if (this.closeTimer) {
      clearTimeout(this.closeTimer);
      this.closeTimer = null;
    }
    if (this.snapTimer) {
      clearTimeout(this.snapTimer);
      this.snapTimer = null;
    }
  }
}
