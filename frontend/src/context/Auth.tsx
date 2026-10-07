import {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  type ReactNode,
} from "react";
import { api, setToken } from "../api/client";
import type { Profile } from "../types";
const Context = createContext<{
  user: Profile | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
  picture: string | null;
  savePicture: (blob: Blob) => Promise<void>;
}>({
  user: null,
  login: async () => {},
  logout: () => {},
  refresh: async () => {},
  picture: null,
  savePicture: async () => {},
});
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null);
  const [picture, setPicture] = useState<string | null>(null);
  const pictureRef = useRef<string | null>(null);
  const activeUser = useRef<number | null>(null);
  function clearPicture() {
    if (pictureRef.current) URL.revokeObjectURL(pictureRef.current);
    pictureRef.current = null;
    setPicture(null);
  }
  async function savePicture(blob: Blob) {
    if (!user) throw new Error("Please sign in again.");
    const owner = user.id;
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
    if (activeUser.current !== owner)
      throw new Error("Your session changed. Please sign in again.");
    clearPicture();
    const url = URL.createObjectURL(blob);
    pictureRef.current = url;
    setPicture(url);
    setUser(profile);
  }
  function logout() {
    activeUser.current = null;
    clearPicture();
    setToken(null);
    setUser(null);
  }
  async function refresh() {
    const profile = await api<Profile>("/auth/users/profile");
    activeUser.current = profile.id;
    setUser(profile);
  }
  async function login(email: string, password: string) {
    const res = await api<{ message: string }>("/auth/users/login", "POST", {
      email,
      password,
    });
    setToken(res.message);
    try {
      await refresh();
    } catch (e) {
      logout();
      throw e;
    }
  }
  useEffect(() => {
    window.addEventListener("session-expired", logout);
    return () => window.removeEventListener("session-expired", logout);
  }, []);
  return (
    <Context.Provider
      value={{ user, login, logout, refresh, picture, savePicture }}
    >
      {children}
    </Context.Provider>
  );
}
export const useAuth = () => useContext(Context);
