import { ArrowUpRight, Plus, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../api/client";
import {
  Badge,
  Empty,
  ErrorBox,
  Loading,
  Modal,
  Pager,
} from "../components/ui";
import { useAuth } from "../context/Auth";
import type { Counterparty, Page } from "../types";
import { date, label } from "../utils/format";
import type { Mutation } from "./MutationDialog";
export function Counterparties({
  version,
  mutate,
}: {
  version: number;
  mutate: (a: Mutation) => void;
}) {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [data, setData] = useState<Page<Counterparty> | null>(null);
  const [error, setError] = useState("");
  const [detail, setDetail] = useState<Counterparty | null>(null);
  useEffect(() => {
    const t = setTimeout(() => {
      setQuery(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError("");
    api<Page<Counterparty>>(
      `/counterparties?page=${page}&size=10&name=${encodeURIComponent(query)}`,
      "GET",
      undefined,
      controller.signal,
    )
      .then(setData)
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    return () => controller.abort();
  }, [page, query, version]);
  const fields = [{ name: "name", label: "Counterparty name" }];
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">RELATIONSHIP REGISTER</span>
          <h1>Counterparties</h1>
          <p>A controlled directory of your institutional relationships.</p>
        </div>
        {user?.role === "RISK_OFFICER" && (
          <button
            className="primary"
            onClick={() =>
              mutate({
                title: "Create counterparty",
                path: "/counterparties",
                method: "POST",
                fields,
              })
            }
          >
            <Plus size={17} />
            New counterparty
          </button>
        )}
      </div>
      <section className="panel">
        <div className="toolbar">
          <label className="search">
            <Search size={17} />
            <input
              aria-label="Search counterparties by name"
              placeholder="Search counterparties…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
          <select
            aria-label="Filter current page by status"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            {["ALL", "ACTIVE", "FROZEN", "CLOSED"].map((x) => (
              <option key={x} value={x}>
                {x === "ALL" ? "All statuses (this page)" : label(x)}
              </option>
            ))}
          </select>
        </div>
        {error ? (
          <ErrorBox message={error} />
        ) : !data ? (
          <Loading />
        ) : !data.content.filter((c) => filter === "ALL" || c.status === filter)
            .length ? (
          <Empty />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Counterparty name</th>
                  <th>Reference</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Last updated</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content
                  .filter((c) => filter === "ALL" || c.status === filter)
                  .map((c) => (
                    <tr key={c.id}>
                      <td>
                        <button
                          className="table-link"
                          onClick={() => setDetail(c)}
                        >
                          <span className="initial">
                            {c.name.slice(0, 2).toUpperCase()}
                          </span>
                          {c.name}
                        </button>
                      </td>
                      <td className="mono">CP-{c.id}</td>
                      <td>
                        <Badge status={c.status} />
                      </td>
                      <td>{date(c.createdAt)}</td>
                      <td>{date(c.updatedAt)}</td>
                      <td>
                        <button
                          className="icon-button"
                          onClick={() => setDetail(c)}
                          aria-label={`View ${c.name}`}
                        >
                          <ArrowUpRight size={18} />
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
      {detail && (
        <Modal title={detail.name} onClose={() => setDetail(null)}>
          <div className="detail-meta">
            <Badge status={detail.status} />
            <span className="mono">CP-{detail.id}</span>
          </div>
          <dl>
            <dt>Created</dt>
            <dd>{date(detail.createdAt)}</dd>
            <dt>Updated</dt>
            <dd>{date(detail.updatedAt)}</dd>
          </dl>
          <div className="notice">
            Institution links, credit-limit IDs and request history are not
            included in the counterparty response. Use Exposure with a known
            credit-limit ID.
          </div>
          {user?.role === "RISK_OFFICER" && (
            <div className="modal-actions">
              <button
                onClick={() => {
                  setDetail(null);
                  mutate({
                    title: "Update counterparty",
                    path: `/counterparties/${detail.id}`,
                    method: "PUT",
                    fields: [{ ...fields[0], defaultValue: detail.name }],
                  });
                }}
              >
                Edit name
              </button>
              <button
                onClick={() => {
                  setDetail(null);
                  mutate({
                    title: "Change counterparty status",
                    path: `/counterparties/${detail.id}/status`,
                    method: "PATCH",
                    fields: [
                      {
                        name: "status",
                        label: "New status",
                        options: ["ACTIVE", "FROZEN", "CLOSED"],
                        defaultValue: detail.status,
                      },
                    ],
                    description:
                      "A status change can restrict credit operations for this counterparty. The backend validates permitted transitions.",
                  });
                }}
              >
                Change status
              </button>
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
