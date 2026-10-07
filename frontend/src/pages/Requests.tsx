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
import type { Exposure, Page, Request, Review } from "../types";
import { date, label, money } from "../utils/format";
import type { Mutation } from "./MutationDialog";
export function Requests({
  approval,
  version,
  mutate,
}: {
  approval: boolean;
  version: number;
  mutate: (a: Mutation) => void;
}) {
  const { user } = useAuth();
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
    api<Page<Request>>(
      `/credit-requests/${approval ? "pending" : "my"}?page=${page}&size=10${approval ? "&sort=createdAt,desc" : ""}`,
      "GET",
      undefined,
      c.signal,
    )
      .then(setData)
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    return () => c.abort();
  }, [approval, page, version]);
  useEffect(() => {
    setReview(null);
    setExposure(null);
    setDetailError("");
    if (!selected) return;
    let active = true;
    setLoading(true);
    Promise.all([
      approval
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
          <button
            className="primary"
            onClick={() =>
              mutate({
                title: "Create credit request",
                path: "/credit-requests",
                method: "POST",
                fields: [
                  {
                    name: "creditLimitId",
                    label: "Credit-limit ID",
                    type: "number",
                  },
                  {
                    name: "amount",
                    label: "Requested amount (£)",
                    type: "number",
                  },
                ],
                description:
                  "The backend determines whether your request reserves capacity immediately or requires Risk Officer approval.",
              })
            }
          >
            <Plus size={17} />
            New request
          </button>
        )}
      </div>
      <section className="panel">
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
        ) : !data.content.length ? (
          <Empty
            title={approval ? "No pending approvals" : "No credit requests yet"}
          />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Counterparty</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Reservation expiry</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <button
                        className="table-link mono"
                        onClick={() => setSelected(r)}
                      >
                        CR-{r.id}
                      </button>
                    </td>
                    <td>Counterparty #{r.counterpartyId}</td>
                    <td className="numeric">{money(r.amount)}</td>
                    <td>
                      <Badge status={r.status} />
                    </td>
                    <td>{date(r.createdAt)}</td>
                    <td>{date(r.expiresAt)}</td>
                    <td>
                      <button onClick={() => setSelected(r)}>
                        {approval ? "Review" : "Details"}
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
            total={data.totalPages}
            count={data.totalElements}
            onChange={setPage}
          />
        )}
      </section>
      {selected && (
        <Modal
          title={`Credit request CR-${selected.id}`}
          onClose={() => setSelected(null)}
        >
          <div className="detail-meta">
            <Badge status={selected.status} />
            <strong className="request-amount">{money(selected.amount)}</strong>
          </div>
          <dl>
            <dt>Counterparty</dt>
            <dd>{review?.counterpartyName || `#${selected.counterpartyId}`}</dd>
            <dt>Requester</dt>
            <dd>{review?.requesterName || `User #${selected.requesterId}`}</dd>
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
                disabled={loading || !!detailError}
                onClick={() => transition("reject")}
              >
                Reject request
              </button>
              <button
                className="primary"
                disabled={loading || !!detailError}
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
