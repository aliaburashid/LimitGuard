import { ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../api/client";
import { Empty, ErrorBox, Loading, Pager } from "../components/ui";
import type { Audit, Page } from "../types";
import { date, label } from "../utils/format";
export function AuditPage({ version }: { version: number }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Audit> | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    setData(null);
    setError("");
    api<Page<Audit>>(`/audit-logs?page=${page}&size=10&sort=createdAt,desc`)
      .then((d) => {
        if (active) setData(d);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [page, version]);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">GOVERNANCE & TRACEABILITY</span>
          <h1>Audit log</h1>
          <p>A chronological record of actions across LimitGuard.</p>
        </div>
        <span className="pill">
          <ShieldCheck size={15} />
          Administrative access
        </span>
      </div>
      <section className="panel">
        {error ? (
          <ErrorBox message={error} />
        ) : !data ? (
          <Loading />
        ) : !data.content.length ? (
          <Empty title="No audit entries" />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Action</th>
                  <th>Entity</th>
                  <th>Actor</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap">{date(a.createdAt)}</td>
                    <td>
                      <span className="audit-action">{label(a.action)}</span>
                    </td>
                    <td>
                      {label(a.entityType)}{" "}
                      <span className="mono">#{a.entityId}</span>
                    </td>
                    <td>
                      {a.actorName || "System"}
                      <small className="block">
                        {a.actorId ? `User #${a.actorId}` : ""}
                      </small>
                    </td>
                    <td className="audit-details">{a.details}</td>
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
    </>
  );
}
