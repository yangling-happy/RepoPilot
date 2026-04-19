export interface TerminalSession {
  id: string;
  createdAt: Date;
  status: "active" | "closed";
}

export interface WebSocketMessage {
  type: "stdin" | "stdout" | "resize";
  data: string | number[];
  sessionId?: string;
}
