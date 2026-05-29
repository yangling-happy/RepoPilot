/** Global content shell: full width, no top/side padding (pages own their spacing). */
export const WORKBENCH_CONTENT_SHELL = "w-full flex-1 px-0 pt-0 pb-10";

/** DocView split layout: no outer padding at all. */
export const DOC_VIEW_SHELL = "w-full flex-1 p-0";

export const WORKBENCH_SUBNAV_GUTTER_X = "px-4 md:px-6";

export const WORKBENCH_SUBNAV_SHELL = [
  "border-b border-neutral-200/80 bg-white/80 py-3 backdrop-blur-sm",
  WORKBENCH_SUBNAV_GUTTER_X,
  "dark:border-white/10 dark:bg-black/60",
].join(" ");

export const WORKBENCH_SUBNAV_INNER = "flex w-full items-center gap-4";

export const DOC_VIEW_CONTENT_PADDING = "p-6 md:p-8";
