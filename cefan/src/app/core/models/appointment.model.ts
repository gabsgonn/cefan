export type AppointmentStatus = 'AGUARDANDO_PAGAMENTO' | 'CONFIRMADO' | 'CANCELADO' | 'EXPIRADO';

export interface Appointment {
  id: string;
  customerId?: string;
  customerName: string;
  customerPhone: string;
  scheduledStart: string;
  scheduledEnd: string;
  status: AppointmentStatus;
  isTouchup: boolean;
  totalPrice: number;
  depositAmount: number;
  mpPaymentId?: string;
  mpPixCopyPaste?: string;
  mpPaymentLink?: string;
  expiresAt?: string;
  reminder24hSent?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateAppointmentRequest {
  customerName: string;
  customerPhone: string;
  scheduledStart: string;
  scheduledEnd: string;
  isTouchup?: boolean;
  totalPrice?: number;
  depositAmount?: number;
}

export interface UpdateAppointmentRequest {
  scheduledStart?: string;
  scheduledEnd?: string;
  status?: AppointmentStatus;
  isTouchup?: boolean;
  newPrice?: number;
  reasonForChange?: string;
}
