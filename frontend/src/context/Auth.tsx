import {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  type ReactNode,
} from "react";
import { api, apiBlob, setToken } from "../api/client";
import type { Profile } from "../types";
const sessionKey = "limitguard.session";

function savedToken() {
  try {
    return sessionStorage.getItem(sessionKey);
  } catch {
    return null;
  }
}

function storeToken(token: string | null) {
  try {
    if (token) sessionStorage.setItem(sessionKey, token);
    else sessionStorage.removeItem(sessionKey);
  } catch {
    // sign-in still works when browser storage is unavailable
  }
}

function expiresAt(token: string): number | null {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const expiry = JSON.parse(atob(payload)).exp;
    return typeof expiry === "number" && Number.isFinite(expiry)
      ? expiry * 1000
      : null;
  } catch {
    return null;
  }
}

const Context = createContext<{
  user: Profile | null;
  restoring: boolean;
  login: (email: string, password: string) => Promise<void>;
  loginWithSso: () => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
  picture: string | null;
  savePicture: (blob: Blob) => Promise<void>;
}>({
  user: null,
  restoring: false,
  login: async () => {},
  loginWithSso: async () => {},
  logout: () => {},
  refresh: async () => {},
  picture: null,
  savePicture: async () => {},
});
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null);
  const [restoring, setRestoring] = useState(() => Boolean(savedToken()));
  const expiryTimer = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );
  const picturePath = useRef<string | null>(null);
  const [picture, setPicture] = useState<string | null>(null);
  const pictureRef = useRef<string | null>(null);
  const activeUser = useRef<number | null>(null);
  const sessionGeneration = useRef(0);
  function clearPicture() {
    if (pictureRef.current) URL.revokeObjectURL(pictureRef.current);
    pictureRef.current = null;
    picturePath.current = null;
    setPicture(null);
  }
  async function savePicture(blob: Blob) {
    if (!user) throw new Error("Please sign in again.");
    const owner = user.id;
    const generation = sessionGeneration.current;
    const data = new FormData();
    data.append(
      "image",
      new File([blob], `profile-${owner}-${Date.now()}.jpg`, {
        type: "image/jpeg",
      }),
    );
    const profile = await api<Profile>(
      "/auth/users/profile-picture",
      "PATCH",
      data,
    );
    if (
      activeUser.current !== owner ||
      generation !== sessionGeneration.current
    )
      throw new Error("Your session changed. Please sign in again.");
    clearPicture();
    const url = URL.createObjectURL(blob);
    pictureRef.current = url;
    picturePath.current = profile.profilePicturePath;
    setPicture(url);
    setUser(profile);
  }
  function logout() {
    sessionGeneration.current++;
    activeUser.current = null;
    clearPicture();
    clearTimeout(expiryTimer.current);
    setToken(null);
    storeToken(null);
    setUser(null);
    setRestoring(false);
  }
  function retainSession(token: string) {
    storeToken(token);
    clearTimeout(expiryTimer.current);
    const expiry = expiresAt(token);
    if (expiry !== null) {
      expiryTimer.current = setTimeout(
        logout,
        Math.max(0, expiry - Date.now()),
      );
    }
  }
  async function refresh(signal?: AbortSignal) {
    const generation = sessionGeneration.current;
    const profile = await api<Profile>(
      "/auth/users/profile",
      "GET",
      undefined,
      signal,
    );
    if (signal?.aborted || generation !== sessionGeneration.current)
      throw new Error("Your session changed. Please sign in again.");
    activeUser.current = profile.id;
    setUser(profile);
  }
  async function authenticate(token: string, generation: number) {
    if (generation !== sessionGeneration.current) return;
    setToken(token);
    try {
      await refresh();
      if (generation === sessionGeneration.current) retainSession(token);
    } catch (error) {
      if (generation === sessionGeneration.current) logout();
      throw error;
    }
  }
  async function login(email: string, password: string) {
    const generation = ++sessionGeneration.current;
    const res = await api<{ message: string }>("/auth/users/login", "POST", {
      email,
      password,
    });
    await authenticate(res.message, generation);
  }
  async function loginWithSso() {
    const generation = ++sessionGeneration.current;
    const res = await api<{ message: string }>(
      "/auth/sso/exchange",
      "POST",
      {},
    );
    await authenticate(res.message, generation);
  }
  useEffect(() => {
    window.addEventListener("session-expired", logout);
    return () => window.removeEventListener("session-expired", logout);
  }, []);
  useEffect(() => {
    const token = savedToken();
    if (!token) return;
    const expiry = expiresAt(token);
    if (expiry !== null && expiry <= Date.now()) {
      logout();
      return;
    }

    const controller = new AbortController();
    const generation = sessionGeneration.current;
    setToken(token);
    void refresh(controller.signal)
      .then(() => {
        if (
          !controller.signal.aborted &&
          generation === sessionGeneration.current
        )
          retainSession(token);
      })
      .catch(() => {
        if (
          !controller.signal.aborted &&
          generation === sessionGeneration.current
        )
          logout();
      })
      .finally(() => {
        if (
          !controller.signal.aborted &&
          generation === sessionGeneration.current
        )
          setRestoring(false);
      });
    return () => {
      controller.abort();
      clearTimeout(expiryTimer.current);
    };
  }, []);

  useEffect(() => {
    const owner = user?.id;
    const path = user?.profilePicturePath;
    if (!owner || !path || picturePath.current === path) return;
    const controller = new AbortController();
    const generation = sessionGeneration.current;
    void apiBlob("/auth/users/profile-picture", controller.signal)
      .then((blob) => {
        if (
          controller.signal.aborted ||
          generation !== sessionGeneration.current ||
          activeUser.current !== owner ||
          !["image/jpeg", "image/png"].includes(blob.type)
        )
          return;
        clearPicture();
        const url = URL.createObjectURL(blob);
        pictureRef.current = url;
        picturePath.current = path;
        setPicture(url);
      })
      .catch(() => {
        // a missing picture leaves the account initials in place
      });
    return () => controller.abort();
  }, [user?.id, user?.profilePicturePath]);

  return (
    <Context.Provider
      value={{
        user,
        restoring,
        login,
        loginWithSso,
        logout,
        refresh,
        picture,
        savePicture,
      }}
    >
      {children}
    </Context.Provider>
  );
}
export const useAuth = () => useContext(Context);
