export function createSessionId(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function getOrCreateTerminalSessionId(storageKey: string): string {
  if (typeof window === "undefined") {
    return createSessionId();
  }

  const existing = window.localStorage.getItem(storageKey);
  if (existing) {
    return existing;
  }

  const created = createSessionId();
  window.localStorage.setItem(storageKey, created);
  return created;
}
