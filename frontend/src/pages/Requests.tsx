import { CompanyLogo } from "../components/CompanyLogo";
import { ArrowUpRight, Plus, Search, SlidersHorizontal } from "lucide-react";
import { useEffect, useId, useState } from "react";
import { api } from "../api/client";
import { Badge, Empty, ErrorBox, Loading, Pager } from "../components/ui";
import { useAuth } from "../context/Auth";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import { CreateRequest } from "../components/CreateRequest";
import { requestDirectory, memberships } from "../api/portfolio";
import type { Membership } from "../types";
import type { Exposure, Page, Request, Review } from "../types";
import { date, label, money, requestReference } from "../utils/format";
import { PersonalRequestTable } from "./PersonalRequestTable";
import "../styles/request-register.css";
import { CreditRequestDetails } from "./CreditRequestDetails";
import type { Mutation } from "./MutationDialog";
export function Requests({
  approval,
  initialStatus = "",
  companyFilter,
  clearCompanyFilter,
  version,
  mutate,
  assess,
  portfolio,
  selection,
  createRequest,
}: {
  approval: boolean;
  initialStatus?: string;
  companyFilter?: { id: number; name: string } | null;
  clearCompanyFilter?: () => void;
  version: number;
  mutate: (a: Mutation) => void;
  assess?: (id: number) => void;
  portfolio?: RelationshipPortfolio;
  selection?: { id: number } | null;
  createRequest?: () => void;
}) {
  const { user } = useAuth();
  const personal = !approval && user?.role === "RELATIONSHIP_MANAGER";
  const filterId = useId();
  const [moreFilters, setMoreFilters] = useState(false);
  const [lastResult, setLastResult] = useState("");
  const [creating, setCreating] = useState(false);
  const [status, setStatus] = useState(
    approval ? "PENDING_APPROVAL" : initialStatus,
  );
  const [query, setQuery] = useState("");
  const [createdFrom, setCreatedFrom] = useState("");
  const [createdThrough, setCreatedThrough] = useState("");
  const [members, setMembers] = useState<Membership[]>([]);
  const [capacities, setCapacities] = useState<Record<number, Exposure>>({});
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Request> | null>(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<Request | null>(null);
  const [review, setReview] = useState<Review | null>(null);
  const [exposure, setExposure] = useState<Exposure | null>(null);
  const [detailError, setDetailError] = useState("");
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (portfolio) return;
    const c = new AbortController();
    setData(null);
    setError("");
    requestDirectory(approval, c.signal)
      .then((rows) => {
        if (c.signal.aborted) return;
        const result = rows.filter((r) => !status || r.status === status);
        setData({
          content: result,
          totalElements: result.length,
          totalPages: Math.ceil(result.length / 10),
          number: page,
        });
        const visible = result
          .filter(
            (r) =>
              !query ||
              `${r.counterpartyName} ${r.id} ${r.requesterName}`
                .toLowerCase()
                .includes(query.toLowerCase()),
          )
          .slice(page * 10, page * 10 + 10);
        void Promise.all(
          visible.map((r) =>
            api<Exposure>(
              `/credit-limits/${r.creditLimitId}/exposure`,
              "GET",
              undefined,
              c.signal,
            ),
          ),
        )
          .then((values) => {
            if (!c.signal.aborted)
              setCapacities(
                Object.fromEntries(values.map((v) => [v.creditLimitId, v])),
              );
          })
          .catch(() => {});
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    void memberships(c.signal)
      .then(setMembers)
      .catch(() => {});
    return () => c.abort();
  }, [approval, page, version, status, query, Boolean(portfolio)]);
  useEffect(() => {
    setReview(null);
    setExposure(null);
    setDetailError("");
    if (!selected) return;
    let active = true;
    setLoading(true);
    Promise.all([
      approval && selected.status === "PENDING_APPROVAL"
        ? api<Review>(`/credit-requests/${selected.id}/review`)
        : Promise.resolve(null),
      api<Exposure>(`/credit-limits/${selected.creditLimitId}/exposure`),
    ])
      .then(([r, e]) => {
        if (active) {
          setReview(r);
          setExposure(e);
        }
      })
      .catch((e) => {
        if (active) setDetailError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selected, approval, version]);
  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    api<Request>(
      `/credit-requests/${selected.id}`,
      "GET",
      undefined,
      controller.signal,
    )
      .then((request) => {
        if (!controller.signal.aborted)
          setSelected((previous) =>
            previous?.id === request.id ? request : previous,
          );
      })
      .catch((e) => {
        if (e.name !== "AbortError") setDetailError(e.message);
      });
    return () => controller.abort();
  }, [version]);
  useEffect(() => {
    if (!selection) return;
    const controller = new AbortController();
    void api<Request>(
      `/credit-requests/${selection.id}`,
      "GET",
      undefined,
      controller.signal,
    )
      .then((request) => {
        if (!controller.signal.aborted) setSelected(request);
      })
      .catch((error: Error) => {
        if (!controller.signal.aborted) setError(error.message);
      });
    return () => controller.abort();
  }, [selection]);
  const source = portfolio ? portfolio.requests : (data?.content ?? null);
  const loadError =
    error ||
    (portfolio && !portfolio.requests && !portfolio.refreshing
      ? portfolio.errors.join(" ")
      : "");
  const search = query.trim().toLowerCase();
  const filtersActive = Boolean(
    search || status || createdFrom || createdThrough || companyFilter,
  );
  function clearFilters() {
    setQuery("");
    setStatus("");
    setCreatedFrom("");
    setCreatedThrough("");
    setPage(0);
    clearCompanyFilter?.();
  }
  function newRequest() {
    if (createRequest) createRequest();
    else setCreating(true);
  }
  const filtered = (source ?? []).filter((request) => {
    const created = request.createdAt.slice(0, 10);
    return (
      (!companyFilter || request.counterpartyId === companyFilter.id) &&
      (!status || request.status === status) &&
      (!search ||
        [
          request.counterpartyName,
          requestReference(request.id),
          ...(!personal ? [request.requesterName, request.creditLimitId] : []),
        ]
          .join(" ")
          .toLowerCase()
          .includes(search)) &&
      (!createdFrom || created >= createdFrom) &&
      (!createdThrough || created <= createdThrough)
    );
  });
  const visiblePage = Math.min(
    page,
    Math.max(0, Math.ceil(filtered.length / 10) - 1),
  );
  const rows = filtered.slice(visiblePage * 10, visiblePage * 10 + 10);
  function openRequest(r: Request) {
    if (approval && assess) assess(r.id);
    else setSelected(r);
  }
  function transition(action: string) {
    if (!selected) return;
    const id = selected.id;
    setSelected(null);
    mutate({
      title: label(action) + " request",
      path: `/credit-requests/${id}/${action}`,
      method: "PATCH",
      fields:
        action === "reject"
          ? [{ name: "reason", label: "Rejection reason" }]
          : [],
      description:
        action === "approve"
          ? "Approval reserves the requested capacity, subject to backend headroom and counterparty checks."
          : action === "use"
            ? "This marks the reservation as used exposure. Confirm that the credit has been utilized."
            : action === "cancel"
              ? "Cancellation releases reserved capacity. This request will become cancelled."
              : "Provide a clear reason for this credit decision.",
    });
  }
  return (
    <>
      <div
        className={
          personal
            ? "rm-overview-header rm-request-register-heading"
            : "page-heading"
        }
      >
        <div>
          {!personal && (
            <span className="eyebrow">RISK DECISION WORKSPACE</span>
          )}
          <h1>{personal ? "My Credit Requests" : "Approval queue"}</h1>
          <p>
            {personal
              ? "Track your submitted credit requests and manage active reservations."
              : "Review capacity. Make deliberate, informed decisions."}
          </p>
        </div>
        {personal && (
          <button className="primary" onClick={newRequest}>
            <Plus size={17} aria-hidden="true" />
            New Request
          </button>
        )}
      </div>
      {lastResult && (
        <div className="notice" role="status">
          {lastResult}
        </div>
      )}
      <div
        className={
          personal ? "rm-request-filters" : "register-filters queue-toolbar"
        }
      >
        <label className="field">
          <span className={personal ? "sr-only" : undefined}>
            Search requests
          </span>
          {personal && <Search size={16} aria-hidden="true" />}
          <input
            value={query}
            placeholder={
              personal
                ? "Search company or request reference"
                : "Company, request ID or requester"
            }
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="field">
          <span className={personal ? "sr-only" : undefined}>Status</span>
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {[
              "PENDING_APPROVAL",
              "RESERVED",
              "USED",
              "REJECTED",
              "EXPIRED",
              "CANCELLED",
            ].map((s) => (
              <option key={s} value={s}>
                {label(s)}
              </option>
            ))}
          </select>
        </label>
        {personal && (
          <>
            <button
              type="button"
              aria-expanded={moreFilters}
              aria-controls={filterId}
              onClick={() => setMoreFilters(!moreFilters)}
            >
              <SlidersHorizontal size={15} aria-hidden="true" />
              More Filters
            </button>
            {filtersActive && (
              <button
                className="text-button rm-request-clear"
                onClick={clearFilters}
              >
                Clear filters
              </button>
            )}
          </>
        )}
        {personal && (
          <div className="rm-request-dates" id={filterId} hidden={!moreFilters}>
            <label className="field">
              <span>Created from</span>
              <input
                type="date"
                value={createdFrom}
                max={createdThrough || undefined}
                onChange={(event) => {
                  setCreatedFrom(event.target.value);
                  setPage(0);
                }}
              />
            </label>
            <label className="field">
              <span>Created through</span>
              <input
                type="date"
                value={createdThrough}
                min={createdFrom || undefined}
                onChange={(event) => {
                  setCreatedThrough(event.target.value);
                  setPage(0);
                }}
              />
            </label>
          </div>
        )}
      </div>
      <section
        className={
          personal
            ? "panel rm-request-register"
            : "panel request-queue-panel risk-queue"
        }
      >
        {(!personal || companyFilter) && (
          <div className="section-head">
            <h2>
              {approval ? "Pending risk decisions" : "Your request register"}
            </h2>
            {companyFilter ? (
              <div className="rm-request-company-filter">
                <span>{companyFilter.name}</span>
                <button
                  className="text-button"
                  onClick={() => {
                    setPage(0);
                    clearCompanyFilter?.();
                  }}
                >
                  Clear company filter
                </button>
              </div>
            ) : (
              <span className="subtle">Newest requests first</span>
            )}
          </div>
        )}
        {loadError ? (
          <ErrorBox message={loadError} />
        ) : !source ? (
          <Loading />
        ) : !filtered.length ? (
          personal ? (
            <div className="rm-request-empty">
              <Empty
                title={
                  filtersActive
                    ? "No matching requests"
                    : "No credit requests yet"
                }
              >
                {filtersActive
                  ? "Try adjusting your search or filters."
                  : "Requests you submit will appear here."}
              </Empty>
              <button
                className={filtersActive ? "text-button" : "primary"}
                onClick={filtersActive ? clearFilters : newRequest}
              >
                {filtersActive ? "Clear filters" : "New Request"}
              </button>
            </div>
          ) : (
            <Empty title="No pending approvals" />
          )
        ) : personal ? (
          <PersonalRequestTable requests={rows} openRequest={openRequest} />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Company</th>
                  <th>Corporate group</th>
                  <th>Exposure / headroom</th>
                  <th>Requester</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <button
                        className="table-link mono"
                        onClick={() => openRequest(r)}
                      >
                        {requestReference(r.id)}
                      </button>
                    </td>
                    <td>
                      <div className="company-brand">
                        <CompanyLogo name={r.counterpartyName} />
                        <div>
                          <strong>{r.counterpartyName}</strong>
                          <div className="small">
                            {r.financialInstitutionName}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      {members.find(
                        (m) => m.counterpartyId === r.counterpartyId,
                      )?.corporateGroupName ?? "—"}
                    </td>
                    <td className="numeric">
                      {capacities[r.creditLimitId] ? (
                        <>
                          {money(
                            Number(capacities[r.creditLimitId].usedAmount) +
                              Number(
                                capacities[r.creditLimitId].reservedAmount,
                              ),
                          )}
                          <div className="small">
                            {money(
                              capacities[r.creditLimitId].availableHeadroom,
                            )}{" "}
                            available
                          </div>
                        </>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td>{r.requesterName}</td>
                    <td className="numeric">{money(r.amount)}</td>
                    <td>
                      <Badge status={r.status} />
                    </td>
                    <td>{date(r.createdAt)}</td>
                    <td>
                      <button onClick={() => openRequest(r)}>
                        {approval ? "Assess risk" : "Details"}
                        <ArrowUpRight size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {filtered.length > 0 && (
          <Pager
            page={visiblePage}
            total={Math.ceil(filtered.length / 10)}
            count={filtered.length}
            onChange={setPage}
          />
        )}
      </section>
      {creating && (
        <CreateRequest
          close={() => setCreating(false)}
          done={(message) => {
            setLastResult(message);
            window.dispatchEvent(new Event("workspace-refresh"));
          }}
        />
      )}
      {selected && (
        <CreditRequestDetails
          request={selected}
          exposure={exposure}
          review={approval ? review : null}
          loading={loading}
          error={detailError}
          canDecide={
            approval &&
            user?.role === "RISK_OFFICER" &&
            selected.status === "PENDING_APPROVAL" &&
            selected.requesterId !== user.id
          }
          canManageReservation={
            !approval &&
            user?.role === "RELATIONSHIP_MANAGER" &&
            selected.status === "RESERVED" &&
            selected.requesterId === user.id
          }
          onClose={() => setSelected(null)}
          transition={transition}
        />
      )}
    </>
  );
}
