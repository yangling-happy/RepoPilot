import type { CSSProperties, MouseEvent, ReactNode } from "react";
import { useState } from "react";

type Props = {
  children: ReactNode;
  className?: string;
};

export function SpotlightCard({ children, className = "" }: Props) {
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [opacity, setOpacity] = useState(0);

  const handleMouseMove = (event: MouseEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();

    setPosition({
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    });
  };

  const spotlightStyle = {
    opacity,
    "--spotlight-color": "rgba(0, 0, 0, 0.06)",
    "--spotlight-x": `${position.x}px`,
    "--spotlight-y": `${position.y}px`,
  } as CSSProperties & Record<string, string | number>;

  return (
    <div
      onMouseMove={handleMouseMove}
      onMouseEnter={() => setOpacity(1)}
      onMouseLeave={() => setOpacity(0)}
      className={`group relative overflow-hidden ${className}`}
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 rounded-[inherit] bg-[radial-gradient(600px_circle_at_var(--spotlight-x)_var(--spotlight-y),var(--spotlight-color),transparent_40%)] opacity-0 transition-opacity duration-300 ease-out"
        style={spotlightStyle}
      />
      <div className="relative z-10">{children}</div>
    </div>
  );
}
