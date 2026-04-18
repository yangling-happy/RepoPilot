export type DemoRepo = {
  id: string
  name: string
  visibility: 'private' | 'internal'
  stack: string
  /** i18n key under `home.repos.times.*` */
  timeKey: string
}

export const DEMO_REPOS: DemoRepo[] = [
  {
    id: 'acme-platform-api',
    name: 'acme/platform-api',
    visibility: 'private',
    stack: 'Java 21 · Spring Boot',
    timeKey: '2h',
  },
  {
    id: 'billing-svc',
    name: 'acme/billing-service',
    visibility: 'internal',
    stack: 'Java 17',
    timeKey: '5h',
  },
  {
    id: 'docs-hub',
    name: 'acme/docs-hub',
    visibility: 'private',
    stack: 'JavaDoc · MkDocs',
    timeKey: '1d',
  },
  {
    id: 'release-tools',
    name: 'platform/release-tools',
    visibility: 'internal',
    stack: 'CLI · Gradle',
    timeKey: '3d',
  },
]
