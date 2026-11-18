import { describe, expect, it } from "vitest";
import {
  validateFinancialProfile,
  type FinancialProfileInput,
} from "./financialProfile";
const valid: FinancialProfileInput = {
  returnOnAssets: "8",
  profitMargin: "12",
  fixedAssetTurnover: "1.5",
  debtToTotalCapitalPercent: "40",
  interestCoverage: "3",
  totalDebtToEbitda: "2",
  financialYearEnd: "2025-12-31",
};
describe("financial profile validation", () => {
  it("accepts all six ratios and a valid reporting date", () =>
    expect(validateFinancialProfile(valid, "2026-11-18")).toBe(""));
  it("permits negative ratios where the backend permits them", () =>
    expect(
      validateFinancialProfile(
        {
          ...valid,
          returnOnAssets: "-1",
          profitMargin: "-2",
          interestCoverage: "-3",
          totalDebtToEbitda: "-4",
        },
        "2026-11-18",
      ),
    ).toBe(""));
  it.each(["", "NaN", "Infinity", "-Infinity", "1e999", "abc", "0x10"])(
    "rejects invalid numerical input %s",
    (value) =>
      expect(
        validateFinancialProfile(
          { ...valid, returnOnAssets: value },
          "2026-11-18",
        ),
      ).not.toBe(""),
  );
  it.each([-1, 101])("rejects out-of-range debt percentage %s", (value) =>
    expect(
      validateFinancialProfile(
        { ...valid, debtToTotalCapitalPercent: String(value) },
        "2026-11-18",
      ),
    ).not.toBe(""),
  );
  it("rejects negative fixed asset turnover", () =>
    expect(
      validateFinancialProfile(
        { ...valid, fixedAssetTurnover: "-0.1" },
        "2026-11-18",
      ),
    ).not.toBe(""));
  it.each(["", "2026-11-19", "2025-02-29", "2026-04-31"])(
    "rejects missing, future or invalid dates %s",
    (value) =>
      expect(
        validateFinancialProfile(
          { ...valid, financialYearEnd: value },
          "2026-11-18",
        ),
      ).not.toBe(""),
  );
  it.each(["2024-02-29", "2026-11-18"])(
    "accepts leap days and today's date %s",
    (value) =>
      expect(
        validateFinancialProfile(
          { ...valid, financialYearEnd: value },
          "2026-11-18",
        ),
      ).toBe(""),
  );
});
