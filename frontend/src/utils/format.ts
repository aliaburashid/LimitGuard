export const label = (s: string) =>
  s
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/^./, (x) => x.toUpperCase());
export const money = (n: number, compact = false) =>
  new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency: "GBP",
    notation: compact ? "compact" : "standard",
    maximumFractionDigits: compact ? 2 : 2,
  }).format(n);
export const date = (s: string | null) =>
  s
    ? new Date(s).toLocaleString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
export const utilization = (e: {
  limitAmount: number;
  usedAmount: number;
  reservedAmount: number;
}) =>
  e.limitAmount > 0
    ? ((Number(e.usedAmount) + Number(e.reservedAmount)) /
        Number(e.limitAmount)) *
      100
    : 0;
