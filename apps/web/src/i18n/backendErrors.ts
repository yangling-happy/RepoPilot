import type { TFunction } from "i18next";

function extractTail(message: string): string {
  const [, tail = ""] = message.split(":");
  return tail.trim();
}

export function mapCloneErrorMessage(message: string, t: TFunction): string {
  if (message === "GitLab token is required in session") {
    return t("pages.documentation.errors.backend.gitlabTokenRequired");
  }

  if (message.startsWith("GitLab project not found:")) {
    return t("pages.documentation.errors.backend.gitlabProjectNotFound", {
      projectId: extractTail(message),
    });
  }

  if (message.startsWith("Branch not found:")) {
    return t("pages.documentation.errors.backend.branchNotFound", {
      branch: extractTail(message),
    });
  }

  if (message === "Invalid token or insufficient permission") {
    return t("pages.documentation.errors.backend.invalidToken");
  }

  if (message === "Clone failed, please check token and repository permissions") {
    return t("pages.documentation.errors.backend.cloneFailed");
  }

  if (message.startsWith("Local directory already exists:")) {
    return t("pages.documentation.errors.backend.localDirExists", { message });
  }

  if (message === "Local file operation failed during clone") {
    return t("pages.documentation.errors.backend.localFileIoFailed");
  }

  return message;
}
