import { useEffect, useRef, useState } from "react";
import { api, BASE } from "../api/client";
import { useAuth } from "../context/Auth";

function GoogleMark() {
  return (
    <svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M21.6 12.23c0-.71-.06-1.39-.18-2.05H12v3.88h5.38a4.6 4.6 0 0 1-1.99 3.02v2.51h3.23c1.89-1.74 2.98-4.3 2.98-7.36Z"
      />
      <path
        fill="#34A853"
        d="M12 22c2.7 0 4.96-.9 6.62-2.41l-3.23-2.51c-.9.6-2.05.96-3.39.96-2.6 0-4.81-1.76-5.6-4.12H3.06v2.59A10 10 0 0 0 12 22Z"
      />
      <path
        fill="#FBBC05"
        d="M6.4 13.92A6 6 0 0 1 6.09 12c0-.67.11-1.32.31-1.92V7.49H3.06A10 10 0 0 0 2 12c0 1.61.39 3.14 1.06 4.51Z"
      />
      <path
        fill="#EA4335"
        d="M12 5.96c1.47 0 2.79.51 3.82 1.51l2.87-2.87A9.61 9.61 0 0 0 12 2a10 10 0 0 0-8.94 5.49l3.34 2.59c.79-2.36 3-4.12 5.6-4.12Z"
      />
    </svg>
  );
}
function MicrosoftMark() {
  return (
    <svg viewBox="0 0 21 21" width="19" height="19" aria-hidden="true">
      <path fill="#f25022" d="M1 1h9v9H1z" />
      <path fill="#7fba00" d="M11 1h9v9h-9z" />
      <path fill="#00a4ef" d="M1 11h9v9H1z" />
      <path fill="#ffb900" d="M11 11h9v9h-9z" />
    </svg>
  );
}
export default function WorkSignIn({
  busy,
  onBusyChange,
  onError,
}: {
  busy: boolean;
  onBusyChange: (value: boolean) => void;
  onError: (message: string) => void;
}) {
  const [providers, setProviders] = useState({
    google: false,
    microsoft: false,
  });
  const [loaded, setLoaded] = useState(false);
  const exchanged = useRef(false);
  const { loginWithSso } = useAuth();
  useEffect(() => {
    const controller = new AbortController();
    void api<typeof providers>(
      "/auth/sso/providers",
      "GET",
      undefined,
      controller.signal,
    )
      .then((value) => {
        if (!controller.signal.aborted)
          setProviders({
            google: value.google === true,
            microsoft: value.microsoft === true,
          });
      })
      .catch(() => {
        /* email sign-in remains available when provider setup is unavailable */
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoaded(true);
      });
    return () => controller.abort();
  }, []);
  useEffect(() => {
    const result = new URLSearchParams(location.hash.slice(1)).get("sso");
    if (!result || exchanged.current) return;
    exchanged.current = true;
    // removes the callback marker from browser history; no token is placed in the URL
    history.replaceState(null, "", location.pathname + location.search);
    if (result !== "complete") {
      onError(
        "Work account sign-in could not be completed. Contact your administrator or sign in with email.",
      );
      return;
    }
    onBusyChange(true);
    void loginWithSso()
      .catch(() =>
        onError(
          "Work account sign-in could not be completed. Please try again or sign in with email.",
        ),
      )
      .finally(() => onBusyChange(false));
  }, [loginWithSso, onBusyChange, onError]);
  return (
    <div className="work-signin">
      <div className="work-signin-divider">
        <span>or continue with</span>
      </div>
      <button
        type="button"
        disabled={busy || !loaded || !providers.microsoft}
        onClick={() => {
          onBusyChange(true);
          location.assign(BASE + "/auth/sso/authorize/microsoft");
        }}
      >
        <MicrosoftMark />
        Continue with Microsoft
      </button>
      <button
        type="button"
        disabled={busy || !loaded || !providers.google}
        onClick={() => {
          onBusyChange(true);
          location.assign(BASE + "/auth/sso/authorize/google");
        }}
      >
        <GoogleMark />
        Continue with Google
      </button>
    </div>
  );
}
