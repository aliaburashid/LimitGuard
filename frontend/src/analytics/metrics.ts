import type { Exposure, GroupExposure, Membership, Request } from "../types";
export const bhd = (value: number) =>
  `BHD ${Number(value).toLocaleString("en-GB", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
export const compact = (value: number) =>
  new Intl.NumberFormat("en-GB", {
    notation: "compact",
    maximumFractionDigits: 2,
  })
    .format(value)
    .replace(/bn$/, "B")
    .replace(/m$/, "M")
    .replace(/k$/, "K");
export const percent = (part: number, whole: number) =>
  whole > 0 ? (100 * part) / whole : null;
export interface Facility extends Exposure {
  name: string;
  status?: string;
}
export function facilityReferences(requests: Request[]) {
  const refs = new Map<
    number,
    { id: number; name: string; counterpartyId: number; status?: string }
  >();
  for (const r of requests)
    if (!refs.has(r.creditLimitId))
      refs.set(r.creditLimitId, {
        id: r.creditLimitId,
        name: r.counterpartyName,
        counterpartyId: r.counterpartyId,
        status: r.counterpartyStatus,
      });
  return [...refs.values()];
}
export function exposureTotals(facilities: Exposure[]): Exposure | null {
  if (!facilities.length) return null;
  return facilities.reduce(
    (sum, f) => ({
      ...sum,
      limitAmount: sum.limitAmount + Number(f.limitAmount),
      usedAmount: sum.usedAmount + Number(f.usedAmount),
      reservedAmount: sum.reservedAmount + Number(f.reservedAmount),
      availableHeadroom: sum.availableHeadroom + Number(f.availableHeadroom),
    }),
    {
      creditLimitId: 0,
      counterpartyId: 0,
      limitAmount: 0,
      usedAmount: 0,
      reservedAmount: 0,
      availableHeadroom: 0,
    },
  );
}
export function reconciles(e: Exposure) {
  const values = [
    e.limitAmount,
    e.usedAmount,
    e.reservedAmount,
    e.availableHeadroom,
  ].map(Number);
  return (
    values.every((n) => Number.isFinite(n) && n >= 0) &&
    Math.round(Number(e.limitAmount) * 100) ===
      Math.round(
        (Number(e.usedAmount) +
          Number(e.reservedAmount) +
          Number(e.availableHeadroom)) *
          100,
      )
  );
}
export function weightedGroupUtilization(groups: GroupExposure[]) {
  return percent(
    groups.reduce((sum, g) => sum + Number(g.exposure), 0),
    groups.reduce((sum, g) => sum + Number(g.amount), 0),
  );
}
export function contributions(
  group: GroupExposure,
  members: Membership[],
  facilities: Facility[],
) {
  const ids = new Set(
    members
      .filter((m) => m.corporateGroupId === group.corporateGroupId)
      .map((m) => m.counterpartyId),
  );
  const rows = facilities
    .filter((f) => ids.has(f.counterpartyId))
    .map((f) => ({
      name: f.name,
      amount: Number(f.usedAmount) + Number(f.reservedAmount),
      counterpartyId: f.counterpartyId,
    }));
  const known = rows.reduce((sum, r) => sum + r.amount, 0);
  const residual = Math.round((Number(group.exposure) - known) * 100) / 100;
  return {
    rows: [
      ...rows,
      ...(residual > 0
        ? [
            {
              name: "Other group exposure · facility detail unavailable",
              amount: residual,
              counterpartyId: 0,
            },
          ]
        : []),
    ],
    inconsistent: residual < -0.01,
    memberCount: ids.size,
    knownCount: rows.length,
  };
}
export function sameProfile(a: unknown, b: unknown) {
  return JSON.stringify(a) === JSON.stringify(b);
}
