// only the current tab retains its position in the authenticated workspace
export function readWorkspaceState<T>(key: string): T | null {
  try {
    const stored = sessionStorage.getItem("limitguard.workspace." + key);
    return stored ? JSON.parse(stored) : null;
  } catch {
    return null;
  }
}

export function saveWorkspaceState<T>(key: string, state: T) {
  try {
    sessionStorage.setItem(
      "limitguard.workspace." + key,
      JSON.stringify(state),
    );
  } catch {
    // navigation still works when browser storage is unavailable
  }
}
