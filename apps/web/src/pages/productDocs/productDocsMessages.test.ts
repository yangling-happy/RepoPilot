import { describe, expect, it } from "vitest";
import { getCloneErrorMessage } from "./productDocsMessages";

describe("getCloneErrorMessage", () => {
  it("expands the generic backend clone io error", () => {
    expect(
      getCloneErrorMessage("Local file operation failed during clone", "en"),
    ).toContain("backend hit an I/O error during cloning");
  });

  it("keeps unknown errors unchanged", () => {
    expect(getCloneErrorMessage("boom", "en")).toBe("boom");
  });
});
