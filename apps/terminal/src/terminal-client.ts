import { createVirtualTerminal, type CreateVirtualTerminalOptions } from "./virtual-terminal";
import type { VirtualTerminalHandle } from "./virtual-terminal";

export class TerminalClient {
  private handle: VirtualTerminalHandle;
  private ws: WebSocket;

  constructor(ws: WebSocket, options?: CreateVirtualTerminalOptions) {
    this.ws = ws;
    this.handle = createVirtualTerminal(options);
    this.setupWebSocket();
    this.setupTerminal();
  }

  private setupWebSocket() {
    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data as string);
      if (message.type === "stdout") {
        this.handle.terminal.write(message.data as string);
      }
    };
  }

  private setupTerminal() {
    this.handle.terminal.onData((data) => {
      this.ws.send(
        JSON.stringify({
          type: "stdin",
          data,
        }),
      );
    });

    this.handle.terminal.onResize((size) => {
      this.ws.send(
        JSON.stringify({
          type: "resize",
          data: [size.cols, size.rows],
        }),
      );
    });
  }

  attach(container: HTMLElement) {
    this.handle.open(container);
  }

  detach() {
    this.handle.dispose();
  }

  onMessage(callback: (message: unknown) => void) {
    this.ws.onmessage = (event) => {
      callback(JSON.parse(event.data as string));
    };
  }
}
