export interface TenantConfig {
  id?: string;
  studioName: string;
  logoBase64?: string | null;
  defaultHourlyRate: number;
  fixedSetupCost: number;
  absorbCreditCardFees: boolean;
  signalPercentage?: number;
  minDepositAmount?: number;
  pixExpirationMinutes?: number;
  openingTime?: string;
  closingTime?: string;
  workingDays?: string;
  welcomeMessage?: string;
  orcamentoIntroMessage?: string;
  agendamentoIntroMessage?: string;
  pixPaymentMessage?: string;
  pixConfirmedMessage?: string;
  humanTransferMessage?: string;
  botResetMessage?: string;
  absenceMessage?: string;
}
