export type RateType = 'PERCENTAGE' | 'HOURLY';

export interface Professional {
  id?: string;
  name: string;
  email: string;
  password?: string;
  phone?: string;
  role: 'ARTIST' | 'OWNER' | 'ADMIN';
  specialties: string;
  artistRateType: RateType;
  artistRateValue: number;
  reputation: number;
  hireDate?: string;
  isActive: boolean;
}
