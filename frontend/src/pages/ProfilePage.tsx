import { useRef, useState } from "react";
import { Camera } from "lucide-react";
import { Avatar } from "../components/Avatar";
import { PictureEditor } from "../components/PictureEditor";
import { Badge, ErrorBox } from "../components/ui";
import { useAuth } from "../context/Auth";
import type { Mutation } from "./MutationDialog";
export function ProfilePage({
  mutate,
  notify,
}: {
  mutate: (a: Mutation) => void;
  notify: (s: string) => void;
}) {
  const { user, savePicture, picture } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const input = useRef<HTMLInputElement>(null);
  function chooseFile(selected?: File) {
    setError("");
    if (!selected) return;
    if (!selected.type.startsWith("image/")) {
      setError("Choose an image file for your profile photo.");
      return;
    }
    setFile(selected);
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">ACCOUNT & SECURITY</span>
          <h1>Your profile</h1>
          <p>Manage your identity and account security.</p>
        </div>
      </div>
      <div className="profile-grid">
        <section className="panel">
          <div className="profile-identity">
            <button
              className="profile-photo-button"
              aria-label="Change profile photo"
              onClick={() => input.current?.click()}
            >
              <Avatar large />
              <span className="photo-camera">
                <Camera size={15} />
              </span>
            </button>
            <button
              className="text-button photo-change"
              onClick={() => input.current?.click()}
            >
              {picture ? "Change photo" : "Add profile photo"}
            </button>
            <h2>
              {user?.firstName} {user?.lastName}
            </h2>
            <Badge status={user!.role} />
          </div>
          <dl>
            <dt>Work email</dt>
            <dd>{user?.email}</dd>
            <dt>Financial institution</dt>
            <dd>{user?.financialInstitution?.name || "Not provided"}</dd>
            <dt>Account reference</dt>
            <dd className="mono">USR-{user?.id}</dd>
          </dl>
          <button
            onClick={() =>
              mutate({
                title: "Update profile",
                path: "/auth/users/profile",
                method: "PATCH",
                fields: [
                  {
                    name: "firstName",
                    label: "First name",
                    defaultValue: user?.firstName,
                  },
                  {
                    name: "lastName",
                    label: "Last name",
                    defaultValue: user?.lastName,
                  },
                  {
                    name: "email",
                    label: "Work email",
                    type: "email",
                    defaultValue: user?.email,
                  },
                ],
              })
            }
          >
            Edit profile
          </button>
        </section>
        <section className="panel">
          <h2>Security & profile image</h2>
          <p className="muted">
            Keep your credentials private and update your password regularly.
          </p>
          <button
            onClick={() =>
              mutate({
                title: "Change password",
                path: "/auth/users/change-password",
                method: "PATCH",
                fields: [
                  {
                    name: "currentPassword",
                    label: "Current password",
                    type: "password",
                  },
                  {
                    name: "newPassword",
                    label: "New password",
                    type: "password",
                  },
                ],
              })
            }
          >
            Change password
          </button>
          <hr />
          <h3>Profile photo</h3>
          <p className="muted">
            Choose a photo, then drag and zoom to get the right circular crop.
          </p>
          <input
            ref={input}
            className="sr-only"
            tabIndex={-1}
            aria-label="Choose profile photo"
            type="file"
            accept="image/*"
            onChange={(e) => {
              chooseFile(e.currentTarget.files?.[0]);
              e.currentTarget.value = "";
            }}
          />
          <button onClick={() => input.current?.click()}>
            <Camera size={16} />
            {picture ? "Change photo" : "Choose photo"}
          </button>
          {user?.profilePicturePath && !picture && (
            <p className="small photo-session-note">
              A photo is saved on your account. Image retrieval is currently
              unavailable; choose a photo to show a preview in this session.
            </p>
          )}
          {picture && (
            <p className="small photo-session-note">
              Photo saved. The preview stays visible throughout this session.
            </p>
          )}
          {error && <ErrorBox message={error} />}
        </section>
      </div>
      {file && (
        <PictureEditor
          file={file}
          onClose={() => setFile(null)}
          onSave={async (blob) => {
            await savePicture(blob);
            notify("Profile photo updated");
          }}
        />
      )}
    </>
  );
}
