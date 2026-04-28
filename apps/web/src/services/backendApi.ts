type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export class ApiError extends Error {
  readonly httpStatus: number;
  readonly apiCode?: number;

  constructor(
    message: string,
    options: { httpStatus: number; apiCode?: number },
  ) {
    super(message);
    this.name = "ApiError";
    this.httpStatus = options.httpStatus;
    this.apiCode = options.apiCode;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

export type CloneRepoRequest = {
  projectId: number;
  branch?: string;
  terminalSessionId?: string;
};

export type CloneRepoResponse = {
  projectId: number;
  gitlabUsername: string;
  projectPath: string;
  branch: string;
  cloneUrl: string;
  workspacePath: string;
  localPath: string;
  commitId: string;
};

export type DocLocalScanResult = {
  gitlabUsername: string;
  project: string;
  branch: string;
  commitId: string;
  localRepoPath: string;
  scannedFileCount: number;
  generatedFileCount: number;
  skippedFileCount: number;
  failedFileCount: number;
  generatedFilePaths: string[];
  failedFilePaths: string[];
  message: string;
};

export type DocStructuredContent = {
  schemaVersion: string;
  project: string;
  branch: string;
  commitId: string;
  sourceFilePath: string;
  types: DocTypeDoc[];
};

export type DocTypeDoc = {
  htmlFile: string;
  kind: string;
  name: string;
  qualifiedName: string;
  signature: string;
  description: string;
  fields: DocMemberDoc[];
  constructors: DocMemberDoc[];
  methods: DocMemberDoc[];
};

export type DocMemberDoc = {
  id: string;
  kind: string;
  name: string;
  signature: string;
  description: string;
  parameters: DocParameterDoc[];
  returns?: DocReturnDoc | null;
  throws?: DocThrowsDoc[];
};

export type DocParameterDoc = {
  name: string;
  type: string;
  description: string;
};

export type DocReturnDoc = {
  type: string;
  description: string;
};

export type DocThrowsDoc = {
  type: string;
  description: string;
};

export type DocQueryItem = {
  gitlabUsername: string;
  project: string;
  branch: string;
  filePath: string;
  commitId: string;
  docFilePath: string;
  parseStatus: string;
  parseErrorMsg?: string | null;
  structuredDoc?: DocStructuredContent | null;
  updateTime?: string | null;
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

export async function queryDocs(payload: {
  project: string;
  branch?: string;
  filePath?: string;
  commitId?: string;
}): Promise<DocQueryItem[]> {
  const params = new URLSearchParams({ project: payload.project });
  if (payload.branch) {
    params.set("branch", payload.branch);
  }
  if (payload.filePath) {
    params.set("filePath", payload.filePath);
  }
  if (payload.commitId) {
    params.set("commitId", payload.commitId);
  }
  return requestApi<DocQueryItem[]>(`/api/doc/query?${params.toString()}`, {
    method: "GET",
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
    throw new ApiError(`HTTP ${response.status}`, {
      httpStatus: response.status,
    });
  }

  if (!response.ok || !json || json.code !== 200) {
    throw new ApiError(json?.message || `HTTP ${response.status}`, {
      httpStatus: response.status,
      apiCode: json?.code,
    });
  }

  return json.data;
}
