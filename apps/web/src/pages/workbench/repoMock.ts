export type WorkbenchRepo = {
  id: string;
  name: string;
  visibility: "private" | "internal" | "public";
  stack: string;
  descriptionKey?: string;
  description?: string;
  branch: string;
  owner: string;
  lastUpdatedAt: string;
  lastDeployAt: string;
  docsEndpoint: string;
  /** 是否为远程GitLab仓库（未克隆到本地） */
  isRemote?: boolean;
  /** GitLab项目ID */
  gitlabProjectId?: number;
  /** 默认分支 */
  defaultBranch?: string;
};

export const WORKBENCH_REPOS: WorkbenchRepo[] = [];
