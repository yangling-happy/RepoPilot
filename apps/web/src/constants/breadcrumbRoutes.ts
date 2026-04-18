export type BreadcrumbSegment = {
  translationKey: string
  path?: string
}

export const BREADCRUMB_BY_PATH: Record<string, BreadcrumbSegment[]> = {
  '/': [{ translationKey: 'breadcrumb.home', path: '/' }],
  '/documentation': [
    { translationKey: 'breadcrumb.home', path: '/' },
    { translationKey: 'breadcrumb.documentation', path: '/documentation' },
  ],
  '/deploy': [
    { translationKey: 'breadcrumb.home', path: '/' },
    { translationKey: 'breadcrumb.deploy', path: '/deploy' },
  ],
}
