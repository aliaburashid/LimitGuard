import { Search, ArrowUpRight } from "lucide-react";
import { useState } from "react";
import { Empty, Modal } from "./ui";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import { requestReference } from "../utils/format";

export function WorkspaceSearch({
  portfolio,
  navigate,
}: {
  portfolio: RelationshipPortfolio;
  navigate: (page: string, id?: number) => void;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const term = query.trim().toLowerCase();
  const matches = term
    ? [
        ...(portfolio.individualCounterparties ?? [])
          .filter((company) =>
            `${company.name} CP-${company.id}`.toLowerCase().includes(term),
          )
          .slice(0, 5)
          .map((company) => ({
            id: company.id,
            page: "counterparties",
            kind: "Counterparty",
            name: company.name,
          })),
        ...(portfolio.requests ?? [])
          .filter((request) =>
            `${requestReference(request.id)} ${request.id} ${request.counterpartyName}`
              .toLowerCase()
              .includes(term),
          )
          .slice(0, 5)
          .map((request) => ({
            id: request.id,
            page: "requests",
            kind: "Credit request",
            name: `${requestReference(request.id)} · ${request.counterpartyName}`,
          })),
        ...(portfolio.groups ?? [])
          .filter((group) =>
            group.corporateGroupName.toLowerCase().includes(term),
          )
          .slice(0, 5)
          .map((group) => ({
            id: group.corporateGroupId,
            page: "corporate-groups",
            kind: "Corporate group",
            name: group.corporateGroupName,
          })),
      ]
    : [];
  function openResult(result: (typeof matches)[number]) {
    setOpen(false);
    navigate(result.page, result.id);
  }
  return (
    <>
      <button
        className="rm-global-search"
        aria-label="Search workspace"
        onClick={() => {
          setQuery("");
          setOpen(true);
        }}
      >
        <Search size={17} />
        <span>Search workspace…</span>
      </button>
      {open && (
        <Modal title="Search workspace" onClose={() => setOpen(false)}>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (matches[0]) openResult(matches[0]);
            }}
          >
            <label className="field">
              <span>Company, request or group</span>
              <input
                autoFocus
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search your workspace…"
              />
            </label>
          </form>
          <div className="rm-search-results" aria-live="polite">
            {matches.length ? (
              matches.map((result) => (
                <button
                  key={`${result.page}-${result.id}`}
                  onClick={() => openResult(result)}
                >
                  <span>
                    <small>{result.kind}</small>
                    <strong>{result.name}</strong>
                  </span>
                  <ArrowUpRight size={16} />
                </button>
              ))
            ) : (
              <Empty
                title={
                  term
                    ? portfolio.refreshing
                      ? "Loading workspace data"
                      : "No matching results"
                    : "Find a record"
                }
              >
                Search accessible counterparties, your requests and institution
                group limits.
              </Empty>
            )}
          </div>
        </Modal>
      )}
    </>
  );
}
