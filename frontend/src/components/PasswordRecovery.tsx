import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowRight, LockKeyhole, Mail } from "lucide-react";
import { api } from "../api/client";
import { ErrorBox } from "./ui";
import EmailCodeInput from "./EmailCodeInput";
import { useCodeTiming } from "../hooks/useCodeTiming";
import {
  readPendingVerification,
  savePendingVerification,
  type VerificationChallenge,
} from "./EmailCodeVerification";

const storageKey = "limitguard.pending-password-recovery";
export function readPendingPasswordRecovery() {
  return readPendingVerification(storageKey);
}
function saveRecovery(challenge: VerificationChallenge | null) {
  savePendingVerification(challenge, storageKey);
}
export default function PasswordRecovery({
  onBack,
  onComplete,
}: {
  onBack: () => void;
  onComplete: () => void;
}) {
  const [challenge, setChallenge] = useState(readPendingPasswordRecovery);
  const [step, setStep] = useState<"email" | "code" | "password">(
    challenge ? "code" : "email",
  );
  const [email, setEmail] = useState(challenge?.email || "");
  const [code, setCode] = useState("");
  // the reset token stays in component memory and is never displayed or persisted
  const [resetToken, setResetToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const { cooldown, expired } = useCodeTiming(challenge);
  const title =
    step === "email"
      ? "Recover your account"
      : step === "code"
        ? "Check your inbox!"
        : "Set a new password";
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, [step]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    if (step === "password" && newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (step === "email") {
        const nextChallenge = await api<VerificationChallenge>(
          "/auth/users/password-reset-code/request",
          "POST",
          { email },
        );
        setChallenge(nextChallenge);
        saveRecovery(nextChallenge);
        setCode("");
        setStep("code");
      } else if (step === "code" && challenge) {
        const verified = await api<{ token: string }>(
          "/auth/users/password-reset-code/verify",
          "POST",
          { challengeId: challenge.challengeId, code },
        );
        setResetToken(verified.token);
        setCode("");
        setStep("password");
      } else if (step === "password") {
        await api("/auth/users/reset-password", "POST", {
          token: resetToken,
          newPassword,
        });
        saveRecovery(null);
        setResetToken("");
        onComplete();
      }
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function resendCode() {
    if (busy || !challenge || cooldown > 0) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const nextChallenge = await api<VerificationChallenge>(
        "/auth/users/password-reset-code/resend",
        "POST",
        { challengeId: challenge.challengeId },
      );
      setChallenge(nextChallenge);
      saveRecovery(nextChallenge);
      setCode("");
      setNotice(
        "A new code has been sent if this address has an active account.",
      );
      input.current?.focus();
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function startAgain() {
    saveRecovery(null);
    setChallenge(null);
    setResetToken("");
    setCode("");
    setNewPassword("");
    setConfirmPassword("");
    setStep("email");
    setError("");
    setNotice("");
  }

  return (
    <div className="email-code-screen password-recovery">
      <span className="auth-lock">
        {step === "code" ? (
          <Mail size={25} aria-hidden="true" />
        ) : (
          <LockKeyhole size={25} aria-hidden="true" />
        )}
      </span>
      <h2 ref={heading} tabIndex={-1}>
        {title}
      </h2>
      <p>
        {step === "email" ? (
          "Enter your registered work email to receive a password reset code."
        ) : step === "code" ? (
          <>
            If <strong>{challenge?.email}</strong> has an active account, a
            six-digit code has been sent. Enter it below to continue.
          </>
        ) : (
          "Choose a new password for your LimitGuard account."
        )}
      </p>
      {error && <ErrorBox message={error} />}
      {notice && (
        <p className="success" role="status">
          {notice}
        </p>
      )}
      <form onSubmit={submit}>
        {step === "email" && (
          <label className="field">
            <span>Work email</span>
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              disabled={busy}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
        )}
        {step === "code" && (
          <>
            <EmailCodeInput
              id="password-reset-code"
              label="Six-digit password reset code"
              value={code}
              onChange={setCode}
              inputRef={input}
              disabled={busy}
            />
            <p id="password-reset-code-timing" className="otp-timing">
              {expired
                ? "Your code has expired. Request a new one below."
                : "Your code expires 10 minutes after it is sent."}
            </p>
          </>
        )}
        {step === "password" && (
          <>
            <label className="field">
              <span>New password</span>
              <input
                type="password"
                autoComplete="new-password"
                minLength={8}
                pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).+"
                required
                value={newPassword}
                disabled={busy}
                onChange={(event) => setNewPassword(event.target.value)}
                aria-describedby="password-reset-rules"
              />
            </label>
            <p className="otp-timing" id="password-reset-rules">
              Use at least 8 characters, including uppercase, lowercase, a
              number and a special character.
            </p>
            <label className="field">
              <span>Confirm new password</span>
              <input
                type="password"
                autoComplete="new-password"
                minLength={8}
                required
                value={confirmPassword}
                disabled={busy}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </label>
          </>
        )}
        <button
          className="primary auth-submit"
          disabled={busy || (step === "code" && (code.length !== 6 || expired))}
        >
          {busy
            ? "Please wait…"
            : step === "email"
              ? "Send reset code"
              : step === "code"
                ? "Verify Code"
                : "Reset password"}
          <ArrowRight size={17} aria-hidden="true" />
        </button>
      </form>
      {step === "code" && (
        <div className="otp-actions">
          <span>Didn’t receive it?</span>
          <button
            className="text-button"
            type="button"
            disabled={busy || cooldown > 0}
            onClick={() => void resendCode()}
          >
            {cooldown > 0 ? `Resend code in ${cooldown}s` : "Resend Code"}
          </button>
        </div>
      )}
      {step !== "email" && (
        <button
          className="text-button otp-change-email"
          type="button"
          disabled={busy}
          onClick={startAgain}
        >
          {step === "code" ? "Change email address" : "Request another code"}
        </button>
      )}
      <button
        className="text-button otp-back"
        type="button"
        disabled={busy}
        onClick={() => {
          saveRecovery(null);
          onBack();
        }}
      >
        Back to sign in
      </button>
    </div>
  );
}
