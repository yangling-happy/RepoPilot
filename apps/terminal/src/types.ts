export interface TerminalSession {
  id: string;
  createdAt: Date;
  status: "active" | "closed";
}

export interface WebSocketMessage {
  type: "stdout" | "exit" | "error" | "resize";
  data?: string | number[];
  sessionId?: string;
  exitCode?: number;
  message?: string;
}
