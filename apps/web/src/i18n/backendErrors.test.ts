import type { TFunction } from "i18next";
import { describe, expect, it, vi } from "vitest";
import { mapCloneErrorMessage } from "../i18n/backendErrors";

describe("mapCloneErrorMessage", () => {
  it("maps the generic backend clone io error to the i18n key", () => {
    const t = vi.fn((key: string) => `translated:${key}`) as unknown as TFunction;
    expect(
      mapCloneErrorMessage("Local file operation failed during clone", t),
    ).toBe("translated:pages.documentation.errors.backend.localFileIoFailed");
    expect(t).toHaveBeenCalledWith(
      "pages.documentation.errors.backend.localFileIoFailed",
    );
  });

  it("keeps unknown errors unchanged", () => {
    const t = vi.fn((key: string) => key) as unknown as TFunction;
    expect(mapCloneErrorMessage("boom", t)).toBe("boom");
    expect(t).not.toHaveBeenCalled();
  });
});
