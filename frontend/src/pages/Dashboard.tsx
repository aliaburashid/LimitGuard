import {
  ArrowRight,
  ArrowUpRight,
  Building2,
  Clock,
  Layers3,
  ShieldCheck,
} from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../api/client";
import { Badge, Empty, ErrorBox, Loading } from "../components/ui";
import { useAuth } from "../context/Auth";
import type { Counterparty, Page, Request } from "../types";
import { date, label, money } from "../utils/format";
export function Dashboard({
  navigate,
  version,
}: {
  navigate: (p: string) => void;
  version: number;
}) {
  const { user } = useAuth();
  const [data, setData] = useState<Page<Counterparty> | null>(null);
  const [requests, setRequests] = useState<Page<Request> | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    setError("");
    Promise.all([
      api<Page<Counterparty>>("/counterparties?size=5"),
      user?.role === "ADMIN"
        ? Promise.resolve(null)
        : api<Page<Request>>(
            "/credit-requests/" +
              (user?.role === "RISK_OFFICER" ? "pending" : "my") +
              "?size=5&sort=createdAt,desc",
          ),
    ])
      .then(([c, r]) => {
        if (active) {
          setData(c);
          setRequests(r);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [user, version]);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">PORTFOLIO CONTROL CENTER</span>
          <h1>Credit oversight</h1>
          <p>Your counterparties and credit operations, in one clear view.</p>
        </div>
        <span className="date-label">
          {new Date().toLocaleDateString("en-GB", {
            day: "numeric",
            month: "long",
            year: "numeric",
          })}
        </span>
      </div>
      {error ? (
        <ErrorBox message={error} />
      ) : !data ? (
        <Loading />
      ) : (
        <>
          <div className="metrics">
            <div className="metric">
              <span>
                Counterparty directory <Building2 size={18} />
              </span>
              <strong>{data.totalElements.toLocaleString()}</strong>
              <small>Accessible counterparties</small>
            </div>
            <div className="metric">
              <span>
                {user?.role === "RISK_OFFICER"
                  ? "Pending decisions"
                  : "Your credit requests"}{" "}
                <Clock size={18} />
              </span>
              <strong>{requests?.totalElements.toLocaleString() ?? "—"}</strong>
              <small>
                {user?.role === "ADMIN"
                  ? "Request access is limited to RM and Risk roles"
                  : "Live API record count"}
              </small>
            </div>
            <div className="metric">
              <span>
                Institution <Layers3 size={18} />
              </span>
              <strong className="metric-name">
                {user?.financialInstitution?.name || "Not provided"}
              </strong>
              <small>Your operating institution</small>
            </div>
            <div className="metric accent">
              <span>
                Access controls <ShieldCheck size={18} />
              </span>
              <strong className="metric-name">{label(user!.role)}</strong>
              <small>Backend authorization enforced</small>
            </div>
          </div>
          <div className="dashboard-grid">
            <section className="panel capacity-panel">
              <span className="eyebrow">EXPOSURE MONITORING</span>
              <h2>Every limit. A clearer decision.</h2>
              <p>
                Inspect used capacity, reservations and available headroom
                against a specific credit limit.
              </p>
              <div className="capacity-graphic" aria-hidden="true">
                <div />
                <div />
                <div />
              </div>
              <div className="legend">
                <span>
                  <i className="used" />
                  Used exposure
                </span>
                <span>
                  <i className="reserved" />
                  Reserved
                </span>
                <span>
                  <i className="available" />
                  Headroom
                </span>
              </div>
              <p className="small">
                Portfolio totals are unavailable from the current API. This
                illustration represents exposure categories, not financial data.
              </p>
              <button onClick={() => navigate("exposure")}>
                Inspect a credit limit <ArrowUpRight size={16} />
              </button>
            </section>
            <section className="panel">
              <div className="section-head">
                <div>
                  <span className="eyebrow">WORK QUEUE</span>
                  <h2>
                    {user?.role === "RISK_OFFICER"
                      ? "Awaiting your decision"
                      : "Recent credit requests"}
                  </h2>
                </div>
                {requests && (
                  <button
                    className="text-button"
                    onClick={() =>
                      navigate(
                        user?.role === "RISK_OFFICER"
                          ? "approvals"
                          : "requests",
                      )
                    }
                  >
                    View all <ArrowRight size={14} />
                  </button>
                )}
              </div>
              {!requests ? (
                <Empty title="Role-controlled workspace">
                  Credit requests are available to Relationship Managers and
                  Risk Officers.
                </Empty>
              ) : !requests.content.length ? (
                <Empty title="Your queue is clear">
                  No credit requests are available in this queue.
                </Empty>
              ) : (
                <div className="activity-list">
                  {requests.content.map((r) => (
                    <button
                      key={r.id}
                      onClick={() =>
                        navigate(
                          user?.role === "RISK_OFFICER"
                            ? "approvals"
                            : "requests",
                        )
                      }
                    >
                      <span className="activity-icon">
                        <Clock size={18} />
                      </span>
                      <span>
                        <strong>Request #{r.id}</strong>
                        <small>
                          Counterparty #{r.counterpartyId} · {date(r.createdAt)}
                        </small>
                      </span>
                      <span className="activity-end">
                        <strong>{money(r.amount, true)}</strong>
                        <Badge status={r.status} />
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </section>
          </div>
          <section className="panel">
            <div className="section-head">
              <div>
                <span className="eyebrow">COUNTERPARTY REGISTER</span>
                <h2>Portfolio relationships</h2>
              </div>
              <button
                className="text-button"
                onClick={() => navigate("counterparties")}
              >
                Open directory <ArrowRight size={15} />
              </button>
            </div>
            {data.content.length ? (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Counterparty</th>
                      <th>Reference</th>
                      <th>Status</th>
                      <th>Last updated</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((c) => (
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
              <Empty />
            )}
          </section>
          <div className="control-note">
            <ShieldCheck size={16} />
            Figures are sourced from authorized API responses. No estimated
            portfolio balances.
          </div>
        </>
      )}
    </>
  );
}
