import { ArrowRight, Plus } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import { Empty, ErrorBox, ExposurePanel, Loading } from "../components/ui";
import { CorporateGroupManagement } from "../components/CorporateGroupManagement";
import type { Counterparty } from "../types";
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
  const lookupInput = useRef<HTMLInputElement>(null);
  const lookupController = useRef<AbortController | null>(null);
  const [expectedCompany, setExpectedCompany] = useState<Counterparty | null>(
    null,
  );
  const [id, setId] = useState("");
  const [data, setData] = useState<Exposure | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function load(value: string) {
    lookupController.current?.abort();
    const controller = new AbortController();
    lookupController.current = controller;
    setBusy(true);
    setError("");
    setData(null);
    try {
      const result = await api<Exposure>(
        `/credit-limits/${value}/exposure`,
        "GET",
        undefined,
        controller.signal,
      );
      if (expectedCompany && result.counterpartyId !== expectedCompany.id)
        throw new Error(
          "This facility belongs to a different company. Enter the selected company’s facility ID.",
        );
      if (!controller.signal.aborted) setData(result);
    } catch (e) {
      if (!controller.signal.aborted) setError((e as Error).message);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  useEffect(() => {
    if (id && data) void load(id);
  }, [version]);
  useEffect(() => () => lookupController.current?.abort(), []);
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
                    label: "Limit amount (BHD)",
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
      <CorporateGroupManagement
        version={version}
        resolvedFacility={data}
        findFacility={(company) => {
          setExpectedCompany(company);
          setId("");
          setData(null);
          setError("");
          lookupInput.current?.focus();
        }}
      />
      <section className="panel">
        <div className="section-head">
          <div>
            <h2>Find a credit limit</h2>
            <p className="muted">
              {expectedCompany
                ? `Enter the credit-limit ID for ${expectedCompany.name}.`
                : "Retrieve an individual facility by its credit-limit ID."}
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
          <label className="field">
            <span>Credit-limit ID</span>
            <input
              ref={lookupInput}
              name="id"
              aria-label="Credit-limit ID"
              type="number"
              min="1"
              step="1"
              required
              value={id}
              onChange={(e) => setId(e.target.value)}
            />
          </label>
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
                          label: "New limit amount (BHD)",
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
              measure, not a credit-risk score. Amounts are displayed in
              Bahraini dinars (BHD), rounded to whole dinars.
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
