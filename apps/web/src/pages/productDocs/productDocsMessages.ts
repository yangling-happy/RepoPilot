const LOCALE_ZH_PREFIX = "zh";

export function getCloneErrorMessage(
  message: string,
  language: string,
): string {
  const isChinese = language.toLowerCase().startsWith(LOCALE_ZH_PREFIX);

  if (message === "GitLab token is required in session") {
    return isChinese
      ? "当前浏览器会话里没有可用的 GitLab Token。请先点击“保存 Token”，或重新输入 Token 后再克隆。"
      : "No GitLab token is available in the current browser session. Save the token again before cloning.";
  }

  if (message.startsWith("GitLab project not found:")) {
    return isChinese
      ? `未找到对应的 GitLab 项目：${extractTail(message)}。请确认项目 ID 是否正确。`
      : `GitLab project not found: ${extractTail(message)}. Verify the project ID.`;
  }

  if (message.startsWith("Branch not found:")) {
    return isChinese
      ? `未找到分支：${extractTail(message)}。请确认分支名是否存在。`
      : `Branch not found: ${extractTail(message)}. Verify the branch name.`;
  }

  if (message === "Invalid token or insufficient permission") {
    return isChinese
      ? "GitLab Token 无效，或当前账号没有访问该仓库的权限。"
      : "The GitLab token is invalid, or the current account cannot access this repository.";
  }

  if (message === "Clone failed, please check token and repository permissions") {
    return isChinese
      ? "仓库克隆失败。请检查 GitLab Token 是否可用，以及当前账号是否拥有仓库读取权限。"
      : "Repository clone failed. Check the GitLab token and repository read permissions.";
  }

  if (message.startsWith("Local directory already exists:")) {
    return isChinese
      ? `${message}。如果这是之前已经克隆过的仓库，可以直接点击“生成本地文档”。`
      : `${message}. If this repository was cloned before, you can generate local docs directly.`;
  }

  if (message === "Local file operation failed during clone") {
    return isChinese
      ? "后端在克隆阶段发生了 I/O 异常。这条原始报错比较笼统，通常表示后端无法访问 GitLab 仓库地址，或无法在本地创建/写入克隆目录。请重点检查 GitLab API/仓库地址配置、克隆目录权限，以及目标目录是否已存在。"
      : "The backend hit an I/O error during cloning. This original message is generic and usually means the backend could not reach the GitLab repository URL, or it could not create or write the local clone directory. Check the GitLab API/repository URL, clone-directory permissions, and whether the target directory already exists.";
  }

  return message;
}

export function getTerminalUnavailableMessage(language: string): string {
  const isChinese = language.toLowerCase().startsWith(LOCALE_ZH_PREFIX);
  return isChinese
    ? "虚拟终端当前未连接。克隆和生成文档仍会继续发起，但本页不会显示实时日志。请检查 terminal 服务地址，或确认 `VITE_TERMINAL_WS_URL` / `/ws` 代理配置是否正确。"
    : "The virtual terminal is currently unavailable. Clone and doc-generation requests can still run, but live logs will not stream into this page. Check the terminal service address or your `VITE_TERMINAL_WS_URL` / `/ws` proxy configuration.";
}

function extractTail(message: string): string {
  const [, tail = ""] = message.split(":");
  return tail.trim();
}
