import { api } from "./client";
import type {
  Page,
  GroupLimit,
  GroupExposure,
  Membership,
  Request,
} from "../types";
export async function allPages<T>(
  path: string,
  signal?: AbortSignal,
): Promise<T[]> {
  const result: T[] = [];
  for (let page = 0; ; page++) {
    const data = await api<Page<T>>(
      `${path}${path.includes("?") ? "&" : "?"}page=${page}&size=100`,
      "GET",
      undefined,
      signal,
    );
    if (!Array.isArray(data.content) || !Number.isInteger(data.totalPages))
      throw new Error("The service returned an incomplete directory.");
    result.push(...data.content);
    if (page + 1 >= data.totalPages) return result;
    if (page >= 999)
      throw new Error("Directory is too large. Narrow the search.");
  }
}
export async function groupPortfolio(
  signal?: AbortSignal,
): Promise<GroupExposure[]> {
  const limits = await allPages<GroupLimit>("/group-credit-limits", signal);
  return Promise.all(
    limits.map(async (g) => {
      const [exposure, headroom] = await Promise.all([
        api<number>(
          `/group-credit-limits/groups/${g.corporateGroupId}/exposure`,
          "GET",
          undefined,
          signal,
        ),
        api<number>(
          `/group-credit-limits/groups/${g.corporateGroupId}/headroom`,
          "GET",
          undefined,
          signal,
        ),
      ]);
      return { ...g, exposure: Number(exposure), headroom: Number(headroom) };
    }),
  );
}
export const memberships = (signal?: AbortSignal) =>
  api<Membership[]>("/corporate-group-memberships", "GET", undefined, signal);
export const requestDirectory = (risk: boolean, signal?: AbortSignal) =>
  allPages<Request>(
    risk
      ? "/credit-requests/search?sort=createdAt,desc"
      : "/credit-requests/my?sortBy=createdAt&direction=DESC",
    signal,
  );
