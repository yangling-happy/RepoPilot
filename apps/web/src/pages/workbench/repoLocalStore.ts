import type { CloneRepoResponse } from "../../services/backendApi";
import type { WorkbenchRepo } from "./repoMock";

const STORAGE_KEY = "repopilot.clonedRepos";

export function loadClonedRepos(): WorkbenchRepo[] {
  if (typeof window === "undefined") {
    return [];
  }

  const raw = window.localStorage.getItem(STORAGE_KEY);
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

export function saveClonedRepo(response: CloneRepoResponse): WorkbenchRepo {
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

  const existing = loadClonedRepos();
  const deduped = [repo, ...existing.filter((item) => item.id !== repo.id)];
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(deduped));
  return repo;
}
