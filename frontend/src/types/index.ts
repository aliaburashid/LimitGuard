export type Role = "ADMIN" | "RISK_OFFICER" | "RELATIONSHIP_MANAGER";
export interface Profile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: Role;
  financialInstitution: { id: number; name: string };
  profilePicturePath: string | null;
}
export type CounterpartyStatus = "ACTIVE" | "FROZEN" | "CLOSED";
export interface Counterparty {
  id: number;
  name: string;
  status: CounterpartyStatus;
  createdAt: string;
  updatedAt: string;
}
export interface Request {
  id: number;
  amount: number;
  status: string;
  creditLimitId: number;
  counterpartyId: number;
  requesterId: number;
  requesterName: string;
  counterpartyName: string;
  counterpartyStatus: CounterpartyStatus;
  financialInstitutionId: number;
  financialInstitutionName: string;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface Exposure {
  creditLimitId: number;
  counterpartyId: number;
  limitAmount: number;
  usedAmount: number;
  reservedAmount: number;
  availableHeadroom: number;
}
export interface Review extends Exposure {
  creditRequestId: number;
  requestedAmount: number;
  status: string;
  requesterId: number;
  requesterName: string;
  counterpartyName: string;
  counterpartyStatus: CounterpartyStatus;
  financialInstitutionId: number;
  financialInstitutionName: string;
  expiresAt: string | null;
  createdAt: string;
}
export interface Audit {
  id: number;
  action: string;
  entityType: string;
  entityId: number;
  actorId: number;
  actorName: string;
  createdAt: string;
  details: string;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
export interface Membership {
  id: number;
  corporateGroupId: number;
  corporateGroupName: string;
  counterpartyId: number;
}
export interface GroupLimit {
  id: number;
  corporateGroupId: number;
  corporateGroupName: string;
  financialInstitutionId: number;
  amount: number;
}
export interface GroupExposure extends GroupLimit {
  exposure: number;
  headroom: number;
}
export interface FinancialProfile {
  counterpartyId: number;
  counterpartyName: string;
  returnOnAssets: number;
  profitMargin: number;
  fixedAssetTurnover: number;
  debtToTotalCapitalPercent: number;
  interestCoverage: number;
  totalDebtToEbitda: number;
  financialYearEnd: string;
  updatedAt: string;
}
export interface Prediction {
  probability: number;
  riskLevel: string;
  modelVersion: string;
}
