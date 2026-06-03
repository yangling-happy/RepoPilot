import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type {
  DocMemberDoc,
  DocQueryItem,
  DocTypeDoc,
} from "../../services/backendApi";

type TypeDocViewProps = { key?: string; typeDoc: DocTypeDoc };
type MemberDocViewProps = { key?: string; member: DocMemberDoc };
type MemberGroupProps = { key?: string; title: string; members: DocMemberDoc[] };

export const STRUCTURED_DOC_SECTIONS = [
  "types",
  "fields",
  "constructors",
  "methods",
] as const;

export type StructuredDocSection = (typeof STRUCTURED_DOC_SECTIONS)[number];

export const STRUCTURED_DOC_SECTION_LABEL_KEYS: Record<
  StructuredDocSection,
  string
> = {
  types: "pages.documentation.structured.types",
  fields: "pages.documentation.structured.fields",
  constructors: "pages.documentation.structured.constructors",
  methods: "pages.documentation.structured.methods",
};

export function getDocKey(doc: DocQueryItem) {
  return `${doc.filePath}::${doc.commitId}`;
}

export function getDefaultDocSection(doc: DocQueryItem): StructuredDocSection {
  const firstSectionWithContent = STRUCTURED_DOC_SECTIONS.find(
    (section) => getSectionCount(doc, section) > 0,
  );
  return firstSectionWithContent ?? "types";
}

export function getSectionCount(
  doc: DocQueryItem,
  section: StructuredDocSection,
): number {
  if (!doc.structuredDoc) {
    return 0;
  }
  if (section === "types") {
    return doc.structuredDoc.types.length;
  }
  return doc.structuredDoc.types.reduce(
    (total, typeDoc) => total + getSectionMembers(typeDoc, section).length,
    0,
  );
}

export function getSectionMembers(
  typeDoc: DocTypeDoc,
  section: Exclude<StructuredDocSection, "types">,
): DocMemberDoc[] {
  switch (section) {
    case "fields":
      return typeDoc.fields;
    case "constructors":
      return typeDoc.constructors;
    case "methods":
      return typeDoc.methods;
  }
}

export function StructuredDocDetail({
  doc,
  section,
  hideHeader = false,
}: {
  doc: DocQueryItem | null;
  section: StructuredDocSection;
  hideHeader?: boolean;
}) {
  const { t } = useTranslation();

  if (!doc) {
    return (
      <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
        <div className="flex min-h-[360px] items-center justify-center rounded-xl border border-dashed border-neutral-300 text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
          {t("pages.documentation.structured.empty")}
        </div>
      </section>
    );
  }

  if (!doc.structuredDoc) {
    return (
      <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
        <div className="font-mono text-xs text-neutral-500 dark:text-neutral-400">
          {doc.filePath}
        </div>
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-400/20 dark:bg-amber-400/10 dark:text-amber-200">
          {doc.parseErrorMsg ||
            t("pages.documentation.structured.parseMissing")}
        </div>
      </section>
    );
  }

  const sectionLabel = t(STRUCTURED_DOC_SECTION_LABEL_KEYS[section]);
  const sectionCount = getSectionCount(doc, section);

  const content = (
    <div className={hideHeader ? "space-y-5" : "mt-5 space-y-5"}>
      {section === "types" ? (
        doc.structuredDoc.types.length > 0 ? (
          doc.structuredDoc.types.map((typeDoc) => (
            <TypeDocView
              key={`${typeDoc.htmlFile}-${typeDoc.name}`}
              typeDoc={typeDoc}
            />
          ))
        ) : (
          <EmptyStructuredSection />
        )
      ) : (
        <StructuredMemberSection doc={doc.structuredDoc} section={section} />
      )}
    </div>
  );

  if (hideHeader) {
    return content;
  }

  return (
    <section className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-white/[0.03]">
      <div className="flex flex-col gap-3 border-b border-neutral-200 pb-4 dark:border-white/10 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <div className="truncate font-mono text-xs text-neutral-500 dark:text-neutral-400">
            {doc.structuredDoc.sourceFilePath}
          </div>
          <h2 className="mt-2 text-xl font-semibold tracking-tight text-neutral-950 dark:text-neutral-50">
            {doc.filePath}
          </h2>
        </div>
        <div className="shrink-0 rounded-xl border border-neutral-200 px-3 py-2 text-right dark:border-white/10">
          <div className="text-2xl font-semibold text-neutral-950 dark:text-neutral-50">
            {sectionCount}
          </div>
          <div className="text-xs text-neutral-500 dark:text-neutral-400">
            {sectionLabel}
          </div>
        </div>
      </div>

      {content}
    </section>
  );
}

function TypeDocView({ typeDoc }: TypeDocViewProps) {
  const { t } = useTranslation();

  return (
    <article className="rounded-xl border border-neutral-200 bg-neutral-50/70 p-4 dark:border-white/10 dark:bg-black/20">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-neutral-900 px-2 py-0.5 text-[11px] font-semibold text-white dark:bg-white dark:text-black">
              {typeDoc.kind}
            </span>
            <h3 className="text-lg font-semibold text-neutral-950 dark:text-neutral-50">
              {typeDoc.name}
            </h3>
          </div>
          <div className="mt-1 break-all font-mono text-xs text-neutral-500 dark:text-neutral-400">
            {typeDoc.qualifiedName}
          </div>
        </div>
      </div>

      <p className="mt-4 text-sm leading-6 text-neutral-700 dark:text-neutral-300">
        {typeDoc.description ||
          t("pages.documentation.structured.noDescription")}
      </p>

      {typeDoc.signature ? (
        <pre className="mt-4 overflow-x-auto rounded-lg bg-neutral-950 px-3 py-2 font-mono text-xs text-neutral-50 dark:bg-black">
          {typeDoc.signature}
        </pre>
      ) : null}
    </article>
  );
}

function StructuredMemberSection({
  doc,
  section,
}: {
  doc: NonNullable<DocQueryItem["structuredDoc"]>;
  section: Exclude<StructuredDocSection, "types">;
}) {
  const { t } = useTranslation();
  const groups = doc.types
    .map((typeDoc) => ({
      typeDoc,
      members: getSectionMembers(typeDoc, section),
    }))
    .filter((group) => group.members.length > 0);

  if (groups.length === 0) {
    return <EmptyStructuredSection />;
  }

  return (
    <>
      {groups.map(({ typeDoc, members }) => (
        <MemberGroup
          key={`${typeDoc.htmlFile}-${typeDoc.name}-${section}`}
          title={`${typeDoc.name} - ${t(STRUCTURED_DOC_SECTION_LABEL_KEYS[section])}`}
          members={members}
        />
      ))}
    </>
  );
}

function EmptyStructuredSection() {
  const { t } = useTranslation();
  return (
    <div className="rounded-xl border border-dashed border-neutral-300 px-4 py-10 text-center text-sm text-neutral-500 dark:border-white/15 dark:text-neutral-400">
      {t("pages.documentation.structured.empty")}
    </div>
  );
}

export function MemberGroup({
  title,
  members,
}: MemberGroupProps) {
  if (members.length === 0) {
    return null;
  }

  return (
    <section>
      <h4 className="text-xs font-semibold uppercase tracking-normal text-neutral-500 dark:text-neutral-400">
        {title}
      </h4>
      <div className="mt-2 space-y-2">
        {members.map((member) => (
          <MemberDocView
            key={`${member.kind}-${member.id || member.name}`}
            member={member}
          />
        ))}
      </div>
    </section>
  );
}

export function MemberDocView({ member }: MemberDocViewProps) {
  const { t } = useTranslation();
  const throwsItems = member.throws ?? [];

  return (
    <div className="rounded-lg border border-neutral-200 bg-white p-3 dark:border-white/10 dark:bg-white/[0.03]">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-sm font-semibold text-neutral-950 dark:text-neutral-50">
          {member.name}
        </span>
        <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] text-neutral-500 dark:bg-white/10 dark:text-neutral-300">
          {member.kind}
        </span>
      </div>

      {member.signature ? (
        <pre className="mt-3 overflow-x-auto rounded-md bg-neutral-950 px-3 py-2 font-mono text-xs text-neutral-50 dark:bg-black">
          {member.signature}
        </pre>
      ) : null}

      <p className="mt-3 text-sm leading-6 text-neutral-700 dark:text-neutral-300">
        {member.description ||
          t("pages.documentation.structured.noDescription")}
      </p>

      {member.parameters.length > 0 ? (
        <DocMetaBlock title={t("pages.documentation.structured.parameters")}>
          {member.parameters.map((parameter) => (
            <div
              key={parameter.name}
              className="grid gap-1 border-t border-neutral-100 py-2 first:border-t-0 dark:border-white/10 md:grid-cols-[160px_minmax(0,1fr)]"
            >
              <div className="font-mono text-xs text-neutral-900 dark:text-neutral-100">
                {parameter.type ? `${parameter.type} ` : ""}
                {parameter.name}
              </div>
              <div className="text-sm text-neutral-600 dark:text-neutral-300">
                {parameter.description}
              </div>
            </div>
          ))}
        </DocMetaBlock>
      ) : null}

      {member.returns ? (
        <DocMetaBlock title={t("pages.documentation.structured.returns")}>
          <div className="grid gap-1 py-2 md:grid-cols-[160px_minmax(0,1fr)]">
            <div className="font-mono text-xs text-neutral-900 dark:text-neutral-100">
              {member.returns.type}
            </div>
            <div className="text-sm text-neutral-600 dark:text-neutral-300">
              {member.returns.description}
            </div>
          </div>
        </DocMetaBlock>
      ) : null}

      {throwsItems.length > 0 ? (
        <DocMetaBlock title={t("pages.documentation.structured.throws")}>
          {throwsItems.map((throwsItem) => (
            <div
              key={`${throwsItem.type}-${throwsItem.description}`}
              className="grid gap-1 border-t border-neutral-100 py-2 first:border-t-0 dark:border-white/10 md:grid-cols-[160px_minmax(0,1fr)]"
            >
              <div className="font-mono text-xs text-neutral-900 dark:text-neutral-100">
                {throwsItem.type}
              </div>
              <div className="text-sm text-neutral-600 dark:text-neutral-300">
                {throwsItem.description}
              </div>
            </div>
          ))}
        </DocMetaBlock>
      ) : null}
    </div>
  );
}

function DocMetaBlock({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="mt-3 rounded-md border border-neutral-200 bg-neutral-50 px-3 py-2 dark:border-white/10 dark:bg-black/20">
      <div className="text-[11px] font-semibold uppercase tracking-normal text-neutral-500 dark:text-neutral-400">
        {title}
      </div>
      <div className="mt-1">{children}</div>
    </div>
  );
}
