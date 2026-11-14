export interface TenantConfig {
  id?: string;
  studioName: string;
  defaultHourlyRate: number;
  fixedSetupCost: number;
  absorbCreditCardFees: boolean;
  signalPercentage?: number;
  minDepositAmount?: number;
  welcomeMessage?: string;
  orcamentoIntroMessage?: string;
  agendamentoIntroMessage?: string;
  pixPaymentMessage?: string;
  pixConfirmedMessage?: string;
  humanTransferMessage?: string;
  botResetMessage?: string;
}
