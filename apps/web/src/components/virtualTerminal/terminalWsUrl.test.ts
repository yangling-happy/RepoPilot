import { describe, expect, it } from "vitest";
import { resolveTerminalWsUrl } from "./terminalWsUrl";

const location = {
  protocol: "http:",
  host: "localhost:3000",
};

describe("resolveTerminalWsUrl", () => {
  it("uses the current host by default", () => {
    expect(
      resolveTerminalWsUrl("docs-session", {
        location,
      }),
    ).toBe("ws://localhost:3000/ws/terminal/docs-session");
  });

  it("supports relative configured paths", () => {
    expect(
      resolveTerminalWsUrl("docs-session", {
        configuredBaseUrl: "/terminal/ws",
        location,
      }),
    ).toBe("ws://localhost:3000/terminal/ws/docs-session");
  });

  it("converts http endpoints into websocket endpoints", () => {
    expect(
      resolveTerminalWsUrl("docs-session", {
        configuredBaseUrl: "http://localhost:8082/ws/terminal",
        location,
      }),
    ).toBe("ws://localhost:8082/ws/terminal/docs-session");
  });
});
