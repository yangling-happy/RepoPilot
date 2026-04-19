import { Terminal, type ITheme } from "xterm";
import { FitAddon } from "xterm-addon-fit";
import { WebLinksAddon } from "xterm-addon-web-links";
import "xterm/css/xterm.css";

export type VirtualTerminalColorScheme = "light" | "dark";

export interface CreateVirtualTerminalOptions {
  colorScheme?: VirtualTerminalColorScheme;
  /** Printed with writeln after the terminal is mounted */
  initialLines?: string[];
  cursorBlink?: boolean;
  fontSize?: number;
}

const lightTheme: ITheme = {
  background: "#fafafa",
  foreground: "#171717",
  cursor: "#171717",
  cursorAccent: "#fafafa",
  selectionBackground: "rgba(23, 23, 23, 0.12)",
};

const darkTheme: ITheme = {
  background: "#0a0a0a",
  foreground: "#fafafa",
  cursor: "#fafafa",
  cursorAccent: "#0a0a0a",
  selectionBackground: "rgba(250, 250, 250, 0.1)",
};

export interface VirtualTerminalHandle {
  readonly terminal: Terminal;
  open(container: HTMLElement): void;
  fit(): void;
  dispose(): void;
}

export function createVirtualTerminal(
  options: CreateVirtualTerminalOptions = {},
): VirtualTerminalHandle {
  const scheme = options.colorScheme ?? "dark";
  const theme = scheme === "light" ? lightTheme : darkTheme;

  const terminal = new Terminal({
    theme,
    fontFamily:
      "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace",
    fontSize: options.fontSize ?? 13,
    lineHeight: 1.35,
    cursorBlink: options.cursorBlink ?? true,
    scrollback: 5000,
    convertEol: true,
  });

  const fitAddon = new FitAddon();
  terminal.loadAddon(fitAddon);
  terminal.loadAddon(new WebLinksAddon());

  const initialLines = options.initialLines ?? [];

  return {
    terminal,
    open(container: HTMLElement) {
      terminal.open(container);
      fitAddon.fit();
      for (const line of initialLines) {
        terminal.writeln(line);
      }
    },
    fit() {
      fitAddon.fit();
    },
    dispose() {
      terminal.dispose();
    },
  };
}
