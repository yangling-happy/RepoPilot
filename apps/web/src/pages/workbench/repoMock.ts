export type WorkbenchRepo = {
  id: string;
  name: string;
  visibility: "private" | "internal";
  stack: string;
  descriptionKey: string;
  branch: string;
  owner: string;
  lastUpdatedAt: string;
  lastDeployAt: string;
  docsEndpoint: string;
};

export const WORKBENCH_REPOS: WorkbenchRepo[] = [
  {
    id: "acme-platform-api",
    name: "acme/platform-api",
    visibility: "private",
    stack: "Java 21 · Spring Boot",
    descriptionKey: "acme-platform-api",
    branch: "main",
    owner: "platform-team",
    lastUpdatedAt: "2026-04-18 09:20",
    lastDeployAt: "2026-04-17 14:22",
    docsEndpoint: "https://docs.acme.internal/platform-api",
  },
  {
    id: "billing-svc",
    name: "acme/billing-service",
    visibility: "internal",
    stack: "Java 17 · MySQL",
    descriptionKey: "billing-svc",
    branch: "release/2026.04",
    owner: "billing-team",
    lastUpdatedAt: "2026-04-18 08:10",
    lastDeployAt: "2026-04-16 21:03",
    docsEndpoint: "https://docs.acme.internal/billing",
  },
  {
    id: "docs-hub",
    name: "acme/docs-hub",
    visibility: "private",
    stack: "MkDocs · Node.js",
    descriptionKey: "docs-hub",
    branch: "main",
    owner: "developer-experience",
    lastUpdatedAt: "2026-04-17 20:36",
    lastDeployAt: "2026-04-17 18:04",
    docsEndpoint: "https://docs.acme.internal/hub",
  },
];
