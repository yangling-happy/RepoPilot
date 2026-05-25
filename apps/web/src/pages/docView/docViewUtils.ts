import type { DocQueryItem } from "../../services/backendApi";
import { getDocKey } from "./DocViewComponents";

export type PackageGroup = {
  name: string;
  packagePath: string;
  docs: DocQueryItem[];
};

const SOURCE_PREFIX_RE = /^src\/main\/(?:java|kotlin)\//;

export function extractPackageSegments(filePath: string): {
  segments: string[];
  className: string;
} {
  const withoutPrefix = filePath.replace(SOURCE_PREFIX_RE, "");
  const parts = withoutPrefix.split("/");
  const fileName = parts.pop() ?? withoutPrefix;
  const className = fileName.replace(/\.\w+$/, "");
  return { segments: parts, className };
}

export function getClassName(doc: DocQueryItem): string {
  if (doc.structuredDoc?.types?.length) {
    return doc.structuredDoc.types[0].name;
  }
  const { className } = extractPackageSegments(doc.filePath);
  return className;
}

export function getPackageGroup(doc: DocQueryItem): string {
  const { segments } = extractPackageSegments(doc.filePath);
  if (segments.length >= 2) {
    return segments.slice(-2).join(".");
  }
  return segments.join(".") || "(root)";
}

export function buildGroups(docs: DocQueryItem[]): PackageGroup[] {
  const groupMap = new Map<string, DocQueryItem[]>();

  for (const doc of docs) {
    const group = getPackageGroup(doc);
    if (!groupMap.has(group)) {
      groupMap.set(group, []);
    }
    groupMap.get(group)!.push(doc);
  }

  return Array.from(groupMap.entries())
    .map(([name, groupDocs]) => ({
      name,
      packagePath: name,
      docs: groupDocs,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

export function scrollToAnchor(anchorId: string) {
  const el = document.getElementById(anchorId);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

export function getAnchorId(doc: DocQueryItem): string {
  return `doc-${getDocKey(doc).replace(/[^a-zA-Z0-9]/g, "-")}`;
}

export function filterGroups(
  groups: PackageGroup[],
  searchQuery: string,
): PackageGroup[] {
  if (!searchQuery.trim()) return groups;
  const lower = searchQuery.toLowerCase();
  return groups
    .map((group) => ({
      ...group,
      docs: group.docs.filter(
        (doc) =>
          getClassName(doc).toLowerCase().includes(lower) ||
          group.name.toLowerCase().includes(lower) ||
          doc.filePath.toLowerCase().includes(lower),
      ),
    }))
    .filter((group) => group.docs.length > 0);
}
