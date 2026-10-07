import { Plus, ShieldCheck } from "lucide-react";
import { useState } from "react";
import type { Mutation } from "./MutationDialog";
export function AdminPage({
  institutions,
  mutate,
}: {
  institutions: boolean;
  mutate: (a: Mutation) => void;
}) {
  const [id, setId] = useState("");
  const fields = [
    { name: "name", label: "Institution name" },
    { name: "status", label: "Status", options: ["ACTIVE", "INACTIVE"] },
  ];
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">SYSTEM ADMINISTRATION</span>
          <h1>{institutions ? "Financial institutions" : "User access"}</h1>
          <p>
            {institutions
              ? "Maintain the institutions operating within LimitGuard."
              : "Manage roles and account access with explicit controls."}
          </p>
        </div>
        {institutions && (
          <button
            className="primary"
            onClick={() =>
              mutate({
                title: "Create institution",
                path: "/financial-institutions",
                method: "POST",
                fields,
              })
            }
          >
            <Plus size={17} />
            New institution
          </button>
        )}
      </div>
      <section className="panel admin-panel">
        <span className="admin-icon">
          <ShieldCheck size={27} />
        </span>
        <h2>
          {institutions ? "Update a known institution" : "Manage a known user"}
        </h2>
        <p className="muted">
          {institutions ? "Institution" : "User"} listing and lookup endpoints
          are not available in the current backend. Enter an ID provided by your
          administrator. No account data is inferred.
        </p>
        <label className="field">
          <span>{institutions ? "Institution" : "User"} ID</span>
          <input
            type="number"
            min="1"
            step="1"
            value={id}
            onChange={(e) => setId(e.target.value)}
            placeholder="Enter ID"
          />
        </label>
        <div className="inline-actions">
          {institutions ? (
            <button
              disabled={!/^\d+$/.test(id) || Number(id) < 1}
              onClick={() =>
                mutate({
                  title: "Update institution",
                  path: `/financial-institutions/${id}`,
                  method: "PUT",
                  fields,
                })
              }
            >
              Update institution
            </button>
          ) : (
            <>
              <button
                disabled={!/^\d+$/.test(id) || Number(id) < 1}
                onClick={() =>
                  mutate({
                    title: "Change user role",
                    path: `/auth/users/${id}/role`,
                    method: "PATCH",
                    fields: [
                      {
                        name: "role",
                        label: "New role",
                        options: [
                          "RELATIONSHIP_MANAGER",
                          "RISK_OFFICER",
                          "ADMIN",
                        ],
                      },
                    ],
                    description: `This changes permissions for user #${id}. Confirm the user ID and intended role before submitting.`,
                  })
                }
              >
                Change role
              </button>
              <button
                className="danger"
                disabled={!/^\d+$/.test(id) || Number(id) < 1}
                onClick={() =>
                  mutate({
                    title: "Deactivate user",
                    path: `/auth/users/${id}/deactivate`,
                    method: "PATCH",
                    fields: [{ name: "reason", label: "Deactivation reason" }],
                    description: `This removes access for user #${id}. Confirm the account ID before submitting.`,
                  })
                }
              >
                Deactivate account
              </button>
            </>
          )}
        </div>
      </section>
    </>
  );
}
