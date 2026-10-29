import { Plus, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import {
  allPages,
  groupPortfolio,
  memberships,
  requestDirectory,
} from "../api/portfolio";
import { useAuth } from "../context/Auth";
import type {
  CorporateGroup,
  Counterparty,
  Exposure,
  GroupExposure,
  Membership,
} from "../types";
import { money } from "../utils/format";
import { CreateRequest, type RequestCompany } from "./CreateRequest";
import { GroupPanel } from "./GroupPanel";
import { Badge, Empty, ErrorBox, Loading, Modal } from "./ui";

type GroupAction =
  | { kind: "group" }
  | { kind: "member" | "limit"; group: CorporateGroup; limit?: GroupExposure };
const refreshWorkspace = () =>
  window.dispatchEvent(new Event("workspace-refresh"));

export function CorporateGroupManagement({
  version,
  resolvedFacility,
  findFacility,
}: {
  version: number;
  resolvedFacility: Exposure | null;
  findFacility: (company: Counterparty) => void;
}) {
  const { user } = useAuth();
  const manage = user?.role === "ADMIN" || user?.role === "RISK_OFFICER";
  const [groups, setGroups] = useState<CorporateGroup[] | null>(null);
  const [companies, setCompanies] = useState<Counterparty[]>([]);
  const [members, setMembers] = useState<Membership[]>([]);
  const [limits, setLimits] = useState<GroupExposure[]>([]);
  const [facilities, setFacilities] = useState<Record<number, Exposure>>({});
  const known = useRef(new Map<number, number>());
  const [selectedId, setSelectedId] = useState("");
  const [error, setError] = useState("");
  const [facilityError, setFacilityError] = useState("");
  const [loading, setLoading] = useState(true);
  const [updated, setUpdated] = useState("");
  const [notice, setNotice] = useState("");
  const [action, setAction] = useState<GroupAction | null>(null);
  const [requesting, setRequesting] = useState<RequestCompany | null>(null);
  useEffect(() => {
    if (!resolvedFacility) return;
    known.current.set(
      resolvedFacility.creditLimitId,
      resolvedFacility.counterpartyId,
    );
    setFacilities((old) => ({
      ...old,
      [resolvedFacility.counterpartyId]: resolvedFacility,
    }));
  }, [resolvedFacility]);
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    setError("");
    setFacilityError("");
    const history =
      user?.role === "ADMIN"
        ? Promise.resolve([])
        : requestDirectory(user?.role === "RISK_OFFICER", c.signal).catch(
            (e) => {
              if (!c.signal.aborted) setFacilityError(e.message);
              return [];
            },
          );
    void Promise.all([
      allPages<CorporateGroup>("/corporate-groups", c.signal),
      allPages<Counterparty>("/counterparties", c.signal),
      memberships(c.signal),
      groupPortfolio(c.signal),
      history,
    ])
      .then(
        async ([
          directory,
          counterparties,
          membership,
          sharedLimits,
          requests,
        ]) => {
          if (c.signal.aborted) return;
          if (!Array.isArray(membership))
            throw new Error(
              "Company membership information could not be read.",
            );
          setGroups(directory);
          setCompanies(counterparties);
          setMembers(membership);
          setLimits(sharedLimits);
          setSelectedId((current) =>
            directory.some((g) => String(g.id) === current)
              ? current
              : String(
                  sharedLimits[0]?.corporateGroupId ?? directory[0]?.id ?? "",
                ),
          );
          requests.forEach((r) =>
            known.current.set(r.creditLimitId, r.counterpartyId),
          );
          const exposures = await Promise.allSettled(
            [...known.current].map(async ([id, company]) => {
              const value = await api<Exposure>(
                `/credit-limits/${id}/exposure`,
                "GET",
                undefined,
                c.signal,
              );
              if (
                value.creditLimitId !== id ||
                value.counterpartyId !== company
              )
                throw new Error("Facility details did not match the company.");
              return value;
            }),
          );
          if (c.signal.aborted) return;
          const values: Record<number, Exposure> = {};
          exposures.forEach((value) => {
            if (value.status === "fulfilled")
              values[value.value.counterpartyId] = value.value;
          });
          setFacilities(values);
          if (exposures.some((x) => x.status === "rejected"))
            setFacilityError(
              "Some individual facilities could not be refreshed. Use Find facility to check their details.",
            );
          setUpdated(new Date().toLocaleTimeString("en-GB"));
        },
      )
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [version, user?.id, user?.role]);
  const selected = groups?.find((g) => g.id === Number(selectedId));
  const shared = limits.find((g) => g.corporateGroupId === selected?.id);
  const rows = members.filter((m) => m.corporateGroupId === selected?.id);
  const available = companies.filter(
    (company) => !members.some((m) => m.counterpartyId === company.id),
  );
  return (
    <section
      className="panel corporate-management"
      aria-label="Corporate group management"
      aria-busy={loading}
    >
      <div className="section-head">
        <div>
          <span className="eyebrow">CONNECTED COMPANIES</span>
          <h2>Corporate group management</h2>
          <p className="muted">
            Individual facilities share the group’s approved capacity at{" "}
            {user?.financialInstitution.name}.
          </p>
        </div>
        <div className="group-management-actions">
          <button onClick={refreshWorkspace} disabled={loading}>
            <RefreshCw size={15} />
            Refresh group capacity
          </button>
          {manage && (
            <button
              className="primary"
              onClick={() => setAction({ kind: "group" })}
            >
              <Plus size={16} />
              Create corporate group
            </button>
          )}
        </div>
      </div>
      {notice && (
        <p role="status" className="notice">
          {notice}
        </p>
      )}
      {error && <ErrorBox message={error} />}
      {!groups ? (
        loading ? (
          <Loading />
        ) : null
      ) : groups.length === 0 ? (
        <Empty title="No corporate groups" />
      ) : (
        <>
          <div className="group-management-selector">
            <label className="field">
              <span>Corporate group</span>
              <select
                aria-label="Corporate group"
                value={selectedId}
                onChange={(e) => setSelectedId(e.target.value)}
              >
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </label>
            {selected && manage && (
              <div className="group-management-actions">
                <button
                  onClick={() => setAction({ kind: "member", group: selected })}
                >
                  Add company
                </button>
                <button
                  onClick={() =>
                    setAction({ kind: "limit", group: selected, limit: shared })
                  }
                >
                  {shared ? "Update shared limit" : "Set shared limit"}
                </button>
              </div>
            )}
          </div>
          {selected && (
            <>
              {shared ? (
                <GroupPanel group={shared} />
              ) : (
                <p className="notice">
                  Shared limit not configured for {selected.name} at your
                  institution.
                </p>
              )}
              <div className="section-head group-members-heading">
                <h3>Companies in {selected.name}</h3>
                <span className="small muted">
                  {rows.length} members ·{" "}
                  {loading ? "Refreshing…" : `Updated ${updated}`}
                </span>
              </div>
              {facilityError && <ErrorBox message={facilityError} />}
              {user?.role === "RELATIONSHIP_MANAGER" && (
                <p className="muted small">
                  Choose an available company to request credit. Amounts up to
                  BHD 500,000 reserve immediately; larger amounts require
                  approval.
                </p>
              )}
              {rows.length === 0 ? (
                <Empty title="No companies in this group" />
              ) : (
                <div className="table-scroll">
                  <table aria-label="Corporate group companies">
                    <thead>
                      <tr>
                        <th>Company</th>
                        <th>Individual limit</th>
                        <th>Used exposure</th>
                        <th>Reserved exposure</th>
                        <th>Individual headroom</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((m) => {
                        const company = companies.find(
                          (c) => c.id === m.counterpartyId,
                        );
                        const facility = facilities[m.counterpartyId];
                        return (
                          <tr key={m.id}>
                            <td>
                              <strong>
                                {company?.name ??
                                  `Company #${m.counterpartyId}`}
                              </strong>
                              {company && <Badge status={company.status} />}
                              {facility && (
                                <small className="muted">
                                  Facility #{facility.creditLimitId}
                                </small>
                              )}
                            </td>
                            {facility ? (
                              <>
                                <td>{money(facility.limitAmount)}</td>
                                <td>{money(facility.usedAmount)}</td>
                                <td>{money(facility.reservedAmount)}</td>
                                <td>{money(facility.availableHeadroom)}</td>
                              </>
                            ) : (
                              <td colSpan={4} className="muted">
                                Individual facility details are not loaded.
                              </td>
                            )}
                            <td>
                              <div className="group-row-actions">
                                {company && (
                                  <button
                                    className="text-button"
                                    onClick={() => findFacility(company)}
                                    aria-label={`Find facility for ${company.name}`}
                                  >
                                    Find facility
                                  </button>
                                )}
                                {user?.role === "RELATIONSHIP_MANAGER" &&
                                  facility &&
                                  company && (
                                    <button
                                      className="primary"
                                      disabled={
                                        company.status !== "ACTIVE" || !shared
                                      }
                                      onClick={() =>
                                        setRequesting({
                                          name: company.name,
                                          creditLimitId: facility.creditLimitId,
                                          counterpartyId: company.id,
                                        })
                                      }
                                    >
                                      Request credit
                                    </button>
                                  )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="small muted">
                Group totals include used and reserved exposure across all
                members at your institution. Facility details use permitted
                request history and verified lookups; unloaded details are not
                treated as zero exposure.
              </p>
            </>
          )}
        </>
      )}
      {action && (
        <GroupManagementDialog
          key={action.kind + ("group" in action ? action.group.id : "")}
          action={action}
          available={available}
          close={() => setAction(null)}
          done={(message, groupId) => {
            setNotice(message);
            if (groupId) setSelectedId(String(groupId));
            refreshWorkspace();
          }}
        />
      )}
      {requesting && (
        <CreateRequest
          initialCompany={requesting}
          close={() => setRequesting(null)}
          done={(message) => {
            setNotice(message);
            refreshWorkspace();
          }}
        />
      )}
    </section>
  );
}

function GroupManagementDialog({
  action,
  available,
  close,
  done,
}: {
  action: GroupAction;
  available: Counterparty[];
  close: () => void;
  done: (message: string, groupId?: number) => void;
}) {
  const [name, setName] = useState("");
  const [company, setCompany] = useState("");
  const [amount, setAmount] = useState(
    action.kind === "limit" ? String(action.limit?.amount ?? "") : "",
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const title =
    action.kind === "group"
      ? "Create corporate group"
      : action.kind === "member"
        ? "Add company"
        : action.limit
          ? "Update shared limit"
          : "Set shared limit";
  return (
    <Modal
      title={title}
      onClose={() => {
        if (!busy) close();
      }}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if (busy) return;
          setBusy(true);
          setError("");
          try {
            if (action.kind === "group") {
              const group = await api<CorporateGroup>(
                "/corporate-groups",
                "POST",
                { name: name.trim() },
              );
              done(`Corporate group ${group.name} created.`, group.id);
            } else if (action.kind === "member") {
              await api("/corporate-group-memberships", "POST", {
                corporateGroupId: action.group.id,
                counterpartyId: Number(company),
              });
              done("Company added to the corporate group.", action.group.id);
            } else {
              await api(
                action.limit
                  ? `/group-credit-limits/${action.limit.id}`
                  : "/group-credit-limits",
                action.limit ? "PUT" : "POST",
                action.limit
                  ? { limitAmount: amount }
                  : { corporateGroupId: action.group.id, limitAmount: amount },
              );
              done("Shared credit limit saved.", action.group.id);
            }
            close();
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <p className="muted">
          {action.kind === "group"
            ? "Create a connected-company group, then add its existing companies and shared institution limit."
            : `For ${action.group.name}. LimitGuard validates this operation when submitted.`}
        </p>
        {action.kind === "group" ? (
          <label className="field">
            <span>Group name</span>
            <input
              required
              maxLength={255}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
        ) : action.kind === "member" ? (
          <label className="field">
            <span>Existing company</span>
            <select
              required
              value={company}
              onChange={(e) => setCompany(e.target.value)}
            >
              <option value="">Select an unassigned company</option>
              {available.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            {available.length === 0 && (
              <span>No unassigned companies are available.</span>
            )}
          </label>
        ) : (
          <label className="field">
            <span>Shared limit amount (BHD)</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
        )}
        {error && <ErrorBox message={error} />}
        <div className="modal-actions">
          <button type="button" disabled={busy} onClick={close}>
            Cancel
          </button>
          <button
            className="primary"
            disabled={
              busy ||
              (action.kind === "member" && !company) ||
              (action.kind === "group" && !name.trim())
            }
          >
            {busy ? "Submitting…" : `Confirm ${title.toLowerCase()}`}
          </button>
        </div>
      </form>
    </Modal>
  );
}
