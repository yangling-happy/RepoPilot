import { ApiError } from "./backendApi";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export type TerminalTaskType =
  | "CLONE_REPO"
  | "REFRESH_DOC"
  | "SCAN_LOCAL_DOC"
  | "BUILD_PROJECT"
  | "DEPLOY_PROJECT";

export type TerminalTaskStartResponse = {
  sessionId: string;
  taskType: TerminalTaskType;
  status: string;
};

export async function startTerminalTask(payload: {
  sessionId: string;
  taskType: TerminalTaskType;
  args: Record<string, string | number | undefined>;
}): Promise<TerminalTaskStartResponse> {
  return requestTerminalApi<TerminalTaskStartResponse>(
    "/api/terminal/tasks/start",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    },
  );
}

async function requestTerminalApi<T>(
  url: string,
  init: RequestInit,
): Promise<T> {
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
