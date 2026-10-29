import { api } from "./client";
import type { CompanyRiskAssessment } from "../types";

export async function assessGroupRisk(
  groupId: number,
  signal?: AbortSignal,
): Promise<CompanyRiskAssessment[]> {
  const results = await api<CompanyRiskAssessment[]>(
    `/corporate-groups/${groupId}/risk-assessment`,
    "POST",
    undefined,
    signal,
  );
  if (
    !Array.isArray(results) ||
    results.some(
      (r) =>
        !r ||
        !Number.isSafeInteger(r.counterpartyId) ||
        r.counterpartyId < 1 ||
        typeof r.counterpartyName !== "string" ||
        typeof r.successful !== "boolean" ||
        (r.successful &&
          (typeof r.probability !== "number" ||
            !Number.isFinite(r.probability) ||
            r.probability < 0 ||
            r.probability > 1 ||
            !["LOW_RISK", "MEDIUM_RISK", "HIGH_RISK"].includes(
              r.riskLevel ?? "",
            ))),
    ) ||
    new Set(results.map((r) => r.counterpartyId)).size !== results.length
  )
    throw new Error(
      "The group assessment returned an unsupported result. Please try again.",
    );
  return results;
}
