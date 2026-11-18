import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "../api/client";
import { useAuth } from "../context/Auth";
import type { FinancialProfile } from "../types";
import {
  financialRatioFields,
  localToday,
  validateFinancialProfile,
  type FinancialProfileInput,
} from "../utils/financialProfile";
import { ErrorBox, Loading, Modal } from "./ui";

import "./FinancialProfileEditor.css";

export const companyEditRestriction =
  "Editing is not permitted for this record. Shared counterparty details cannot be changed by one institution. No changes were saved.";

export function FinancialProfileEditor({
  companyId,
  close,
  saved,
}: {
  companyId: number;
  close: () => void;
  saved: () => void;
}) {
  const { user } = useAuth();
  const [profile, setProfile] = useState<FinancialProfile | null>();
  const [error, setError] = useState("");
  const [restricted, setRestricted] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (user?.role !== "RISK_OFFICER") return;
    const controller = new AbortController();
    setError("");
    setProfile(undefined);
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
        if (controller.signal.aborted) return;
        if (e instanceof ApiError && e.status === 404) setProfile(null);
        else
          setError(
            e instanceof ApiError && e.status === 403
              ? companyEditRestriction
              : e.message,
          );
      });
    return () => controller.abort();
  }, [companyId, revision, user?.role]);
  if (user?.role !== "RISK_OFFICER") return null;
  if (profile === undefined)
    return (
      <Modal title="Edit Financial Profile" onClose={close}>
        {error ? (
          <>
            <ErrorBox message={error} />
            <button onClick={() => setRevision((n) => n + 1)}>
              Retry financial profile
            </button>
          </>
        ) : (
          <Loading />
        )}
      </Modal>
    );
  return (
    <FinancialProfileForm
      companyId={companyId}
      profile={profile}
      restricted={restricted}
      onRestricted={() => setRestricted(true)}
      close={close}
      saved={() => {
        window.dispatchEvent(new Event("workspace-refresh"));
        saved();
      }}
    />
  );
}

function FinancialProfileForm({
  companyId,
  profile,
  restricted,
  onRestricted,
  close,
  saved,
}: {
  companyId: number;
  profile: FinancialProfile | null;
  restricted: boolean;
  onRestricted: () => void;
  close: () => void;
  saved: (profile: FinancialProfile) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [values, setValues] = useState<FinancialProfileInput>(
    () =>
      Object.fromEntries([
        ...financialRatioFields.map((f) => [
          f.name,
          profile ? String(profile[f.name]) : "",
        ]),
        ["financialYearEnd", profile?.financialYearEnd ?? ""],
      ]) as FinancialProfileInput,
  );
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy || restricted) return;
    const validation = validateFinancialProfile(values);
    if (validation) {
      setError(validation);
      return;
    }
    setBusy(true);
    setError("");
    try {
      // Decimal strings preserve the entered precision for the existing BigDecimal DTO.
      const result = await api<FinancialProfile>(
        `/counterparties/${companyId}/financial-profile`,
        "PUT",
        values,
      );
      saved(result);
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        onRestricted();
        setError(companyEditRestriction);
      } else setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      className="financial-profile-editor"
      title="Edit Financial Profile"
      onClose={() => {
        if (!busy) close();
      }}
    >
      <form onSubmit={submit}>
        <p className="muted">
          Enter percentages as 8 for 8%, and multiples as 1.5 for 1.5×.
        </p>
        <fieldset
          className="financial-profile-form"
          disabled={busy || restricted}
        >
          {financialRatioFields.map((field) => (
            <label className="field" key={field.name}>
              <span>{field.label}</span>
              <input
                name={field.name}
                type="number"
                step="any"
                required
                min={"min" in field ? field.min : undefined}
                max={"max" in field ? field.max : undefined}
                value={values[field.name]}
                onChange={(e) =>
                  setValues((v) => ({ ...v, [field.name]: e.target.value }))
                }
              />
            </label>
          ))}
          <label className="field">
            <span>Financial year end</span>
            <input
              name="financialYearEnd"
              type="date"
              required
              max={localToday()}
              value={values.financialYearEnd}
              onChange={(e) =>
                setValues((v) => ({ ...v, financialYearEnd: e.target.value }))
              }
            />
          </label>
        </fieldset>
        {error && <ErrorBox message={error} />}
        <div className="modal-actions">
          <button type="button" disabled={busy} onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy || restricted}>
            {busy ? "Saving…" : "Save financial profile"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
