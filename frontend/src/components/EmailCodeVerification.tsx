import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowRight, Mail } from "lucide-react";
import { api } from "../api/client";
import EmailCodeInput from "./EmailCodeInput";
import { useCodeTiming } from "../hooks/useCodeTiming";
import { ErrorBox } from "./ui";

export type VerificationChallenge = {
  challengeId: string;
  email: string;
  expiresAt: string;
  resendAfter: string;
};
const storageKey = "limitguard.pending-verification";
export function readPendingVerification(
  key = storageKey,
): VerificationChallenge | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || "null");
    return value &&
      typeof value.challengeId === "string" &&
      typeof value.email === "string" &&
      Number.isFinite(Date.parse(value.expiresAt)) &&
      Number.isFinite(Date.parse(value.resendAfter))
      ? value
      : null;
  } catch {
    return null;
  }
}
export function savePendingVerification(
  value: VerificationChallenge | null,
  key = storageKey,
) {
  // keeps the pending screen through a reload without storing a password or code
  try {
    if (value) sessionStorage.setItem(key, JSON.stringify(value));
    else sessionStorage.removeItem(key);
  } catch {
    /* verification still works if browser storage is unavailable */
  }
}
export default function EmailCodeVerification({
  challenge,
  onUpdate,
  onVerified,
  onBack,
}: {
  challenge: VerificationChallenge;
  onUpdate: (value: VerificationChallenge) => void;
  onVerified: () => void;
  onBack: () => void;
}) {
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [changingEmail, setChangingEmail] = useState(false);
  const [email, setEmail] = useState(challenge.email);
  const input = useRef<HTMLInputElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const { cooldown, expired } = useCodeTiming(challenge);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, []);

  async function verify(event: FormEvent) {
    event.preventDefault();
    if (busy || code.length !== 6 || expired) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api("/auth/users/verification-code/verify", "POST", {
        challengeId: challenge.challengeId,
        code,
      });
      onVerified();
    } catch (reason) {
      setError((reason as Error).message);
      input.current?.focus();
    } finally {
      setBusy(false);
    }
  }
  async function send(changeEmail = false) {
    if (busy || cooldown > 0) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const next = await api<VerificationChallenge>(
        `/auth/users/verification-code/${changeEmail ? "change-email" : "resend"}`,
        "POST",
        {
          challengeId: challenge.challengeId,
          ...(changeEmail ? { email } : {}),
        },
      );
      onUpdate(next);
      setCode("");
      setChangingEmail(false);
      setNotice("A new code has been sent. Use the latest email.");
      input.current?.focus();
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="email-code-screen">
      <span className="auth-lock">
        <Mail size={25} aria-hidden="true" />
      </span>
      <h2 ref={heading} tabIndex={-1}>
        Check your inbox!
      </h2>
      <p>
        We’ve sent a six-digit verification code to{" "}
        <strong>{challenge.email}</strong>. Enter it below to verify your work
        email.
      </p>
      {error && <ErrorBox message={error} />}
      {notice && (
        <p className="success" role="status">
          {notice}
        </p>
      )}
      {changingEmail ? (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void send(true);
          }}
        >
          <label className="field">
            <span>New work email</span>
            <input
              type="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
            />
          </label>
          <button
            className="primary auth-submit"
            disabled={busy || cooldown > 0}
          >
            Send new code <ArrowRight size={17} />
          </button>
          {cooldown > 0 && (
            <p className="otp-timing">
              You can send a new code in {cooldown}s.
            </p>
          )}
          <button
            className="text-button"
            type="button"
            disabled={busy}
            onClick={() => setChangingEmail(false)}
          >
            Keep current email
          </button>
        </form>
      ) : (
        <form onSubmit={verify}>
          <EmailCodeInput
            id="email-verification-code"
            label="Six-digit verification code"
            value={code}
            onChange={setCode}
            inputRef={input}
            disabled={busy}
          />
          <p id="email-verification-code-timing" className="otp-timing">
            {expired
              ? "Your code has expired. Request a new one below."
              : "Your code expires 10 minutes after it is sent."}
          </p>
          <button
            className="primary auth-submit"
            disabled={busy || code.length !== 6 || expired}
          >
            {busy ? "Please wait…" : "Verify Email"}
            <ArrowRight size={17} aria-hidden="true" />
          </button>
        </form>
      )}
      <div className="otp-actions">
        <span>Didn’t receive it?</span>
        <button
          className="text-button"
          type="button"
          disabled={busy || cooldown > 0}
          onClick={() => void send()}
        >
          {cooldown > 0 ? `Resend code in ${cooldown}s` : "Resend Code"}
        </button>
      </div>
      <button
        className="text-button otp-change-email"
        type="button"
        disabled={busy}
        onClick={() => {
          setChangingEmail(true);
          setError("");
        }}
      >
        Change email address
      </button>
      <button
        className="text-button otp-back"
        type="button"
        disabled={busy}
        onClick={onBack}
      >
        Back to sign in
      </button>
    </div>
  );
}
