import type { CloneRepoResponse } from "../../services/backendApi";
import type { WorkbenchRepo } from "./repoMock";

function storageKey(username: string | undefined, suffix: string): string {
  return username
    ? `repopilot.${username}.${suffix}`
    : `repopilot.guest.${suffix}`;
}

function readRepos(key: string): WorkbenchRepo[] {
  if (typeof window === "undefined") {
    return [];
  }

  const raw = window.localStorage.getItem(key);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as WorkbenchRepo[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function loadClonedRepos(username?: string): WorkbenchRepo[] {
  return readRepos(storageKey(username, "clonedRepos"));
}

export function saveClonedRepo(
  response: CloneRepoResponse,
  username?: string,
): WorkbenchRepo {
  const repo: WorkbenchRepo = {
    id: String(response.projectId),
    name: response.projectPath || `project-${response.projectId}`,
    visibility: "private",
    stack: "-",
    description: response.localPath,
    branch: response.branch,
    owner: "gitlab",
    lastUpdatedAt: new Date().toLocaleString(),
    lastDeployAt: "-",
    docsEndpoint: "-",
  };

  const key = storageKey(username, "clonedRepos");
  const existing = readRepos(key);
  const deduped = [repo, ...existing.filter((item) => item.id !== repo.id)];
  window.localStorage.setItem(key, JSON.stringify(deduped));
  return repo;
}

export function loadRemoteRepos(username?: string): WorkbenchRepo[] {
  return readRepos(storageKey(username, "remoteRepos"));
}

export function saveRemoteRepos(
  repos: WorkbenchRepo[],
  username?: string,
): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(
    storageKey(username, "remoteRepos"),
    JSON.stringify(repos),
  );
}

/**
 * 清除指定用户的所有 localStorage 数据（登出时调用）
 */
export function clearUserData(username?: string): void {
  if (typeof window === "undefined" || !username) {
    return;
  }
  const prefix = `repopilot.${username}.`;
  const keysToRemove: string[] = [];
  for (let i = 0; i < window.localStorage.length; i++) {
    const key = window.localStorage.key(i);
    if (key && key.startsWith(prefix)) {
      keysToRemove.push(key);
    }
  }
  keysToRemove.forEach((key) => window.localStorage.removeItem(key));
}
