export function getGithubUrl(): string {
  const url = import.meta.env.VITE_GITHUB_URL?.trim()
  if (url) {
    return url
  }
  return '#'
}
