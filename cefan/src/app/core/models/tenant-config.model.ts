export interface TenantConfig {
  id?: string;
  studioName: string;
  defaultHourlyRate: number;
  fixedSetupCost: number;
  absorbCreditCardFees: boolean;
}
