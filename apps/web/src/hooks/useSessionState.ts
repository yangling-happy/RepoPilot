import { useCallback, useState } from "react";

/**
 * Like useState, but persists the value to sessionStorage so it survives
 * page navigation within the same browser tab.
 *
 * @param storageKey unique key for this piece of state (e.g. "deploy.branch")
 * @param defaultValue fallback when sessionStorage has no stored value
 */
export function useSessionState<T>(
  storageKey: string,
  defaultValue: T | (() => T),
): [T, (value: T | ((prev: T) => T)) => void] {
  const fullKey = `repopilot.form.${storageKey}`;

  const [state, setState] = useState<T>(() => {
    if (typeof window === "undefined") {
      return typeof defaultValue === "function"
        ? (defaultValue as () => T)()
        : defaultValue;
    }
    const stored = window.sessionStorage.getItem(fullKey);
    if (stored !== null) {
      try {
        return JSON.parse(stored) as T;
      } catch {
        // corrupted value, fall through to default
      }
    }
    return typeof defaultValue === "function"
      ? (defaultValue as () => T)()
      : defaultValue;
  });

  const set = useCallback(
    (value: T | ((prev: T) => T)) => {
      setState((prev) => {
        const next = typeof value === "function" ? (value as (prev: T) => T)(prev) : value;
        try {
          window.sessionStorage.setItem(fullKey, JSON.stringify(next));
        } catch {
          // sessionStorage full or unavailable — silently ignore
        }
        return next;
      });
    },
    [fullKey],
  );

  return [state, set];
}
