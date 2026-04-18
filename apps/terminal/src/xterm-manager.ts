// 类型声明
interface Terminal {
  write(data: string): void;
  open(container: HTMLElement): void;
  loadAddon(addon: any): void;
  onData(callback: (data: string) => void): void;
  onResize(callback: (size: { cols: number; rows: number }) => void): void;
  dispose(): void;
}

interface FitAddon {
  fit(): void;
  new (): FitAddon;
}

interface WebLinksAddon {
  new (): WebLinksAddon;
}

// 模拟 xterm 模块
const Terminal: any = function () {
  return {
    write: function (data: string) {
      console.log("Terminal.write:", data);
    },
    open: function (container: HTMLElement) {
      console.log("Terminal.open:", container);
    },
    loadAddon: function (addon: any) {
      console.log("Terminal.loadAddon:", addon);
    },
    onData: function (callback: (data: string) => void) {
      console.log("Terminal.onData:", callback);
    },
    onResize: function (
      callback: (size: { cols: number; rows: number }) => void,
    ) {
      console.log("Terminal.onResize:", callback);
    },
    dispose: function () {
      console.log("Terminal.dispose");
    },
  };
};

const FitAddon: any = function () {
  return {
    fit: function () {
      console.log("FitAddon.fit");
    },
  };
};

const WebLinksAddon: any = function () {
  return {};
};

export class TerminalClient {
  private terminal: Terminal;
  private ws: WebSocket;
  private fitAddon: FitAddon;

  constructor(ws: WebSocket) {
    this.ws = ws;
    this.fitAddon = new FitAddon();
    this.terminal = new Terminal({
      fontSize: 14,
      fontFamily: "Consolas, monospace",
      theme: {
        background: "#1E1E1E",
        foreground: "#D4D4D4",
      },
    });

    this.terminal.loadAddon(this.fitAddon);
    this.terminal.loadAddon(new WebLinksAddon());

    this.setupWebSocket();
    this.setupTerminal();
  }

  private setupWebSocket() {
    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.type === "stdout") {
        this.terminal.write(message.data);
      }
    };
  }

  private setupTerminal() {
    this.terminal.onData((data) => {
      this.ws.send(
        JSON.stringify({
          type: "stdin",
          data,
        }),
      );
    });

    this.terminal.onResize((size) => {
      this.ws.send(
        JSON.stringify({
          type: "resize",
          data: [size.cols, size.rows],
        }),
      );
    });
  }

  attach(container: HTMLElement) {
    this.terminal.open(container);
    this.fitAddon.fit();
  }

  detach() {
    this.terminal.dispose();
  }

  onMessage(callback: (message: any) => void) {
    this.ws.onmessage = (event) => {
      callback(JSON.parse(event.data));
    };
  }
}
