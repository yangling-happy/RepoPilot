import { useTheme } from "next-themes";
import { useEffect, useRef } from "react";
import { createVirtualTerminal } from "@repo-pilot/terminal-client";

const shellClass =
  "relative rounded-[2rem] border border-neutral-200/60 bg-white/50 shadow-sm backdrop-blur-md dark:border-white/5 dark:bg-white/[0.02] md:rounded-[2.5rem]";

type Props = {
  title: string;
  subtitle?: string;
  /** Lines shown after mount (e.g. boot banner) */
  bootLines: readonly string[];
  className?: string;
};

export function VirtualTerminalPanel({
  title,
  subtitle,
  bootLines,
  className = "",
}: Props) {
  const { resolvedTheme } = useTheme();
  const hostRef = useRef<HTMLDivElement>(null);

  const bootKey = bootLines.join("\u0000");

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    const colorScheme = resolvedTheme === "dark" ? "dark" : "light";
    const handle = createVirtualTerminal({
      colorScheme,
      initialLines: [...bootLines],
    });
    handle.open(host);

    const ro = new ResizeObserver(() => {
      handle.fit();
    });
    ro.observe(host);

    return () => {
      ro.disconnect();
      handle.dispose();
    };
  }, [resolvedTheme, bootKey]);

  return (
    <section className={`${shellClass} ${className}`}>
      <div className="border-b border-neutral-200/80 px-6 py-5 dark:border-white/10 md:px-10 md:py-6">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-neutral-400">
          {title}
        </p>
        {subtitle ? (
          <p className="mt-2 text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
            {subtitle}
          </p>
        ) : null}
      </div>
      {/* 内边距 + min-w-0：避免 flex 子项裁切 xterm；不用 overflow-hidden，以免挡住滚动条与最后一行 */}
      <div className="box-border min-w-0 px-4 pb-5 pt-3 md:px-6 md:pb-6">
        <div
          ref={hostRef}
          className="box-border h-[min(420px,55vh)] min-h-[280px] w-full min-w-0 rounded-xl bg-neutral-50/90 p-3 ring-1 ring-inset ring-neutral-200/70 dark:bg-black/50 dark:ring-white/10"
        />
      </div>
    </section>
  );
}
