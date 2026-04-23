type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export type CloneRepoRequest = {
  projectId: number;
  branch?: string;
  terminalSessionId?: string;
};

export type CloneRepoResponse = {
  projectId: number;
  projectPath: string;
  branch: string;
  cloneUrl: string;
  localPath: string;
  commitId: string;
};

export type DocLocalScanResult = {
  scannedFileCount: number;
  generatedFileCount: number;
  skippedFileCount: number;
};

export async function setGitlabToken(token: string): Promise<void> {
  const body = new URLSearchParams({ token });
  await requestApi<void>("/api/session/setGitlabToken", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
    },
    body: body.toString(),
  });
}

export async function cloneRepo(
  payload: CloneRepoRequest,
): Promise<CloneRepoResponse> {
  return requestApi<CloneRepoResponse>("/api/repo/clone", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

export async function scanLocalDoc(payload: {
  project: string;
  branch: string;
}): Promise<DocLocalScanResult> {
  return requestApi<DocLocalScanResult>("/api/doc/scan-local", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

async function requestApi<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    credentials: "include",
  });

  let json: ApiResponse<T> | null = null;
  try {
    json = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new Error(`HTTP ${response.status}`);
  }

  if (!response.ok || !json || json.code !== 200) {
    throw new Error(json?.message || `HTTP ${response.status}`);
  }

  return json.data;
}
