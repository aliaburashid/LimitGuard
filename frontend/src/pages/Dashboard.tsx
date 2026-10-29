import { lazy, Suspense } from "react";
const RiskOverview = lazy(() =>
  import("./RiskOverview").then((module) => ({ default: module.RiskOverview })),
);
import {
  ArrowRight,
  Building2,
  Clock,
  ShieldCheck,
  Wallet,
  ArrowUpRight,
} from "lucide-react";
import { useEffect, useState } from "react";
import { requestDirectory, groupPortfolio } from "../api/portfolio";
import { api } from "../api/client";
import { Badge, Empty, ErrorBox, Loading } from "../components/ui";
import { GroupPanel } from "../components/GroupPanel";
import { useAuth } from "../context/Auth";
import type { Counterparty, Page, Request, GroupExposure } from "../types";
import { date, label, money, requestReference } from "../utils/format";
function OperationalDashboard({
  navigate,
  version,
}: {
  navigate: (p: string) => void;
  version: number;
}) {
  const { user } = useAuth();
  const risk = user?.role === "RISK_OFFICER";
  const [requests, setRequests] = useState<Request[] | null>(null);
  const [groups, setGroups] = useState<GroupExposure[] | null>(null);
  const [directory, setDirectory] = useState<Page<Counterparty> | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    setErrors([]);
    const capture = (e: Error) => {
      if (!controller.signal.aborted)
        setErrors((previous) => [...previous, e.message]);
    };
    void groupPortfolio(controller.signal).then(setGroups).catch(capture);
    void api<Page<Counterparty>>(
      "/counterparties?size=5",
      "GET",
      undefined,
      controller.signal,
    )
      .then(setDirectory)
      .catch(capture);
    if (user?.role !== "ADMIN")
      void requestDirectory(risk, controller.signal)
        .then(setRequests)
        .catch(capture);
    return () => controller.abort();
  }, [user?.id, version]);
  const statuses = [
    "PENDING_APPROVAL",
    "RESERVED",
    "USED",
    "REJECTED",
    "EXPIRED",
    "CANCELLED",
  ];
  const sum = (status: string) =>
    (requests ?? [])
      .filter((r) => r.status === status)
      .reduce((v, r) => v + Number(r.amount), 0);
  const totalGroup = groups?.reduce((v, g) => v + Number(g.amount), 0);
  const groupExposure = groups?.reduce((v, g) => v + g.exposure, 0);
  const pending = requests?.filter((r) => r.status === "PENDING_APPROVAL");
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">
            {risk
              ? "RISK OFFICER • PORTFOLIO CONTROL"
              : "RELATIONSHIP MANAGEMENT • CREDIT OPERATIONS"}
          </span>
          <h1>Credit oversight</h1>
          <p>
            {user?.financialInstitution.name} ·{" "}
            {risk
              ? "A clear view of capacity, concentration and credit decisions."
              : "Manage your relationships through the full credit lifecycle."}
          </p>
        </div>
        <button
          className="primary"
          onClick={() =>
            navigate(
              risk
                ? "approvals"
                : user?.role === "ADMIN"
                  ? "exposure"
                  : "requests",
            )
          }
        >
          {risk ? "Open approval queue" : "Open workspace"}
          <ArrowUpRight size={17} />
        </button>
      </div>
      {errors.length > 0 && (
        <ErrorBox message={[...new Set(errors)].join(" ")} />
      )}
      <div className="metrics">
        <div className="metric accent">
          <span>
            {risk ? "Pending approvals" : "Your pending requests"}
            <Clock size={18} />
          </span>
          <strong>{requests ? pending!.length : "—"}</strong>
          <small>
            {requests
              ? money(sum("PENDING_APPROVAL")) + " awaiting decision"
              : "Loading authorized requests"}
          </small>
        </div>
        <div className="metric">
          <span>
            Group credit limits
            <Wallet size={18} />
          </span>
          <strong className="metric-name">
            {groups ? money(totalGroup!) : "—"}
          </strong>
          <small>{groups?.length ?? "—"} groups within your institution</small>
        </div>
        <div className="metric">
          <span>
            Group exposure
            <Building2 size={18} />
          </span>
          <strong className="metric-name">
            {groups ? money(groupExposure!) : "—"}
          </strong>
          <small>Used + reserved across group members</small>
        </div>
        <div className="metric">
          <span>
            Group headroom
            <ShieldCheck size={18} />
          </span>
          <strong className="metric-name">
            {groups ? money(groups.reduce((v, g) => v + g.headroom, 0)) : "—"}
          </strong>
          <small>Sum of available group capacity</small>
        </div>
      </div>
      <p className="scope-note">
        Group figures cover bank-linked corporate groups. Ungrouped limits are
        excluded. Request amounts below describe{" "}
        {risk ? "your institution’s" : "your own"} request register, rather than
        opening ledger balances.
      </p>
      <div className="status-strip">
        {statuses.map((status) => (
          <button
            key={status}
            onClick={() => navigate(risk ? "approvals" : "requests")}
          >
            <Badge status={status} />
            <strong>
              {requests
                ? requests.filter((r) => r.status === status).length
                : "—"}
            </strong>
            <small>{requests ? money(sum(status)) : "Loading"}</small>
          </button>
        ))}
      </div>
      <div className="dashboard-grid banking-grid">
        <section className="panel">
          <div className="section-head">
            <div>
              <span className="eyebrow">
                {risk ? "DECISION PRIORITIES" : "YOUR CREDIT LIFECYCLE"}
              </span>
              <h2>
                {risk ? "Awaiting risk review" : "Recent credit requests"}
              </h2>
            </div>
            <button
              className="text-button"
              onClick={() => navigate(risk ? "approvals" : "requests")}
            >
              View register
              <ArrowRight size={15} />
            </button>
          </div>
          {!requests ? (
            user?.role === "ADMIN" ? (
              <Empty title="Role-controlled workspace" />
            ) : (
              <Loading />
            )
          ) : !(risk ? pending : requests)?.length ? (
            <Empty title="Your queue is clear" />
          ) : (
            <div className="activity-list">
              {(risk ? pending! : requests).slice(0, 6).map((r) => (
                <button
                  key={r.id}
                  onClick={() => navigate(risk ? "approvals" : "requests")}
                >
                  <span className="activity-icon">
                    <Clock size={18} />
                  </span>
                  <span>
                    <strong>{r.counterpartyName}</strong>
                    <small>
                      {requestReference(r.id)} · {r.requesterName} ·{" "}
                      {date(r.createdAt)}
                    </small>
                  </span>
                  <span className="activity-end">
                    <strong>{money(r.amount)}</strong>
                    <Badge status={r.status} />
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>
        <section className="panel concentration-panel">
          <span className="eyebrow">CONCENTRATION MONITOR</span>
          <h2>Corporate group utilization</h2>
          <p className="muted">
            Committed exposure against approved group limits.
          </p>
          {!groups ? (
            <Loading />
          ) : !groups.length ? (
            <Empty title="No group limits available" />
          ) : (
            [...groups]
              .sort(
                (a, b) =>
                  b.exposure / Number(b.amount) - a.exposure / Number(a.amount),
              )
              .slice(0, 4)
              .map((g) => <GroupPanel key={g.id} group={g} />)
          )}
          <button className="text-button" onClick={() => navigate("exposure")}>
            Explore all group limits
            <ArrowRight size={15} />
          </button>
        </section>
      </div>
      {risk && requests && (
        <section className="panel">
          <div className="section-head">
            <div>
              <span className="eyebrow">RECENT DECISIONS</span>
              <h2>Approved and rejected requests</h2>
            </div>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Company</th>
                  <th>Amount</th>
                  <th>Outcome</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {[...requests]
                  .filter((r) => ["RESERVED", "REJECTED"].includes(r.status))
                  .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
                  .slice(0, 6)
                  .map((r) => (
                    <tr key={r.id}>
                      <td className="mono">{requestReference(r.id)}</td>
                      <td>{r.counterpartyName}</td>
                      <td className="numeric">{money(r.amount)}</td>
                      <td>
                        <Badge status={r.status} />
                      </td>
                      <td>{date(r.updatedAt)}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          <p className="scope-note">
            Reserved includes immediate reservations. The request API does not
            distinguish those from Risk Officer approvals.
          </p>
        </section>
      )}
      <section className="panel">
        <div className="section-head">
          <div>
            <span className="eyebrow">RELATIONSHIP DIRECTORY</span>
            <h2>Counterparties</h2>
          </div>
          <button
            className="text-button"
            onClick={() => navigate("counterparties")}
          >
            Open directory
            <ArrowRight size={15} />
          </button>
        </div>
        {directory ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Company</th>
                  <th>Reference</th>
                  <th>Status</th>
                  <th>Last updated</th>
                </tr>
              </thead>
              <tbody>
                {directory.content.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <strong>{c.name}</strong>
                    </td>
                    <td className="mono">CP-{c.id}</td>
                    <td>
                      <Badge status={c.status} />
                    </td>
                    <td>{date(c.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Loading />
        )}
      </section>
      <div className="control-note">
        <ShieldCheck size={16} />
        Live institution-scoped capacity · {label(user!.role)} · BHD
      </div>
    </>
  );
}

export function Dashboard(props: {
  navigate: (page: string) => void;
  version: number;
  assess?: (id: number) => void;
}) {
  const { user } = useAuth();
  return user?.role === "RISK_OFFICER" ? (
    <Suspense fallback={<Loading />}>
      <RiskOverview
        {...props}
        assess={props.assess ?? (() => props.navigate("approvals"))}
      />
    </Suspense>
  ) : (
    <OperationalDashboard {...props} />
  );
}
