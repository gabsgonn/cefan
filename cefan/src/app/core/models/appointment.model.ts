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
  mpPixQrCodeBase64?: string;
  mpPaymentLink?: string;
  expiresAt?: string;
  reminder24hSent?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface AppointmentAuditLog {
  id: string;
  appointmentId: string;
  oldValue: number;
  newValue: number;
  reason: string;
  changedAt: string;
}

export type ConversationState =
  | 'MENU'
  | 'ORCAMENTO_AGUARDANDO_FOTO'
  | 'AGENDAMENTO_SELECAO_DATA'
  | 'TRANSBORDO_HUMANO';

export interface ConversationSession {
  id: string;
  customerId?: string;
  customerName: string;
  customerPhone: string;
  currentState: ConversationState;
  contextData?: string;
  lastInteractionAt: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface NotificationPreferences {
  notifyNewLead: boolean;
  notifyPaymentReceived: boolean;
  notifyHumanRequest: boolean;
}

export interface NotificationEvent {
  id: string;
  type: 'NEW_LEAD' | 'PAYMENT_RECEIVED' | 'HUMAN_REQUEST' | string;
  title: string;
  message: string;
  customerPhone?: string;
  createdAt: string;
  read: boolean;
}

export interface AuthUser {
  token: string;
  name: string;
  email: string;
  role: string;
  studioName: string;
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
