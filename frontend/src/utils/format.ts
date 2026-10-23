export const label = (s: string) =>
  s
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/^./, (x) => x.toUpperCase());
const dinars = new Intl.NumberFormat("en-GB", {
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});
export const money = (n: number) => `BD ${dinars.format(n)}`;
export const requestReference = (id: number) =>
  `CR-${String(id).padStart(6, "0")}`;
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
