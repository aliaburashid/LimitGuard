import { describe, expect, it } from "vitest";
import {
  contributions,
  exposureTotals,
  facilityReferences,
  reconciles,
  weightedGroupUtilization,
} from "./metrics";
import type { Exposure, GroupExposure, Membership, Request } from "../types";
const exposure: Exposure = {
  creditLimitId: 2,
  counterpartyId: 3,
  limitAmount: 1000000,
  usedAmount: 200000,
  reservedAmount: 75000,
  availableHeadroom: 725000,
};
describe("analytics financial scope", () => {
  it("reconciles used, reserved and available without treating request amounts as exposure", () => {
    expect(reconciles(exposure)).toBe(true);
    expect(reconciles({ ...exposure, availableHeadroom: 800000 })).toBe(false);
    expect(
      exposureTotals([exposure, { ...exposure, creditLimitId: 4 }]),
    ).toMatchObject({
      limitAmount: 2000000,
      usedAmount: 400000,
      reservedAmount: 150000,
      availableHeadroom: 1450000,
    });
  });
  it("does not show zero capacity when no facilities were discovered", () => {
    expect(exposureTotals([])).toBeNull();
  });
  it("counts each facility once even if it has multiple requests", () => {
    const r = {
      creditLimitId: 2,
      counterpartyId: 3,
      counterpartyName: "Test company",
    } as Request;
    expect(facilityReferences([r, r])).toHaveLength(1);
  });
  it("weights group utilization by limits rather than averaging percentages", () => {
    const groups = [
      { amount: 100, exposure: 100 },
      { amount: 900, exposure: 90 },
    ] as GroupExposure[];
    expect(weightedGroupUtilization(groups)).toBe(19);
    expect(weightedGroupUtilization([])).toBeNull();
  });
  it("labels unobservable group exposure and detects mismatched snapshots", () => {
    const g = { corporateGroupId: 1, exposure: 500000 } as GroupExposure;
    const members = [
      { corporateGroupId: 1, counterpartyId: 3 },
      { corporateGroupId: 1, counterpartyId: 4 },
    ] as Membership[];
    const c = contributions(g, members, [
      { ...exposure, name: "Test company" },
    ]);
    expect(c).toMatchObject({
      memberCount: 2,
      knownCount: 1,
      inconsistent: false,
    });
    expect(c.rows[1]).toMatchObject({
      amount: 225000,
      name: "Other group exposure · facility detail unavailable",
    });
    expect(
      contributions({ ...g, exposure: 1000 }, members, [
        { ...exposure, name: "Test company" },
      ]).inconsistent,
    ).toBe(true);
  });
});
