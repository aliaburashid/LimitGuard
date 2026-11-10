import { useEffect, useState } from "react";
import { api } from "../api/client";

type Institution = { id: number; name: string };

export default function InstitutionSelect({
  onAvailabilityChange,
}: {
  onAvailabilityChange: (available: boolean) => void;
}) {
  const [institutions, setInstitutions] = useState<Institution[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    // cancels the directory request if the user leaves registration
    const controller = new AbortController();
    setLoading(true);
    setError("");
    onAvailabilityChange(false);
    void api<Institution[]>(
      "/financial-institutions/directory",
      "GET",
      undefined,
      controller.signal,
    )
      .then((directory) => {
        if (controller.signal.aborted) return;
        if (
          !Array.isArray(directory) ||
          directory.some(
            (item) =>
              !Number.isInteger(item.id) ||
              item.id <= 0 ||
              typeof item.name !== "string",
          )
        )
          throw new Error(
            "The institution directory is unavailable. Please try again.",
          );
        setInstitutions(directory);
        onAvailabilityChange(directory.length > 0);
      })
      .catch((reason: Error) => {
        if (!controller.signal.aborted) setError(reason.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [attempt, onAvailabilityChange]);

  return (
    <div className="institution-directory">
      <div className="field">
        <label htmlFor="registration-institution">Financial institution</label>
        <select
          id="registration-institution"
          name="financialInstitutionId"
          required
          defaultValue=""
          disabled={loading || !!error || !institutions.length}
          aria-describedby="institution-directory-status"
        >
          <option value="" disabled>
            {loading ? "Loading institutions…" : "Select your institution"}
          </option>
          {institutions.map((institution) => (
            <option key={institution.id} value={institution.id}>
              {institution.name}
            </option>
          ))}
        </select>
      </div>
      <div id="institution-directory-status" role={error ? "alert" : "status"}>
        {loading ? (
          <small>Loading the institution directory…</small>
        ) : error ? (
          <>
            <small>{error}</small>
            <button
              type="button"
              className="text-button"
              onClick={() => setAttempt((value) => value + 1)}
            >
              Retry institution directory
            </button>
          </>
        ) : !institutions.length ? (
          <small>
            No active institutions are available. Contact your administrator.
          </small>
        ) : (
          <small>Choose the institution your account belongs to.</small>
        )}
      </div>
    </div>
  );
}
