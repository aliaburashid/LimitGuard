import { lazy, Suspense } from "react";
import {
  ArrowRight,
  ArrowUpRight,
  Building2,
  Clock3,
  Layers3,
  Plus,
  Wallet,
} from "lucide-react";
import { compact, exposureTotals } from "../analytics/metrics";
import { useAuth } from "../context/Auth";
import { Badge, Empty, ErrorBox, Loading } from "../components/ui";
import { GroupHeadroom, preciseMoney } from "../components/ExposureHeadroomBar";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import { date, requestReference } from "../utils/format";

const RelationshipExposureTrend = lazy(
  () => import("../components/RelationshipExposureTrend"),
);

export function RelationshipOverview({
  portfolio,
  navigate,
  version,
  openPendingRequests,
  createRequest,
}: {
  portfolio: RelationshipPortfolio;
  navigate: (page: string, recordId?: number) => void;
  version: number;
  openPendingRequests: () => void;
  createRequest: () => void;
}) {
  const { user } = useAuth();
  const { facilities, requests, groups, errors, refreshing } = portfolio;
  const totals = exposureTotals(facilities ?? []);
  const pending = requests?.filter(
    (request) => request.status === "PENDING_APPROVAL",
  );
  const committed = (totals?.usedAmount ?? 0) + (totals?.reservedAmount ?? 0);
  const metrics = [
    {
      title: "Total Approved Credit",
      value: facilities ? (totals?.limitAmount ?? 0) : null,
      description: "Individual facility limits",
      icon: Building2,
      currency: true,
      open: () => navigate("facilities"),
    },
    {
      title: "Committed Exposure",
      value: facilities ? committed : null,
      description: "Used and reserved",
      icon: Layers3,
      currency: true,
      open: () => navigate("facilities"),
    },
    {
      title: "Available Headroom",
      value: facilities ? (totals?.availableHeadroom ?? 0) : null,
      description: "Remaining facility capacity",
      icon: Wallet,
      currency: true,
      open: () => navigate("facilities"),
    },
    {
      title: "My Pending Requests",
      value: pending?.length ?? null,
      description: "Awaiting a decision",
      icon: Clock3,
      currency: false,
      open: openPendingRequests,
    },
  ];
  return (
    <div className="rm-page rm-overview-page">
      <header className="rm-overview-header">
        <div>
          <h1>Portfolio Overview</h1>
          <p>{user?.financialInstitution.name} · Relationship Manager</p>
        </div>
        <button className="primary" onClick={createRequest}>
          <Plus size={17} aria-hidden="true" />
          Create Credit Request
        </button>
      </header>
      {errors.length > 0 && (
        <ErrorBox message={[...new Set(errors)].join(" ")} />
      )}
      <div className="rm-metrics" aria-label="Portfolio metrics">
        {metrics.map(
          ({ title, value, description, icon: Icon, currency, open }) => {
            const exact =
              value === null
                ? "Unavailable"
                : currency
                  ? preciseMoney(value)
                  : String(value);
            return (
              <button
                className="panel rm-metric"
                key={title}
                onClick={open}
                aria-label={`${title}, ${exact}`}
                title={exact}
              >
                <span className="rm-metric-label">
                  <Icon size={16} aria-hidden="true" />
                  {title}
                </span>
                <strong>
                  {value === null
                    ? "—"
                    : currency
                      ? `BD ${compact(value)}`
                      : value}
                </strong>
                <span className="rm-metric-description">{description}</span>
              </button>
            );
          },
        )}
      </div>
      <div className="rm-overview-grid">
        <Suspense
          fallback={
            <section className="panel rm-exposure-trend">
              <h2>Exposure Trend</h2>
              <Loading />
            </section>
          }
        >
          <RelationshipExposureTrend key={user?.id} version={version} />
        </Suspense>
        <section className="panel rm-group-overview">
          <div className="section-head">
            <div>
              <span className="eyebrow">SHARED CAPACITY</span>
              <h2>Corporate group utilization</h2>
            </div>
          </div>
          {groups?.length ? (
            [...groups]
              .sort(
                (a, b) =>
                  b.exposure / Number(b.amount) - a.exposure / Number(a.amount),
              )
              .slice(0, 2)
              .map((group) => <GroupHeadroom key={group.id} group={group} />)
          ) : refreshing ? (
            <Loading />
          ) : (
            <Empty title="No shared group limits available" />
          )}
          <button
            className="text-button"
            onClick={() => navigate("corporate-groups")}
          >
            Explore group limits <ArrowRight size={15} />
          </button>
        </section>
      </div>
      <section className="panel rm-recent-requests">
        <div className="section-head">
          <div>
            <span className="eyebrow">REQUEST REGISTER</span>
            <h2>Recent credit requests</h2>
          </div>
          <button className="text-button" onClick={() => navigate("requests")}>
            View all requests <ArrowRight size={15} />
          </button>
        </div>
        {requests?.length ? (
          <div className="table-scroll">
            <table aria-label="Recent credit requests">
              <thead>
                <tr>
                  <th>Request ID</th>
                  <th>Counterparty</th>
                  <th>Amount</th>
                  <th>Request type</th>
                  <th>Submitted</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {requests.slice(0, 6).map((request) => (
                  <tr key={request.id}>
                    <td className="mono">{requestReference(request.id)}</td>
                    <td>{request.counterpartyName}</td>
                    <td className="numeric">{preciseMoney(request.amount)}</td>
                    <td>Credit capacity</td>
                    <td>{date(request.createdAt)}</td>
                    <td>
                      <Badge status={request.status} />
                    </td>
                    <td>
                      <button
                        className="text-button"
                        aria-label={`View details for ${requestReference(request.id)}`}
                        onClick={() => navigate("requests", request.id)}
                      >
                        View Details <ArrowUpRight size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : refreshing ? (
          <Loading />
        ) : (
          <Empty title="No credit requests available" />
        )}
      </section>
    </div>
  );
}
