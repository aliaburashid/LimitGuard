import { ArrowRight, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../api/client";
import {
  Empty,
  ErrorBox,
  ExposurePanel,
  Field,
  Loading,
} from "../components/ui";
import { useAuth } from "../context/Auth";
import type { Exposure } from "../types";
import type { Mutation } from "./MutationDialog";
export function ExposurePage({
  mutate,
  version,
}: {
  mutate: (a: Mutation) => void;
  version: number;
}) {
  const { user } = useAuth();
  const [id, setId] = useState("");
  const [data, setData] = useState<Exposure | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function load(value: string) {
    setBusy(true);
    setError("");
    setData(null);
    try {
      setData(await api<Exposure>(`/credit-limits/${value}/exposure`));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (id && data) void load(id);
  }, [version]);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">CAPACITY & CONTROL</span>
          <h1>Credit exposure</h1>
          <p>Understand available capacity before committing credit.</p>
        </div>
        {user?.role === "RISK_OFFICER" && (
          <button
            className="primary"
            onClick={() =>
              mutate({
                title: "Create credit limit",
                path: "/credit-limits",
                method: "POST",
                fields: [
                  {
                    name: "financialInstitutionId",
                    label: "Financial institution ID",
                    type: "number",
                  },
                  {
                    name: "counterpartyId",
                    label: "Counterparty ID",
                    type: "number",
                  },
                  {
                    name: "limitAmount",
                    label: "Limit amount (£)",
                    type: "number",
                  },
                ],
              })
            }
          >
            <Plus size={17} />
            New credit limit
          </button>
        )}
      </div>
      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Find a credit limit</h2>
            <p className="muted">
              The API supports lookup by ID. A credit-limit directory is not
              available.
            </p>
          </div>
        </div>
        <form
          className="lookup"
          onSubmit={(e) => {
            e.preventDefault();
            if (busy) return;
            const value = String(new FormData(e.currentTarget).get("id"));
            if (!/^\d+$/.test(value) || Number(value) < 1) return;
            setId(value);
            void load(value);
          }}
        >
          <Field name="id" label="Credit-limit ID" type="number" />
          <button className="primary" disabled={busy}>
            {busy ? "Loading…" : "View exposure"}
            <ArrowRight size={16} />
          </button>
        </form>
        {error && <ErrorBox message={error} />}
      </section>
      {busy ? (
        <Loading />
      ) : data ? (
        <>
          <ExposurePanel data={data} />
          <section className="panel">
            <div className="section-head">
              <div>
                <h2>Credit-limit controls</h2>
                <p className="muted">
                  Counterparty #{data.counterpartyId} · Limit #
                  {data.creditLimitId}
                </p>
              </div>
              {user?.role === "RISK_OFFICER" && (
                <button
                  onClick={() =>
                    mutate({
                      title: "Update credit limit",
                      path: `/credit-limits/${data.creditLimitId}`,
                      method: "PUT",
                      fields: [
                        {
                          name: "limitAmount",
                          label: "New limit amount (£)",
                          type: "number",
                          defaultValue: data.limitAmount,
                        },
                      ],
                      description:
                        "Changing the limit affects available capacity. Reductions remain subject to existing backend exposure controls.",
                    })
                  }
                >
                  Update limit
                </button>
              )}
            </div>
            <div className="notice">
              Utilization includes used and reserved exposure. It is a capacity
              measure, not a credit-risk score. Currency display is GBP; the API
              does not return a currency field.
            </div>
          </section>
        </>
      ) : (
        <section className="panel">
          <Empty title="Exposure starts with a limit">
            Enter a known credit-limit ID to retrieve current exposure and
            available headroom.
          </Empty>
        </section>
      )}
    </>
  );
}
