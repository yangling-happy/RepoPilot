type LocationLike = {
  protocol: string;
  host: string;
};

export function resolveTerminalWsUrl(
  sessionId: string,
  options: {
    configuredBaseUrl?: string;
    location: LocationLike;
  },
): string {
  const protocol = options.location.protocol === "https:" ? "wss:" : "ws:";
  const baseUrl = normalizeTerminalBaseUrl(
    options.configuredBaseUrl,
    protocol,
    options.location.host,
  );

  return `${baseUrl.replace(/\/+$/, "")}/${encodeURIComponent(sessionId)}`;
}

function normalizeTerminalBaseUrl(
  configuredBaseUrl: string | undefined,
  protocol: string,
  host: string,
): string {
  const trimmedBaseUrl = configuredBaseUrl?.trim();
  if (!trimmedBaseUrl) {
    return `${protocol}//${host}/ws/terminal`;
  }

  if (/^wss?:\/\//i.test(trimmedBaseUrl)) {
    return trimmedBaseUrl;
  }

  if (/^https?:\/\//i.test(trimmedBaseUrl)) {
    const url = new URL(trimmedBaseUrl);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    return url.toString();
  }

  if (trimmedBaseUrl.startsWith("//")) {
    return `${protocol}${trimmedBaseUrl}`;
  }

  if (trimmedBaseUrl.startsWith("/")) {
    return `${protocol}//${host}${trimmedBaseUrl}`;
  }

  return trimmedBaseUrl;
}
