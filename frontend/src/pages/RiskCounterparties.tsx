import { useEffect, useState, type FormEvent } from "react";
import { ArrowLeft, ArrowUpRight, Plus, Search } from "lucide-react";
import { api, ApiError } from "../api/client";
import {
  allPages,
  facilityDirectory,
  groupPortfolio,
  memberships,
} from "../api/portfolio";
import { CompanyLogo, CorporateGroupLogo } from "../components/CompanyLogo";
import {
  ExposureHeadroomBar,
  facilityHeadroom,
  groupHeadroom,
} from "../components/ExposureHeadroomBar";
import { FinancialProfileEditor } from "../components/FinancialProfileEditor";
import {
  Badge,
  Empty,
  ErrorBox,
  Loading,
  Modal,
  Pager,
} from "../components/ui";
import { counterpartyLogoDomain } from "../config/verifiedLogoDomains";
import { useAuth } from "../context/Auth";
import type { Facility } from "../analytics/metrics";
import type {
  CorporateGroup,
  Counterparty,
  FinancialProfile,
  GroupExposure,
  Membership,
} from "../types";
import { date, label } from "../utils/format";
import { financialRatioFields } from "../utils/financialProfile";
import "../styles/risk-counterparties.css";

type Tab = "individual" | "groups" | "facilities";
type Field = {
  name: string;
  label: string;
  value?: string;
  options?: { value: string; label: string }[];
  type?: string;
};
type Action = {
  title: string;
  path: string;
  method: string;
  fields: Field[];
  body?: Record<string, unknown>;
  message: string;
  warning?: string;
  sharedRecord?: boolean;
};
type Catalog = {
  companies: Counterparty[];
  groups: CorporateGroup[];
  members: Membership[];
};
const tabs: { id: Tab; name: string }[] = [
  { id: "individual", name: "Individual Counterparties" },
  { id: "groups", name: "Corporate Groups" },
  { id: "facilities", name: "Credit Facilities" },
];

// Directories are already scoped by Credit Service. Keep IDs inside API operations.
export function RiskCounterparties({
  version,
  selection,
  groupView = false,
  facilitiesView,
  facilitySelection,
  changeView,
}: {
  version: number;
  selection?: { id: number } | null;
  groupView?: boolean;
  facilitiesView: boolean;
  facilitySelection?: number;
  changeView: (facilities: boolean) => void;
}) {
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>(
    facilitiesView ? "facilities" : groupView ? "groups" : "individual",
  );
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [facilities, setFacilities] = useState<Facility[] | null>(null);
  const [limits, setLimits] = useState<GroupExposure[] | null>(null);
  const [error, setError] = useState("");
  const [capacityError, setCapacityError] = useState("");
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [companyId, setCompanyId] = useState<number | null>(null);
  const [groupId, setGroupId] = useState<number | null>(null);
  const [facilityCompanyId, setFacilityCompanyId] = useState("");
  const [facilityGroupId, setFacilityGroupId] = useState("");
  const [action, setAction] = useState<Action | null>(null);
  const [financialId, setFinancialId] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  const authorized = user?.role === "RISK_OFFICER";
  useEffect(() => {
    if (groupView) {
      setTab("groups");
      setCompanyId(null);
      setGroupId(selection?.id ?? null);
    } else if (selection && !facilitiesView) {
      setTab("individual");
      setCompanyId(selection.id);
    }
  }, [groupView, selection?.id, facilitiesView]);
  useEffect(() => {
    setTab((current) =>
      facilitiesView
        ? "facilities"
        : current === "facilities"
          ? "individual"
          : current,
    );
  }, [facilitiesView]);
  useEffect(() => {
    const controller = new AbortController();
    setBusy(true);
    setError("");
    setCapacityError("");
    void Promise.all([
      allPages<Counterparty>(
        "/counterparties?sort=name,asc",
        controller.signal,
      ),
      allPages<CorporateGroup>(
        "/corporate-groups?sort=name,asc",
        controller.signal,
      ),
      memberships(controller.signal),
    ])
      .then(([companies, groups, members]) => {
        if (!controller.signal.aborted)
          setCatalog({ companies, groups, members });
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    // Failed capacity reads must never be interpreted as a missing facility.
    void Promise.all([
      facilityDirectory(controller.signal),
      groupPortfolio(controller.signal),
    ])
      .then(([individual, shared]) => {
        if (!controller.signal.aborted) {
          setFacilities(individual);
          setLimits(shared);
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setFacilities(null);
          setLimits(null);
          setCapacityError(e.message);
        }
      });
    return () => controller.abort();
  }, [version, revision, user?.id]);
  useEffect(() => {
    if (!facilitySelection || !facilities) return;
    const facility = facilities.find(
      (row) => row.creditLimitId === facilitySelection,
    );
    if (facility) setFacilityCompanyId(String(facility.counterpartyId));
  }, [facilitySelection, facilities]);
  if (!authorized)
    return <ErrorBox message="Risk Officer access is required." />;
  const companies = catalog?.companies ?? [];
  const groups = catalog?.groups ?? [];
  const members = catalog?.members ?? [];
  const company = companies.find((row) => row.id === companyId);
  const selectedGroup = groups.find((row) => row.id === groupId);
  const selectedMembers = members.filter(
    (row) => row.corporateGroupId === groupId,
  );
  const unassigned = companies.filter(
    (row) => !members.some((member) => member.counterpartyId === row.id),
  );
  const companyOptions = companies.map((row) => ({
    value: String(row.id),
    label: row.name,
  }));
  const groupOptions = groups.map((row) => ({
    value: String(row.id),
    label: row.name,
  }));
  const selectedFacility = facilities?.find(
    (row) => row.counterpartyId === Number(facilityCompanyId),
  );
  const selectedLimit = limits?.find(
    (row) => row.corporateGroupId === Number(facilityGroupId),
  );
  const refresh = (message: string) => {
    setNotice(message);
    setRevision((value) => value + 1);
    window.dispatchEvent(new Event("workspace-refresh"));
  };
  function switchTab(next: Tab) {
    setTab(next);
    setCompanyId(null);
    setGroupId(null);
    setQuery("");
    setPage(0);
    if ((next === "facilities") !== facilitiesView)
      changeView(next === "facilities");
  }
  function openCompany(id: number) {
    setCompanyId(id);
    setGroupId(null);
    setTab("individual");
  }
  function manageFacility(id?: number, shared = false) {
    if (shared) setFacilityGroupId(String(id ?? ""));
    else setFacilityCompanyId(String(id ?? ""));
    switchTab("facilities");
  }
  function editLimit(shared: boolean) {
    const existing = shared ? selectedLimit : selectedFacility;
    const id = shared ? facilityGroupId : facilityCompanyId;
    if (!id) return;
    setAction({
      title: shared
        ? existing
          ? "Update shared limit"
          : "Create shared limit"
        : existing
          ? "Update credit facility"
          : "Create credit facility",
      path: shared
        ? `/group-credit-limits${selectedLimit ? `/${selectedLimit.id}` : ""}`
        : `/credit-limits${selectedFacility ? `/${selectedFacility.creditLimitId}` : ""}`,
      method: existing ? "PUT" : "POST",
      body: existing
        ? {}
        : shared
          ? { corporateGroupId: Number(id) }
          : { counterpartyId: Number(id) },
      fields: [
        {
          name: "limitAmount",
          label: "Approved limit (BHD)",
          type: "number",
          value: existing
            ? String(
                shared ? selectedLimit!.amount : selectedFacility!.limitAmount,
              )
            : "",
        },
      ],
      warning:
        "Confirm the approved limit. Existing exposure and headroom controls apply.",
      message: shared ? "Shared credit limit saved." : "Credit facility saved.",
    });
  }
  const search = query.trim().toLocaleLowerCase();
  const visibleCompanies = companies.filter(
    (row) =>
      row.name.toLocaleLowerCase().includes(search) &&
      (!status || row.status === status),
  );
  const visibleGroups = groups.filter((row) =>
    row.name.toLocaleLowerCase().includes(search),
  );
  const pageCount = Math.ceil(visibleCompanies.length / 12);
  const visiblePage = Math.min(page, Math.max(0, pageCount - 1));
  return (
    <div className="ro-counterparties" aria-busy={busy}>
      <header className="page-heading">
        <h1>Counterparty Management</h1>
        {tab === "individual" && !company && (
          <button
            className="primary"
            onClick={() =>
              setAction({
                title: "Create counterparty",
                path: "/counterparties",
                method: "POST",
                fields: [{ name: "name", label: "Counterparty name" }],
                message: "Counterparty created successfully.",
              })
            }
          >
            <Plus size={16} />
            New counterparty
          </button>
        )}
        {tab === "groups" && !selectedGroup && (
          <button
            className="primary"
            onClick={() =>
              setAction({
                title: "Create corporate group",
                path: "/corporate-groups",
                method: "POST",
                fields: [{ name: "name", label: "Group name" }],
                message: "Corporate group created successfully.",
              })
            }
          >
            <Plus size={16} />
            Create corporate group
          </button>
        )}
      </header>
      <div
        className="rm-relationship-tabs"
        role="group"
        aria-label="Counterparty views"
      >
        {tabs.map((item) => (
          <button
            key={item.id}
            aria-pressed={tab === item.id}
            onClick={() => switchTab(item.id)}
          >
            {item.name}
          </button>
        ))}
      </div>
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {error && <ErrorBox message={error} />}
      {capacityError && (
        <ErrorBox message={`Credit capacity unavailable: ${capacityError}`} />
      )}
      {busy && !catalog ? (
        <Loading />
      ) : !catalog ? (
        <button onClick={() => setRevision((value) => value + 1)}>
          Retry directory
        </button>
      ) : (
        <>
          {tab === "individual" &&
            (company ? (
              <>
                <button
                  className="text-button rm-back"
                  onClick={() => setCompanyId(null)}
                >
                  <ArrowLeft size={16} />
                  Individual Counterparties
                </button>
                <section className="panel">
                  <div className="section-head">
                    <h2 className="company-brand">
                      <CompanyLogo
                        name={company.name}
                        domain={counterpartyLogoDomain(company.name)}
                        large
                      />
                      {company.name}
                    </h2>
                    <Badge status={company.status} />
                  </div>
                  <dl className="ro-record-meta">
                    <dt>Corporate group</dt>
                    <dd>
                      {members.find((row) => row.counterpartyId === company.id)
                        ?.corporateGroupName ?? "Standalone company"}
                    </dd>
                    <dt>Created</dt>
                    <dd>{date(company.createdAt)}</dd>
                    <dt>Updated</dt>
                    <dd>{date(company.updatedAt)}</dd>
                  </dl>
                  <div className="ro-record-actions">
                    <button
                      onClick={() =>
                        setAction({
                          title: "Update counterparty",
                          path: `/counterparties/${company.id}`,
                          method: "PUT",
                          fields: [
                            {
                              name: "name",
                              label: "Counterparty name",
                              value: company.name,
                            },
                          ],
                          message: "Counterparty name updated.",
                          sharedRecord: true,
                        })
                      }
                    >
                      Edit name
                    </button>
                    <button
                      onClick={() =>
                        setAction({
                          title: "Change counterparty status",
                          path: `/counterparties/${company.id}/status`,
                          method: "PATCH",
                          fields: [
                            {
                              name: "status",
                              label: "New status",
                              value: company.status,
                              options: ["ACTIVE", "FROZEN", "CLOSED"].map(
                                (value) => ({ value, label: label(value) }),
                              ),
                            },
                          ],
                          warning: `Confirm the status change for ${company.name}. Frozen and closed counterparties cannot submit new credit requests.`,
                          message: "Counterparty status updated.",
                          sharedRecord: true,
                        })
                      }
                    >
                      Change status
                    </button>
                    <button onClick={() => manageFacility(company.id)}>
                      View credit facilities
                    </button>
                  </div>
                </section>
                <FinancialProfileSummary
                  key={`${company.id}-${revision}-${version}`}
                  companyId={company.id}
                  edit={() => setFinancialId(company.id)}
                />
              </>
            ) : (
              <section className="panel">
                <div className="toolbar">
                  <label className="search">
                    <Search size={17} />
                    <input
                      aria-label="Search counterparties by name"
                      placeholder="Search companies…"
                      value={query}
                      onChange={(e) => {
                        setQuery(e.target.value);
                        setPage(0);
                      }}
                    />
                  </label>
                  <select
                    aria-label="Filter counterparties by status"
                    value={status}
                    onChange={(e) => {
                      setStatus(e.target.value);
                      setPage(0);
                    }}
                  >
                    <option value="">All statuses</option>
                    {["ACTIVE", "FROZEN", "CLOSED"].map((value) => (
                      <option key={value} value={value}>
                        {label(value)}
                      </option>
                    ))}
                  </select>
                </div>
                {!visibleCompanies.length ? (
                  <Empty title="No matching counterparties" />
                ) : (
                  <div className="rm-facility-grid">
                    {visibleCompanies
                      .slice(visiblePage * 12, visiblePage * 12 + 12)
                      .map((row) => (
                        <article className="rm-group-card" key={row.id}>
                          <h3 className="company-brand">
                            <CompanyLogo
                              name={row.name}
                              domain={counterpartyLogoDomain(row.name)}
                            />
                            {row.name}
                          </h3>
                          <Badge status={row.status} />
                          <p className="muted small">
                            {members.find(
                              (member) => member.counterpartyId === row.id,
                            )?.corporateGroupName ?? "Standalone company"}
                          </p>
                          <button
                            className="text-button"
                            aria-label={`View ${row.name}`}
                            onClick={() => openCompany(row.id)}
                          >
                            View counterparty <ArrowUpRight size={15} />
                          </button>
                        </article>
                      ))}
                  </div>
                )}
                {visibleCompanies.length > 0 && (
                  <Pager
                    page={visiblePage}
                    total={pageCount}
                    count={visibleCompanies.length}
                    onChange={setPage}
                  />
                )}
              </section>
            ))}
          {tab === "groups" &&
            (selectedGroup ? (
              <>
                <button
                  className="text-button rm-back"
                  onClick={() => setGroupId(null)}
                >
                  <ArrowLeft size={16} />
                  Corporate Groups
                </button>
                <section className="panel">
                  <div className="section-head">
                    <h2 className="company-brand">
                      <CorporateGroupLogo name={selectedGroup.name} />
                      {selectedGroup.name}
                    </h2>
                    <button
                      onClick={() =>
                        setAction({
                          title: "Rename corporate group",
                          path: `/corporate-groups/${selectedGroup.id}`,
                          method: "PUT",
                          fields: [
                            {
                              name: "name",
                              label: "Group name",
                              value: selectedGroup.name,
                            },
                          ],
                          message: "Corporate group renamed.",
                          sharedRecord: true,
                        })
                      }
                    >
                      Rename group
                    </button>
                  </div>
                  {limits?.find(
                    (row) => row.corporateGroupId === selectedGroup.id,
                  ) ? (
                    <ExposureHeadroomBar
                      title={selectedGroup.name}
                      capacity={groupHeadroom(
                        limits.find(
                          (row) => row.corporateGroupId === selectedGroup.id,
                        )!,
                      )}
                      embedded
                    />
                  ) : (
                    <p className="muted">
                      {limits === null
                        ? "Shared capacity unavailable."
                        : "No shared limit configured."}
                    </p>
                  )}
                  <button
                    className="text-button"
                    onClick={() => manageFacility(selectedGroup.id, true)}
                  >
                    View Credit Facilities
                  </button>
                </section>
                <section className="panel">
                  <div className="section-head">
                    <h2>Member companies</h2>
                    <button
                      disabled={!unassigned.length}
                      onClick={() =>
                        setAction({
                          title: "Add company",
                          path: "/corporate-group-memberships",
                          method: "POST",
                          body: { corporateGroupId: selectedGroup.id },
                          fields: [
                            {
                              name: "counterpartyId",
                              label: "Existing company",
                              options: unassigned.map((row) => ({
                                value: String(row.id),
                                label: row.name,
                              })),
                            },
                          ],
                          warning:
                            "Select an explicitly related company. A company can belong to only one corporate group.",
                          message: "Company added to the corporate group.",
                          sharedRecord: true,
                        })
                      }
                    >
                      Add company
                    </button>
                  </div>
                  {!unassigned.length && (
                    <p className="muted small">
                      No unassigned companies are available.
                    </p>
                  )}
                  {!selectedMembers.length ? (
                    <Empty title="No member companies" />
                  ) : (
                    <div className="rm-facility-grid">
                      {selectedMembers.map((member) => {
                        const record = companies.find(
                          (row) => row.id === member.counterpartyId,
                        );
                        return (
                          <article className="rm-group-member" key={member.id}>
                            {record ? (
                              <>
                                <h3 className="company-brand">
                                  <CompanyLogo
                                    name={record.name}
                                    domain={counterpartyLogoDomain(record.name)}
                                  />
                                  {record.name}
                                </h3>
                                <Badge status={record.status} />
                                <div className="ro-record-actions">
                                  <button
                                    className="text-button"
                                    onClick={() => openCompany(record.id)}
                                  >
                                    View counterparty
                                  </button>
                                  <button
                                    onClick={() =>
                                      setAction({
                                        title: "Remove company",
                                        path: `/corporate-group-memberships/${member.id}`,
                                        method: "DELETE",
                                        fields: [],
                                        warning: `Remove ${record.name} from ${selectedGroup.name}? Existing exposure restrictions still apply.`,
                                        message:
                                          "Company removed from the corporate group.",
                                        sharedRecord: true,
                                      })
                                    }
                                  >
                                    Remove company
                                  </button>
                                </div>
                              </>
                            ) : (
                              <p>Company details unavailable.</p>
                            )}
                          </article>
                        );
                      })}
                    </div>
                  )}
                </section>
              </>
            ) : (
              <section className="panel">
                <div className="toolbar">
                  <label className="search">
                    <Search size={17} />
                    <input
                      aria-label="Search corporate groups"
                      placeholder="Search corporate groups…"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                    />
                  </label>
                </div>
                {!visibleGroups.length ? (
                  <Empty title="No matching corporate groups" />
                ) : (
                  <div className="rm-facility-grid rm-group-grid">
                    {visibleGroups.map((group) => {
                      const limit = limits?.find(
                        (row) => row.corporateGroupId === group.id,
                      );
                      const rows = members.filter(
                        (row) => row.corporateGroupId === group.id,
                      );
                      return (
                        <article className="rm-group-card" key={group.id}>
                          {limit ? (
                            <ExposureHeadroomBar
                              title={group.name}
                              headingIcon={
                                <CorporateGroupLogo name={group.name} />
                              }
                              description={`${rows.length} member companies`}
                              capacity={groupHeadroom(limit)}
                              embedded
                            />
                          ) : (
                            <>
                              <h3 className="company-brand">
                                <CorporateGroupLogo name={group.name} />
                                {group.name}
                              </h3>
                              <p className="muted">
                                {rows.length} member companies ·{" "}
                                {limits === null
                                  ? "Capacity unavailable"
                                  : "No shared limit"}
                              </p>
                            </>
                          )}
                          <div className="ro-member-preview">
                            {rows.map((member) => {
                              const record = companies.find(
                                (row) => row.id === member.counterpartyId,
                              );
                              return record ? (
                                <span className="company-brand" key={member.id}>
                                  <CompanyLogo
                                    name={record.name}
                                    domain={counterpartyLogoDomain(record.name)}
                                  />
                                  {record.name}
                                </span>
                              ) : null;
                            })}
                          </div>
                          <button
                            className="text-button"
                            aria-label={`View members of ${group.name}`}
                            onClick={() => setGroupId(group.id)}
                          >
                            View members <ArrowUpRight size={15} />
                          </button>
                        </article>
                      );
                    })}
                  </div>
                )}
              </section>
            ))}
          {tab === "facilities" && (
            <>
              <section
                className="panel"
                aria-label="Individual Credit Facilities"
              >
                <h2>Individual Credit Facilities</h2>
                <NamedSelection
                  label="Counterparty"
                  value={facilityCompanyId}
                  options={companyOptions}
                  change={setFacilityCompanyId}
                />
                {selectedFacility ? (
                  <ExposureHeadroomBar
                    title={
                      companies.find(
                        (row) => row.id === selectedFacility.counterpartyId,
                      )?.name ?? selectedFacility.name
                    }
                    capacity={facilityHeadroom(selectedFacility)}
                    embedded
                  />
                ) : facilityCompanyId && facilities !== null ? (
                  <p className="muted">
                    No credit facility configured for this company.
                  </p>
                ) : null}
                <button
                  className="primary"
                  disabled={!facilityCompanyId || facilities === null || busy}
                  onClick={() => editLimit(false)}
                >
                  {selectedFacility
                    ? "Update credit facility"
                    : "Create credit facility"}
                </button>
              </section>
              <section
                className="panel"
                aria-label="Corporate Group Shared Limits"
              >
                <h2>Corporate Group Shared Limits</h2>
                <NamedSelection
                  label="Corporate group"
                  value={facilityGroupId}
                  options={groupOptions}
                  change={setFacilityGroupId}
                />
                {selectedLimit ? (
                  <ExposureHeadroomBar
                    title={
                      groups.find(
                        (row) => row.id === selectedLimit.corporateGroupId,
                      )?.name ?? selectedLimit.corporateGroupName
                    }
                    capacity={groupHeadroom(selectedLimit)}
                    embedded
                  />
                ) : facilityGroupId && limits !== null ? (
                  <p className="muted">
                    No shared limit configured for this group.
                  </p>
                ) : null}
                <button
                  className="primary"
                  disabled={!facilityGroupId || limits === null || busy}
                  onClick={() => editLimit(true)}
                >
                  {selectedLimit
                    ? "Update shared limit"
                    : "Create shared limit"}
                </button>
              </section>
            </>
          )}
        </>
      )}
      {action && (
        <RecordDialog
          action={action}
          close={() => setAction(null)}
          saved={refresh}
        />
      )}
      {financialId !== null && (
        <FinancialProfileEditor
          companyId={financialId}
          close={() => setFinancialId(null)}
          saved={() => {
            setFinancialId(null);
            refresh("Financial profile saved successfully.");
          }}
        />
      )}
    </div>
  );
}

function NamedSelection({
  label,
  value,
  options,
  change,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  change: (value: string) => void;
}) {
  const [query, setQuery] = useState("");
  return (
    <div className="ro-named-selection">
      <label className="field">
        <span>Search {label.toLowerCase()}</span>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </label>
      <label className="field">
        <span>{label}</span>
        <select
          aria-label={label}
          value={value}
          onChange={(e) => change(e.target.value)}
        >
          <option value="">Select by name</option>
          {options
            .filter(
              (row) =>
                row.value === value ||
                row.label
                  .toLocaleLowerCase()
                  .includes(query.trim().toLocaleLowerCase()),
            )
            .map((row) => (
              <option key={row.value} value={row.value}>
                {row.label}
              </option>
            ))}
        </select>
      </label>
    </div>
  );
}

function RecordDialog({
  action,
  close,
  saved,
}: {
  action: Action;
  close: () => void;
  saved: (message: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [restricted, setRestricted] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || restricted) return;
    const form = new FormData(event.currentTarget);
    const body: Record<string, unknown> = { ...action.body };
    for (const field of action.fields) {
      const value = String(form.get(field.name) ?? "").trim();
      body[field.name] =
        field.name === "counterpartyId" ? Number(value) : value;
    }
    setBusy(true);
    setError("");
    try {
      await api(
        action.path,
        action.method,
        Object.keys(body).length ? body : undefined,
      );
      saved(action.message);
      close();
    } catch (e) {
      setError(
        action.sharedRecord && e instanceof ApiError && e.status === 403
          ? "Editing is not permitted. This record may be shared across institutions or restricted by your permissions. No changes were saved."
          : (e as Error).message,
      );
      if (e instanceof ApiError && e.status === 403) setRestricted(true);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={action.title}
      onClose={() => {
        if (!busy) close();
      }}
    >
      <form onSubmit={submit}>
        {action.warning && <p className="notice">{action.warning}</p>}
        {action.fields.map((field) => (
          <label className="field" key={field.name}>
            <span>{field.label}</span>
            {field.options ? (
              <select
                name={field.name}
                required
                defaultValue={field.value ?? ""}
              >
                <option value="" disabled>
                  Select by name
                </option>
                {field.options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            ) : (
              <input
                name={field.name}
                type={field.type ?? "text"}
                defaultValue={field.value}
                required
                maxLength={field.type === "number" ? undefined : 255}
                min={field.type === "number" ? "0.01" : undefined}
                step={field.type === "number" ? "0.01" : undefined}
              />
            )}
          </label>
        ))}
        {error && <ErrorBox message={error} />}
        <div className="modal-actions">
          <button type="button" disabled={busy} onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy || restricted}>
            {busy ? "Saving…" : `Confirm ${action.title.toLowerCase()}`}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function FinancialProfileSummary({
  companyId,
  edit,
}: {
  companyId: number;
  edit: () => void;
}) {
  const [profile, setProfile] = useState<FinancialProfile | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    void api<FinancialProfile>(
      `/counterparties/${companyId}/financial-profile`,
      "GET",
      undefined,
      controller.signal,
    )
      .then((value) => {
        if (!controller.signal.aborted) setProfile(value);
      })
      .catch((e) => {
        if (
          !controller.signal.aborted &&
          !(e instanceof ApiError && e.status === 404)
        )
          setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [companyId]);
  return (
    <section className="panel">
      <div className="section-head">
        <h2>Financial profile</h2>
        <button onClick={edit}>Edit Financial Profile</button>
      </div>
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorBox message={error} />
      ) : profile ? (
        <dl className="ro-financial-ratios">
          {financialRatioFields.map((field) => (
            <div key={field.name}>
              <dt>{field.label}</dt>
              <dd>{profile[field.name]}</dd>
            </div>
          ))}
          <div>
            <dt>Financial year-end</dt>
            <dd>{profile.financialYearEnd}</dd>
          </div>
        </dl>
      ) : (
        <Empty title="No financial profile" />
      )}
    </section>
  );
}
