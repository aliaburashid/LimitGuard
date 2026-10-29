import { ArrowRight, ArrowUpRight, CheckCircle2, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../api/client";
import {
  Badge,
  Empty,
  ErrorBox,
  ExposurePanel,
  Loading,
  Modal,
  Pager,
} from "../components/ui";
import { useAuth } from "../context/Auth";
import { CreateRequest } from "../components/CreateRequest";
import { requestDirectory, memberships } from "../api/portfolio";
import type { Membership } from "../types";
import type { Exposure, Page, Request, Review } from "../types";
import { date, label, money, requestReference } from "../utils/format";
import type { Mutation } from "./MutationDialog";
export function Requests({
  approval,
  version,
  mutate,
  assess,
}: {
  approval: boolean;
  version: number;
  mutate: (a: Mutation) => void;
  assess?: (id: number) => void;
}) {
  const { user } = useAuth();
  const [lastResult, setLastResult] = useState("");
  const [creating, setCreating] = useState(false);
  const [status, setStatus] = useState(approval ? "PENDING_APPROVAL" : "");
  const [query, setQuery] = useState("");
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
  }, [approval, page, version, status, query]);
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
      .then(setSelected)
      .catch((e) => {
        if (e.name !== "AbortError") setDetailError(e.message);
      });
    return () => controller.abort();
  }, [version]);
  const filtered =
    data?.content.filter(
      (r) =>
        !query ||
        `${r.counterpartyName} ${r.id} ${r.requesterName}`
          .toLowerCase()
          .includes(query.toLowerCase()),
    ) ?? [];
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
      <div className="page-heading">
        <div>
          <span className="eyebrow">
            {approval ? "RISK DECISION WORKSPACE" : "CREDIT OPERATIONS"}
          </span>
          <h1>{approval ? "Approval queue" : "Credit requests"}</h1>
          <p>
            {approval
              ? "Review capacity. Make deliberate, informed decisions."
              : "Track your requests from submission to final outcome."}
          </p>
        </div>
        {!approval && user?.role === "RELATIONSHIP_MANAGER" && (
          <button className="primary" onClick={() => setCreating(true)}>
            <Plus size={17} />
            New request
          </button>
        )}
      </div>
      {lastResult && (
        <div className="notice" role="status">
          {lastResult}
        </div>
      )}
      <div className={`register-filters ${approval ? "queue-toolbar" : ""}`}>
        <label className="field">
          <span>Search requests</span>
          <input
            value={query}
            placeholder="Company, request ID or requester"
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="field">
          <span>Status</span>
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
      </div>
      <section
        className={`panel request-queue-panel ${approval ? "risk-queue" : ""}`}
      >
        <div className="section-head">
          <h2>
            {approval ? "Pending risk decisions" : "Your request register"}
          </h2>
          <span className="subtle">Newest requests first</span>
        </div>
        {error ? (
          <ErrorBox message={error} />
        ) : !data ? (
          <Loading />
        ) : !filtered.length ? (
          <Empty
            title={approval ? "No pending approvals" : "No credit requests yet"}
          />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Company</th>
                  <th>Corporate group</th>
                  <th>Exposure / headroom</th>
                  {!approval && <th>Requester</th>}
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Created</th>
                  {!approval && <th>Reservation expiry</th>}
                  <th />
                </tr>
              </thead>
              <tbody>
                {filtered.slice(page * 10, page * 10 + 10).map((r) => (
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
                      <strong>{r.counterpartyName}</strong>
                      <div className="small">{r.financialInstitutionName}</div>
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
                    {!approval && <td>{r.requesterName}</td>}
                    <td className="numeric">{money(r.amount)}</td>
                    <td>
                      <Badge status={r.status} />
                    </td>
                    <td>{date(r.createdAt)}</td>
                    {!approval && <td>{date(r.expiresAt)}</td>}
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
        {data && (
          <Pager
            page={page}
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
        <Modal
          title={`Credit request ${requestReference(selected.id)}`}
          onClose={() => setSelected(null)}
        >
          <div className="detail-meta">
            <Badge status={selected.status} />
            <strong className="request-amount">{money(selected.amount)}</strong>
          </div>
          <dl>
            <dt>Counterparty</dt>
            <dd>{selected.counterpartyName}</dd>
            <dt>Institution</dt>
            <dd>{selected.financialInstitutionName}</dd>
            <dt>Counterparty status</dt>
            <dd>
              <Badge status={selected.counterpartyStatus} />
            </dd>
            <dt>Requester</dt>
            <dd>{selected.requesterName}</dd>
            <dt>Created</dt>
            <dd>{date(selected.createdAt)}</dd>
            <dt>Updated</dt>
            <dd>{date(selected.updatedAt)}</dd>
            <dt>Reservation expiry</dt>
            <dd>{date(selected.expiresAt)}</dd>
          </dl>
          <div className="lifecycle">
            <span className="complete">
              <CheckCircle2 size={16} />
              Created
            </span>
            <ArrowRight size={15} />
            <span className="complete">{label(selected.status)}</span>
          </div>
          {loading ? (
            <Loading />
          ) : detailError ? (
            <ErrorBox message={detailError} />
          ) : (
            exposure && (
              <>
                <ExposurePanel data={exposure} />
                {approval && review && (
                  <div className="notice">
                    If approved: committed exposure{" "}
                    {money(
                      Number(review.usedAmount) +
                        Number(review.reservedAmount) +
                        Number(review.requestedAmount),
                    )}
                    ; remaining headroom{" "}
                    {money(
                      Number(review.availableHeadroom) -
                        Number(review.requestedAmount),
                    )}
                    . Illustrative arithmetic; the backend makes the final
                    capacity decision.
                  </div>
                )}
              </>
            )
          )}
          {approval && selected.status === "PENDING_APPROVAL" && (
            <div className="modal-actions">
              <button
                className="danger"
                disabled={
                  loading || !!detailError || selected.requesterId === user?.id
                }
                onClick={() => transition("reject")}
              >
                Reject request
              </button>
              <button
                className="primary"
                disabled={
                  loading || !!detailError || selected.requesterId === user?.id
                }
                onClick={() => transition("approve")}
              >
                Approve & reserve
              </button>
            </div>
          )}
          {!approval && selected.status === "RESERVED" && (
            <div className="modal-actions">
              <button disabled={loading} onClick={() => transition("cancel")}>
                Cancel reservation
              </button>
              <button
                className="primary"
                disabled={loading}
                onClick={() => transition("use")}
              >
                Mark as used
              </button>
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
