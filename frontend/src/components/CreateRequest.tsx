import { useEffect, useState } from "react";
import { api } from "../api/client";
import {
  groupPortfolio,
  memberships,
  requestDirectory,
} from "../api/portfolio";
import type { Exposure, GroupExposure, Membership, Request } from "../types";
import { Modal, ErrorBox, ExposurePanel, Loading } from "./ui";
import { GroupPanel } from "./GroupPanel";
import { money } from "../utils/format";
export function CreateRequest({
  close,
  done,
}: {
  close: () => void;
  done: (message: string) => void;
}) {
  const [known, setKnown] = useState<Request[]>([]);
  const [id, setId] = useState("");
  const [amount, setAmount] = useState("250000");
  const [exposure, setExposure] = useState<Exposure | null>(null);
  const [groups, setGroups] = useState<GroupExposure[]>([]);
  const [members, setMembers] = useState<Membership[]>([]);
  const [error, setError] = useState("");
  const [capacityError, setCapacityError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const c = new AbortController();
    void requestDirectory(false, c.signal)
      .then(setKnown)
      .catch(() => {});
    void Promise.all([groupPortfolio(c.signal), memberships(c.signal)])
      .then(([g, m]) => {
        setGroups(g);
        setMembers(m);
      })
      .catch((e) => {
        if (!c.signal.aborted) setCapacityError(e.message);
      });
    return () => c.abort();
  }, []);
  useEffect(() => {
    const c = new AbortController();
    setExposure(null);
    setError("");
    if (/^[1-9]\d*$/.test(id))
      void api<Exposure>(
        `/credit-limits/${id}/exposure`,
        "GET",
        undefined,
        c.signal,
      )
        .then((value) => {
          if (!c.signal.aborted) setExposure(value);
        })
        .catch((e) => {
          if (!c.signal.aborted) setError(e.message);
        });
    return () => c.abort();
  }, [id]);
  const choices = [...new Map(known.map((r) => [r.creditLimitId, r])).values()];
  const member = members.find(
    (m) => m.counterpartyId === exposure?.counterpartyId,
  );
  const group = groups.find(
    (g) => g.corporateGroupId === member?.corporateGroupId,
  );
  return (
    <Modal
      title="Create credit request"
      onClose={() => {
        if (!busy) close();
      }}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if (busy || !exposure) return;
          setBusy(true);
          setError("");
          try {
            const request = await api<Request>("/credit-requests", "POST", {
              creditLimitId: Number(id),
              amount,
            });
            done(
              `Credit request #${request.id}: ${request.status.replaceAll("_", " ")}.`,
            );
            close();
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <p className="muted">
          Select a company or enter its credit-limit ID. Requests above BHD
          500,000 enter Risk Officer approval.
        </p>
        <div className="form-grid">
          <label className="field">
            <span>Company / credit facility</span>
            <select
              value={
                choices.some((c) => String(c.creditLimitId) === id) ? id : ""
              }
              onChange={(e) => {
                setId(e.target.value);
              }}
            >
              <option value="">Select a company, or use an ID below</option>
              {choices.map((c) => (
                <option key={c.creditLimitId} value={c.creditLimitId}>
                  {c.counterpartyName} · Limit #{c.creditLimitId}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Credit-limit ID</span>
            <input
              type="number"
              min="1"
              step="1"
              required
              value={id}
              onChange={(e) => {
                setId(e.target.value);
              }}
            />
          </label>
          <label className="field">
            <span>Requested amount (BHD)</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
        </div>
        {!choices.length && (
          <p className="scope-note">
            Enter the credit-limit ID provided by your Risk Officer to request
            credit for a company without previous requests.
          </p>
        )}
        {capacityError && <ErrorBox message={capacityError} />}
        {exposure && (
          <>
            <ExposurePanel data={exposure} />
            {group && <GroupPanel group={group} />}
            {group &&
              Number(amount) > group.headroom &&
              Number(amount) <= Number(exposure.availableHeadroom) && (
                <div className="notice risk-warning">
                  The company has sufficient individual headroom, but its
                  corporate group has only {money(group.headroom)} available.
                  {Number(amount) <= 500000
                    ? " The shared group limit prevents this reservation."
                    : " Approval remains subject to the shared group limit."}
                </div>
              )}
            <div className="notice">
              Proposed individual headroom:{" "}
              {money(Number(exposure.availableHeadroom) - Number(amount))}
              {group &&
                ` · Proposed group headroom: ${money(group.headroom - Number(amount))}`}
              .{" "}
              {Number(amount) > 500000
                ? "Pending requests do not reserve capacity until approval."
                : "Capacity is validated when submitted."}
            </div>
          </>
        )}
        {id && !exposure && !error && <Loading />}
        {error && <ErrorBox message={error} />}
        <div className="modal-actions">
          <button type="button" disabled={busy} onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy || !exposure}>
            {busy ? "Submitting…" : "Confirm create credit request"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
