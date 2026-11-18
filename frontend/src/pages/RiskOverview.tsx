import {
  ArrowRight,
  ArrowUpRight,
  Clock3,
  ShieldCheck,
  Wallet,
} from "lucide-react";
import { usePortfolio } from "../hooks/usePortfolio";
import { ChartCard } from "../components/AnalyticsCharts";
import { CompanyLogo, CorporateGroupLogo } from "../components/CompanyLogo";
import { counterpartyLogoDomain } from "../config/verifiedLogoDomains";
import { Badge, Empty, ErrorBox, Loading } from "../components/ui";
import { bhd, compact, exposureTotals, percent } from "../analytics/metrics";
import { date, money, requestReference } from "../utils/format";

export function RiskOverview({
  version,
  navigate,
  assess,
}: {
  version: number;
  navigate: (page: string, recordId?: number, status?: string) => void;
  assess: (id: number) => void;
}) {
  const { requests, groups, facilities, errors } = usePortfolio(version);
  const pending = requests?.filter((r) => r.status === "PENDING_APPROVAL");
  const priorities = pending
    ? [...pending]
        .sort(
          (a, b) =>
            Number(b.amount) - Number(a.amount) ||
            a.createdAt.localeCompare(b.createdAt),
        )
        .slice(0, 3)
    : null;
  const totals = facilities ? exposureTotals(facilities) : null;
  const committed = totals
    ? Number(totals.usedAmount) + Number(totals.reservedAmount)
    : null;
  // Show recorded updates, not inferred approvals or a fabricated event history.
  const recent = requests
    ? requests
        .filter((r) =>
          ["RESERVED", "USED", "REJECTED", "EXPIRED", "CANCELLED"].includes(
            r.status,
          ),
        )
        .sort(
          (a, b) =>
            new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime() ||
            b.id - a.id,
        )
        .slice(0, 4)
    : null;
  const rankedGroups = groups
    ? [...groups]
        .sort(
          (a, b) =>
            (percent(Number(b.exposure), Number(b.amount)) ?? -1) -
              (percent(Number(a.exposure), Number(a.amount)) ?? -1) ||
            Number(b.exposure) - Number(a.exposure),
        )
        .slice(0, 5)
    : null;
  const hasErrors = errors.length > 0;

  return (
    <div className="risk-overview ro-overview">
      <header className="ro-overview-heading">
        <div>
          <h1>Credit Portfolio Overview</h1>
        </div>
        <button className="primary" onClick={() => navigate("approvals")}>
          Open approval queue <ArrowUpRight size={16} />
        </button>
      </header>
      {hasErrors && <ErrorBox message={[...new Set(errors)].join(" ")} />}
      <div className="overview-kpis ro-kpis">
        <div className="overview-kpi">
          <div>
            <span>Pending approvals</span>
            <Clock3 size={17} />
          </div>
          <strong>{pending?.length ?? "—"}</strong>
          <p>
            {pending
              ? `${money(pending.reduce((s, r) => s + Number(r.amount), 0))} awaiting decision`
              : "Loading requests…"}
          </p>
        </div>
        <div className="overview-kpi">
          <div>
            <span>Committed exposure</span>
            <Wallet size={17} />
          </div>
          <strong title={committed !== null ? bhd(committed) : undefined}>
            <small>BHD</small> {committed !== null ? compact(committed) : "—"}
          </strong>
          <p>Used and reserved credit</p>
        </div>
        <div className="overview-kpi">
          <div>
            <span>Available headroom</span>
            <ShieldCheck size={17} />
          </div>
          <strong title={totals ? bhd(totals.availableHeadroom) : undefined}>
            <small>BHD</small>{" "}
            {totals ? compact(totals.availableHeadroom) : "—"}
          </strong>
          <p>Remaining individual facility capacity</p>
        </div>
      </div>
      <div className="ro-overview-grid">
        <ChartCard
          title="Corporate group exposure"
          subtitle="Top five by utilization"
          className="ro-group-exposure"
          action={
            <button
              className="text-button"
              onClick={() => navigate("corporate-groups")}
            >
              View all groups <ArrowRight size={14} />
            </button>
          }
        >
          {rankedGroups?.length ? (
            <div
              className="ro-group-chart"
              role="group"
              aria-label="Corporate group utilization"
            >
              {rankedGroups.map((g) => {
                const value = percent(Number(g.exposure), Number(g.amount));
                const description = `${g.corporateGroupName}: ${value === null ? "utilization unavailable" : `${value.toFixed(1)}% utilized`}. Committed ${bhd(g.exposure)}; approved limit ${bhd(Number(g.amount))}.`;
                return (
                  <button
                    key={g.corporateGroupId}
                    className="ro-group-row"
                    onClick={() => navigate("facilities")}
                    aria-label={description}
                    title={description}
                  >
                    <CorporateGroupLogo name={g.corporateGroupName} />
                    <span className="ro-group-bar-label">
                      <strong>{g.corporateGroupName}</strong>
                      <span className="ro-group-track" aria-hidden="true">
                        <span
                          style={{
                            width: `${Math.min(100, Math.max(0, value ?? 0))}%`,
                          }}
                        />
                      </span>
                    </span>
                    <span
                      className={`ro-group-value${value !== null && value >= 85 ? " ro-elevated" : ""}`}
                    >
                      {value === null ? "—" : `${value.toFixed(1)}%`}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : !groups && !hasErrors ? (
            <Loading />
          ) : (
            <Empty title="No group exposure available">
              Configured group limits will appear here.
            </Empty>
          )}
        </ChartCard>
        <ChartCard
          title="Pending decisions"
          subtitle="Highest requested amounts first"
          className="ro-pending"
          action={
            <button
              className="text-button"
              onClick={() => navigate("approvals")}
            >
              View queue <ArrowRight size={14} />
            </button>
          }
        >
          {priorities?.length ? (
            <div className="ro-decision-list">
              {priorities.map((r) => (
                <button
                  key={r.id}
                  className="ro-decision"
                  onClick={() => assess(r.id)}
                  aria-label={`Review ${r.counterpartyName}, ${requestReference(r.id)}, ${money(r.amount)}`}
                >
                  <CompanyLogo
                    name={r.counterpartyName}
                    domain={counterpartyLogoDomain(r.counterpartyName)}
                  />
                  <span className="ro-decision-company">
                    <strong>{r.counterpartyName}</strong>
                    <small>{requestReference(r.id)}</small>
                  </span>
                  <span className="ro-decision-amount">
                    <strong>{money(r.amount)}</strong>
                    <small>
                      Review <ArrowUpRight size={12} />
                    </small>
                  </span>
                </button>
              ))}
            </div>
          ) : priorities ? (
            <Empty title="No pending approvals">
              No requests are awaiting a decision.
            </Empty>
          ) : hasErrors ? (
            <Empty title="Pending decisions unavailable">
              Refresh the workspace to try again.
            </Empty>
          ) : (
            <Loading />
          )}
        </ChartCard>
      </div>
      <ChartCard
        title="Recent credit activity"
        className="ro-recent-activity"
        action={
          <button
            className="text-button"
            onClick={() => navigate("approvals", undefined, "ALL")}
          >
            View all <ArrowRight size={14} />
          </button>
        }
      >
        {recent?.length ? (
          <div className="ro-activity-list">
            {recent.map((r) => (
              <article key={r.id} className="ro-activity-row">
                <CompanyLogo
                  name={r.counterpartyName}
                  domain={counterpartyLogoDomain(r.counterpartyName)}
                />
                <div className="ro-activity-company">
                  <strong>{r.counterpartyName}</strong>
                  <small>{requestReference(r.id)}</small>
                </div>
                <time dateTime={r.updatedAt}>{date(r.updatedAt)}</time>
                <Badge status={r.status} />
              </article>
            ))}
          </div>
        ) : recent ? (
          <Empty title="No recent credit activity">
            Completed credit updates will appear here.
          </Empty>
        ) : hasErrors ? (
          <Empty title="Recent activity unavailable">
            Refresh the workspace to try again.
          </Empty>
        ) : (
          <Loading />
        )}
      </ChartCard>
    </div>
  );
}
