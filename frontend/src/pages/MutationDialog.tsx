import { useState, type FormEvent } from "react";
import { api } from "../api/client";
import { ErrorBox, Field, Modal } from "../components/ui";
export type Mutation = {
  title: string;
  path: string;
  method: string;
  fields: {
    name: string;
    label: string;
    type?: string;
    options?: string[];
    defaultValue?: string | number;
  }[];
  description?: string;
};
export function MutationDialog({
  action,
  onClose,
  onDone,
}: {
  action: Mutation;
  onClose: () => void;
  onDone: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    const form = e.currentTarget;
    const body: Record<string, unknown> = Object.fromEntries(
      new FormData(form),
    );
    for (const field of action.fields)
      if (
        field.type === "number" &&
        field.name !== "amount" &&
        field.name !== "limitAmount"
      )
        body[field.name] = Number(body[field.name]);
    setBusy(true);
    setError("");
    try {
      await api(
        action.path,
        action.method,
        action.fields.length ? body : undefined,
      );
      onDone();
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={action.title}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form onSubmit={submit}>
        <p className="muted">
          {action.description ||
            "This operation is validated and authorized by LimitGuard. Review the details before submitting."}
        </p>
        {action.fields.map((f) => (
          <Field key={f.name} {...f} />
        ))}
        {error && <ErrorBox message={error} />}
        <div className="modal-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Submitting…" : "Confirm " + action.title.toLowerCase()}
          </button>
        </div>
      </form>
    </Modal>
  );
}
