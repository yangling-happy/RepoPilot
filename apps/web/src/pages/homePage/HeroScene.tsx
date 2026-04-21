type Props = {
  className?: string;
};

export function HeroScene({ className }: Props) {
  return (
    <div
      className={[
        "relative isolate h-[min(46vh,380px)] w-full overflow-hidden rounded-2xl border border-neutral-200/80 bg-neutral-950/[0.02] dark:border-white/10 dark:bg-white/[0.03]",
        className ?? "",
      ].join(" ")}
    >
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(120%_80%_at_50%_0%,rgba(0,0,0,0.06)_0%,transparent_55%)] dark:bg-[radial-gradient(120%_80%_at_50%_0%,rgba(255,255,255,0.10)_0%,transparent_55%)]" />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_50%_at_80%_60%,rgba(0,0,0,0.05),transparent_60%)] dark:bg-[radial-gradient(60%_50%_at_80%_60%,rgba(255,255,255,0.06),transparent_60%)]" />

      <div className="absolute inset-0 flex items-center justify-center [perspective:1200px]">
        <div
          className="relative h-[260px] w-[min(92vw,760px)] [transform-style:preserve-3d] animate-hero-tilt"
          style={{ transformOrigin: "50% 40%" }}
        >
          {/* Back plane */}
          <div
            className="absolute left-1/2 top-1/2 h-[220px] w-[min(90vw,700px)] -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-black/[0.06] bg-white/30 shadow-[0_28px_110px_-52px_rgba(0,0,0,0.38)] dark:border-white/[0.08] dark:bg-white/[0.03] dark:shadow-[0_28px_110px_-52px_rgba(255,255,255,0.09)]"
            style={{
              transform: "translateZ(-48px)",
              opacity: 0.45,
            }}
          />
          {/* Front panel + grid */}
          <div
            className="absolute left-1/2 top-1/2 h-[236px] w-[min(92vw,720px)] -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-black/10 bg-gradient-to-br from-neutral-100/90 via-white/50 to-transparent shadow-[0_45px_140px_-55px_rgba(0,0,0,0.45)] dark:border-white/15 dark:from-white/[0.07] dark:via-white/[0.03] dark:to-transparent dark:shadow-[0_45px_140px_-55px_rgba(255,255,255,0.14)]"
            style={{
              transform: "translateZ(26px)",
              transformStyle: "preserve-3d",
            }}
          >
            <div
              className="absolute inset-[1px] rounded-[15px] opacity-75 dark:opacity-60"
              style={{
                backgroundImage: `
                  linear-gradient(to right, rgba(120,120,120,0.35) 1px, transparent 1px),
                  linear-gradient(to bottom, rgba(120,120,120,0.35) 1px, transparent 1px)
                `,
                backgroundSize: "26px 26px",
              }}
            />
            <div className="pointer-events-none absolute inset-0 animate-hero-glow rounded-[15px] bg-[linear-gradient(110deg,transparent_35%,rgba(255,255,255,0.65)_50%,transparent_65%)] opacity-35 dark:bg-[linear-gradient(110deg,transparent_35%,rgba(255,255,255,0.22)_50%,transparent_65%)]" />

            <div
              className="absolute left-[10%] top-[18%] h-16 w-[38%] rounded-xl border border-black/10 bg-white/80 shadow-sm dark:border-white/15 dark:bg-black/50"
              style={{
                transform: "translateZ(28px)",
                transformStyle: "preserve-3d",
              }}
            />
            <div
              className="absolute bottom-[16%] right-[12%] h-14 w-[44%] rounded-xl border border-black/10 bg-white/72 shadow-sm dark:border-white/15 dark:bg-black/42"
              style={{
                transform: "translateZ(42px)",
                transformStyle: "preserve-3d",
              }}
            />
          </div>
        </div>
      </div>

      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-white to-transparent dark:from-black" />
    </div>
  );
}
