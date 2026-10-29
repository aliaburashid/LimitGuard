import {
  ArrowRight,
  ArrowUpRight,
  AlertTriangle,
  Building2,
  Clock3,
  Layers3,
  ShieldCheck,
  Wallet,
} from "lucide-react";
import { usePortfolio } from "../hooks/usePortfolio";
import { useAuth } from "../context/Auth";
import {
  ChartCard,
  DataNote,
  ExposureDonut,
  GroupComparison,
} from "../components/AnalyticsCharts";
import { Badge, Empty, ErrorBox, Loading } from "../components/ui";
import {
  bhd,
  compact,
  exposureTotals,
  percent,
  weightedGroupUtilization,
} from "../analytics/metrics";
import { date, label, money, requestReference } from "../utils/format";
const statuses = [
  "PENDING_APPROVAL",
  "RESERVED",
  "USED",
  "REJECTED",
  "EXPIRED",
  "CANCELLED",
];
export function RiskOverview({
  version,
  navigate,
  assess,
}: {
  version: number;
  navigate: (page: string) => void;
  assess: (id: number) => void;
}) {
  const { user } = useAuth();
  const { requests, groups, facilities, members, errors, refreshing } =
    usePortfolio(version);
  const pending = requests?.filter((r) => r.status === "PENDING_APPROVAL");
  const priorities = pending
    ? [...pending]
        .sort(
          (a, b) =>
            Number(b.amount) - Number(a.amount) ||
            a.createdAt.localeCompare(b.createdAt),
        )
        .slice(0, 4)
    : null;
  const totals = facilities ? exposureTotals(facilities) : null;
  const committed = totals
    ? Number(totals.usedAmount) + Number(totals.reservedAmount)
    : null;
  const groupUtilization = groups ? weightedGroupUtilization(groups) : null;
  const restricted =
    facilities?.filter((f) => f.status && f.status !== "ACTIVE") ?? [];
  const unavailable =
    members && groups
      ? new Set(
          members
            .filter(
              (m) =>
                !groups.some(
                  (g) => g.corporateGroupId === m.corporateGroupId,
                ) &&
                facilities?.some((f) => f.counterpartyId === m.counterpartyId),
            )
            .map((m) => m.corporateGroupId),
        ).size
      : 0;
  const belowThreshold =
    pending?.filter((r) => Number(r.amount) <= 500000) ?? [];
  const topGroup = groups?.length
    ? [...groups].sort((a, b) => b.exposure - a.exposure)[0]
    : null;
  const totalGroupExposure =
    groups?.reduce((sum, g) => sum + g.exposure, 0) ?? 0;
  return (
    <div className="risk-overview">
      <div className="analytics-page-heading">
        <div>
          <div className="page-kicker">
            Risk management <span>/</span> Portfolio overview
          </div>
          <h1>Credit oversight</h1>
          <p>Exposure, capacity and decisions. One clear view.</p>
        </div>
        <div className="heading-actions">
          <span className="institution-pill">
            <Building2 size={14} />
            {user?.financialInstitution.name}
          </span>
          <button className="primary" onClick={() => navigate("approvals")}>
            Open approval queue
            <ArrowUpRight size={15} />
          </button>
        </div>
      </div>
      <div className="portfolio-scope">
        <span className="scope-dot" />
        <span>
          Institution view · {facilities?.length ?? "—"} observed facilities ·{" "}
          {groups?.length ?? "—"} configured group limits
        </span>
        <span>{refreshing ? "Refreshing…" : "Current API snapshot"}</span>
      </div>
      {!!errors.length && <ErrorBox message={[...new Set(errors)].join(" ")} />}
      <div className="overview-kpis">
        <div className="overview-kpi">
          <div>
            <span>Pending approvals</span>
            <Clock3 size={16} />
          </div>
          <strong>{pending?.length ?? "—"}</strong>
          <p>
            {pending
              ? `${money(pending.reduce((s, r) => s + Number(r.amount), 0))} awaiting decision`
              : "Loading request register"}
          </p>
        </div>
        <div className="overview-kpi">
          <div>
            <span>Committed exposure</span>
            <Wallet size={16} />
          </div>
          <strong title={committed !== null ? bhd(committed) : undefined}>
            <small>BHD</small> {committed !== null ? compact(committed) : "—"}
          </strong>
          <p>Used + reserved · observed facilities</p>
        </div>
        <div className="overview-kpi">
          <div>
            <span>Available headroom</span>
            <ShieldCheck size={16} />
          </div>
          <strong title={totals ? bhd(totals.availableHeadroom) : undefined}>
            <small>BHD</small>{" "}
            {totals ? compact(totals.availableHeadroom) : "—"}
          </strong>
          <p>Individual facility capacity</p>
        </div>
        <div className="overview-kpi">
          <div>
            <span>Group utilization</span>
            <Layers3 size={16} />
          </div>
          <strong>
            {groupUtilization !== null ? groupUtilization.toFixed(1) : "—"}
            <small>%</small>
          </strong>
          <p>Exposure / combined group limits</p>
        </div>
      </div>
      <div className="overview-analytics-grid">
        <ChartCard
          title="Credit exposure composition"
          subtitle="Used, reserved and remaining individual capacity"
          action={<span className="chart-unit">BHD</span>}
        >
          {totals ? (
            <ExposureDonut data={totals} />
          ) : !facilities && !errors.length ? (
            <Loading />
          ) : (
            <Empty title="No facility exposure available">
              Inspect requests to view company facilities.
            </Empty>
          )}
          <div className="chart-footer">
            <span>Approved facility capacity</span>
            <strong>{totals ? bhd(totals.limitAmount) : "—"}</strong>
          </div>
        </ChartCard>
        <ChartCard
          title="Corporate group utilization"
          subtitle="Committed exposure against each approved group limit"
        >
          {groups?.length ? (
            <GroupComparison
              groups={groups}
              onSelect={() => navigate("exposure")}
            />
          ) : !groups && !errors.length ? (
            <Loading />
          ) : (
            <Empty title="No group limits configured">
              Group utilization is unavailable until this institution has a
              group limit.
            </Empty>
          )}
        </ChartCard>
      </div>
      <div className="overview-bottom-grid">
        <ChartCard
          title="Pending decisions"
          subtitle="Highest requested amounts first"
          action={
            <button
              className="text-button"
              onClick={() => navigate("approvals")}
            >
              View queue
              <ArrowRight size={14} />
            </button>
          }
        >
          <div className="compact-approval-list">
            {priorities?.length ? (
              priorities.map((r) => (
                <button key={r.id} onClick={() => assess(r.id)}>
                  <span className="company-mark">
                    {r.counterpartyName
                      .split(" ")
                      .slice(0, 2)
                      .map((s) => s[0])
                      .join("")}
                  </span>
                  <span>
                    <strong title={r.counterpartyName}>
                      {r.counterpartyName}
                    </strong>
                    <small>
                      {requestReference(r.id)} · {date(r.createdAt)}
                    </small>
                  </span>
                  <span className="preview-amount">
                    <strong>{money(r.amount)}</strong>
                    <small>
                      Assess risk <ArrowUpRight size={11} />
                    </small>
                  </span>
                </button>
              ))
            ) : priorities ? (
              <Empty title="Your approval queue is clear" />
            ) : (
              <Loading />
            )}
          </div>
        </ChartCard>
        <ChartCard
          title="Portfolio watch"
          subtitle="Concentration and data signals"
        >
          <div className="portfolio-watch">
            {topGroup && (
              <div className="watch-insight">
                <span className="watch-icon">
                  <Layers3 size={17} />
                </span>
                <div>
                  <strong>
                    {percent(topGroup.exposure, totalGroupExposure)?.toFixed(
                      1,
                    ) ?? "0"}
                    % group concentration
                  </strong>
                  <p>{topGroup.corporateGroupName}</p>
                  <small>
                    Largest share of configured-group committed exposure
                  </small>
                </div>
              </div>
            )}
            {unavailable > 0 && (
              <div className="watch-insight">
                <span className="watch-icon neutral">
                  <Building2 size={17} />
                </span>
                <div>
                  <strong>
                    {unavailable} group limit{unavailable === 1 ? "" : "s"} not
                    configured
                  </strong>
                  <p>
                    Group headroom is unavailable for these observed
                    relationships.
                  </p>
                </div>
              </div>
            )}
            {belowThreshold.length > 0 && (
              <button
                className="watch-insight watch-link"
                onClick={() => assess(belowThreshold[0].id)}
              >
                <span className="watch-icon warning">
                  <AlertTriangle size={17} />
                </span>
                <span>
                  <strong>
                    {belowThreshold.length} pending request
                    {belowThreshold.length === 1 ? "" : "s"} below the current
                    threshold
                  </strong>
                  <p>Review existing record history before deciding.</p>
                </span>
                <ArrowUpRight size={14} />
              </button>
            )}
            {restricted.length > 0 && (
              <div className="watch-insight">
                <span className="watch-icon warning">
                  <AlertTriangle size={17} />
                </span>
                <div>
                  <strong>
                    {restricted.length} restricted counterpart
                    {restricted.length === 1 ? "y" : "ies"}
                  </strong>
                  <p>
                    Frozen or closed counterparties in the observed facilities.
                  </p>
                </div>
              </div>
            )}
            {!topGroup &&
              !unavailable &&
              !belowThreshold.length &&
              !restricted.length && (
                <DataNote>
                  No concentration or restriction signals are available in the
                  current scope.
                </DataNote>
              )}
          </div>
        </ChartCard>
      </div>
      <div
        className="request-status-summary"
        aria-label="Request status breakdown"
      >
        <span>Request register</span>
        {statuses.map((status) => (
          <button key={status} onClick={() => navigate("approvals")}>
            <Badge status={status} />
            <strong>
              {requests?.filter((r) => r.status === status).length ?? "—"}
            </strong>
            <span className="sr-only">{label(status)} requests</span>
          </button>
        ))}
      </div>
      <p className="analytics-scope-footnote">
        Facility totals cover limits discovered from this institution’s requests
        from request history. The API has no complete individual-limit
        directory. Group figures cover all configured limits; individual and
        group capacity are separate controls.
      </p>
    </div>
  );
}
