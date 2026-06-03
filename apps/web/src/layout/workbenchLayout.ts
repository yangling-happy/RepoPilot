export const DOC_VIEW_SIDEBAR = [
  "shrink-0 w-64 overflow-y-auto",
  "border-r border-neutral-200 bg-neutral-50",
  "dark:border-white/10 dark:bg-neutral-900",
].join(" ");

/** DocView split pane: full bleed, no shell padding. */
export const DOC_VIEW_SHELL = "w-full flex-1 p-0";

export const DOC_VIEW_SUBNAV_SHELL = [
  "sticky top-14 z-40 md:top-16",
  "border-b border-neutral-200/80 bg-white/80 px-4 py-3 backdrop-blur-sm md:px-6",
  "dark:border-white/10 dark:bg-black/60",
].join(" ");

export const DOC_VIEW_SUBNAV_INNER = "flex w-full items-center gap-4";

export const DOC_VIEW_CONTENT_PADDING = "p-6 md:p-8";

/** Documentation / deploy / dashboard: centered column with shell padding. */
export const WORKBENCH_PAGE_SHELL =
  "mx-auto w-full max-w-6xl flex-1 px-6 py-10 md:px-10";

export const WORKBENCH_SUBNAV_SHELL = [
  "border-b border-neutral-200/80 bg-white/80 px-6 py-3 backdrop-blur-sm md:px-8",
  "dark:border-white/10 dark:bg-black/60",
].join(" ");

export const WORKBENCH_SUBNAV_INNER =
  "mx-auto flex w-full max-w-6xl items-center gap-4";

export const WORKBENCH_FORM_PAGE_INNER =
  "mx-auto max-w-[1200px] pb-20 pt-2 text-neutral-950 dark:text-neutral-50";
