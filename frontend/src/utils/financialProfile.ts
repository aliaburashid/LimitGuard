export const financialRatioFields = [
  { name: "returnOnAssets", label: "Return on assets (%)" },
  { name: "profitMargin", label: "Profit margin (%)" },
  { name: "fixedAssetTurnover", label: "Fixed asset turnover (×)", min: 0 },
  {
    name: "debtToTotalCapitalPercent",
    label: "Debt to total capital (%)",
    min: 0,
    max: 100,
  },
  { name: "interestCoverage", label: "Interest coverage (×)" },
  { name: "totalDebtToEbitda", label: "Total debt / EBITDA (×)" },
] as const;
export type FinancialProfileInput = Record<
  (typeof financialRatioFields)[number]["name"] | "financialYearEnd",
  string
>;

export function localToday() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function validateFinancialProfile(
  values: FinancialProfileInput,
  today = localToday(),
): string {
  for (const field of financialRatioFields) {
    const text = values[field.name].trim();
    const number = Number(text);
    if (
      !/^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?$/.test(text) ||
      !Number.isFinite(number)
    )
      return `${field.label} must be a finite number.`;
    if ("min" in field && number < field.min)
      return `${field.label} must be at least ${field.min}.`;
    if ("max" in field && number > field.max)
      return `${field.label} must not exceed ${field.max}.`;
  }
  const day = values.financialYearEnd;
  const timestamp = Date.parse(`${day}T00:00:00Z`);
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(day) ||
    !Number.isFinite(timestamp) ||
    new Date(timestamp).toISOString().slice(0, 10) !== day ||
    day.startsWith("0000")
  )
    return "Enter a valid financial year end.";
  if (day > today) return "Financial year end must be today or earlier.";
  return "";
}
