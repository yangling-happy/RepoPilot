import {
  createVirtualTerminal,
  type CreateVirtualTerminalOptions,
} from "./virtual-terminal";
import type { VirtualTerminalHandle } from "./virtual-terminal";

export class TerminalClient {
  private handle: VirtualTerminalHandle;
  private ws: WebSocket;
  private pendingMessages: string[] = [];
  private disposed = false;

  private readonly handleOpen = () => {
    this.flushPendingMessages();
  };

  private readonly handleMessage = (event: MessageEvent) => {
    const message = JSON.parse(event.data as string);
    if (message.type === "stdout") {
      this.handle.terminal.write(message.data as string);
    }
  };

  private readonly handleClose = () => {
    this.pendingMessages = [];
  };

  constructor(ws: WebSocket, options?: CreateVirtualTerminalOptions) {
    this.ws = ws;
    this.handle = createVirtualTerminal(options);
    this.setupWebSocket();
    this.setupTerminal();
  }

  private setupWebSocket() {
    this.ws.addEventListener("open", this.handleOpen);
    this.ws.addEventListener("message", this.handleMessage);
    this.ws.addEventListener("close", this.handleClose);
  }

  private setupTerminal() {
    this.handle.terminal.onData((data: string) => {
      this.send({
        type: "stdin",
        data,
      });
    });

    this.handle.terminal.onResize((size: { cols: number; rows: number }) => {
      this.send({
        type: "resize",
        data: [size.cols, size.rows],
      });
    });
  }

  private send(payload: unknown) {
    if (this.disposed) {
      return;
    }
    const message = JSON.stringify(payload);
    if (this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(message);
      return;
    }

    if (this.ws.readyState === WebSocket.CONNECTING) {
      this.pendingMessages.push(message);
    }
  }

  private flushPendingMessages() {
    if (this.ws.readyState !== WebSocket.OPEN) {
      return;
    }

    for (const message of this.pendingMessages) {
      this.ws.send(message);
    }
    this.pendingMessages = [];
  }

  attach(container: HTMLElement) {
    this.handle.open(container);
  }

  fit() {
    if (this.disposed) {
      return;
    }
    this.handle.fit();
  }

  sendStdin(data: string) {
    this.send({
      type: "stdin",
      data,
    });
  }

  detach() {
    this.dispose();
  }

  dispose() {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    this.ws.removeEventListener("open", this.handleOpen);
    this.ws.removeEventListener("message", this.handleMessage);
    this.ws.removeEventListener("close", this.handleClose);
    this.pendingMessages = [];
    this.handle.dispose();
    if (
      this.ws.readyState === WebSocket.OPEN ||
      this.ws.readyState === WebSocket.CONNECTING
    ) {
      this.ws.close();
    }
  }

  onMessage(callback: (message: unknown) => void) {
    this.ws.addEventListener("message", (event) => {
      callback(JSON.parse(event.data as string));
    });
  }
}
