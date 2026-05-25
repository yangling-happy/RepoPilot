import type {
  CloneRepoResponse,
  DocLocalScanResult,
  DocQueryItem,
} from "../services/backendApi";

export const MOCK_REPO = "mock";
export const MOCK_PROJECT_ID = 999;
export const MOCK_BRANCH = "main";
export const MOCK_COMMIT_ID = "a1b2c3d4e5f6789012345678abcdef012345678";

export function isMockRepo(repo: string | null | undefined): boolean {
  return repo === MOCK_REPO;
}

export function isMockProjectId(projectId: string): boolean {
  return Number(projectId) === MOCK_PROJECT_ID;
}

export function isMockMode(
  repo: string | null | undefined,
  projectId?: string,
): boolean {
  return isMockRepo(repo) || (projectId ? isMockProjectId(projectId) : false);
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export function createMockCloneResponse(
  branch: string,
): CloneRepoResponse {
  return {
    projectId: MOCK_PROJECT_ID,
    gitlabUsername: "demo-user",
    projectPath: "demo/repopilot-sample",
    branch,
    cloneUrl: "https://gitlab.example.com/demo/repopilot-sample.git",
    workspacePath: "/tmp/repopilot/workspaces/demo-user",
    localPath: "/tmp/repopilot/workspaces/demo-user/999-main",
    commitId: MOCK_COMMIT_ID,
  };
}

export function createMockScanResult(
  branch: string,
  docs: DocQueryItem[],
): DocLocalScanResult {
  return {
    gitlabUsername: "demo-user",
    project: MOCK_REPO,
    branch,
    commitId: MOCK_COMMIT_ID,
    localRepoPath: "/tmp/repopilot/workspaces/demo-user/999-main",
    scannedFileCount: 42,
    generatedFileCount: docs.length,
    skippedFileCount: 3,
    failedFileCount: 1,
    generatedFilePaths: docs.map((doc) => doc.docFilePath),
    failedFilePaths: ["src/main/java/com/example/legacy/LegacyParser.java"],
    message: "Mock scan completed",
    fileListingDurationMs: 128,
    docGenerationDurationMs: 1840,
    dbOperationDurationMs: 96,
    totalDurationMs: 2064,
  };
}

export const MOCK_DOC_ITEMS: DocQueryItem[] = [
  {
    gitlabUsername: "demo-user",
    project: MOCK_REPO,
    branch: MOCK_BRANCH,
    filePath: "src/main/java/com/example/auth/UserService.java",
    commitId: MOCK_COMMIT_ID,
    docFilePath: "docs/com/example/auth/UserService.html",
    parseStatus: "SUCCESS",
    structuredDoc: {
      schemaVersion: "1",
      project: MOCK_REPO,
      branch: MOCK_BRANCH,
      commitId: MOCK_COMMIT_ID,
      sourceFilePath: "src/main/java/com/example/auth/UserService.java",
      types: [
        {
          htmlFile: "UserService.html",
          kind: "class",
          name: "UserService",
          qualifiedName: "com.example.auth.UserService",
          signature: "public class UserService",
          description:
            "用户领域服务，负责用户查询、创建与权限校验。所有对外接口均通过 Spring 容器注入。",
          fields: [
            {
              id: "userRepository",
              kind: "field",
              name: "userRepository",
              signature: "private final UserRepository userRepository",
              description: "用户数据访问层。",
              parameters: [],
            },
          ],
          constructors: [
            {
              id: "constructor",
              kind: "constructor",
              name: "UserService",
              signature: "public UserService(UserRepository userRepository)",
              description: "通过依赖注入构造 UserService。",
              parameters: [
                {
                  name: "userRepository",
                  type: "UserRepository",
                  description: "用户仓储实现。",
                },
              ],
            },
          ],
          methods: [
            {
              id: "findById",
              kind: "method",
              name: "findById",
              signature: "public Optional<User> findById(String userId)",
              description: "按 ID 查询用户，不存在时返回 empty。",
              parameters: [
                {
                  name: "userId",
                  type: "String",
                  description: "用户唯一标识。",
                },
              ],
              returns: {
                type: "Optional<User>",
                description: "匹配的用户实体。",
              },
            },
            {
              id: "createUser",
              kind: "method",
              name: "createUser",
              signature:
                "public User createUser(CreateUserRequest request) throws DuplicateUserException",
              description: "创建新用户并持久化到数据库。",
              parameters: [
                {
                  name: "request",
                  type: "CreateUserRequest",
                  description: "包含用户名、邮箱等字段。",
                },
              ],
              returns: {
                type: "User",
                description: "创建成功的用户实体。",
              },
              throws: [
                {
                  type: "DuplicateUserException",
                  description: "用户名或邮箱已存在时抛出。",
                },
              ],
            },
          ],
        },
      ],
    },
  },
  {
    gitlabUsername: "demo-user",
    project: MOCK_REPO,
    branch: MOCK_BRANCH,
    filePath: "src/main/java/com/example/auth/AuthController.java",
    commitId: MOCK_COMMIT_ID,
    docFilePath: "docs/com/example/auth/AuthController.html",
    parseStatus: "SUCCESS",
    structuredDoc: {
      schemaVersion: "1",
      project: MOCK_REPO,
      branch: MOCK_BRANCH,
      commitId: MOCK_COMMIT_ID,
      sourceFilePath: "src/main/java/com/example/auth/AuthController.java",
      types: [
        {
          htmlFile: "AuthController.html",
          kind: "class",
          name: "AuthController",
          qualifiedName: "com.example.auth.AuthController",
          signature: "@RestController public class AuthController",
          description: "认证相关 REST 接口，提供登录、登出与 token 刷新能力。",
          fields: [
            {
              id: "authService",
              kind: "field",
              name: "authService",
              signature: "private final AuthService authService",
              description: "认证业务服务。",
              parameters: [],
            },
          ],
          constructors: [],
          methods: [
            {
              id: "login",
              kind: "method",
              name: "login",
              signature:
                "public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request)",
              description: "用户名密码登录，成功后返回 JWT。",
              parameters: [
                {
                  name: "request",
                  type: "LoginRequest",
                  description: "登录凭证。",
                },
              ],
              returns: {
                type: "ResponseEntity<LoginResponse>",
                description: "包含 accessToken 与过期时间。",
              },
            },
          ],
        },
      ],
    },
  },
  {
    gitlabUsername: "demo-user",
    project: MOCK_REPO,
    branch: MOCK_BRANCH,
    filePath: "src/main/java/com/example/repo/RepoCloneService.java",
    commitId: MOCK_COMMIT_ID,
    docFilePath: "docs/com/example/repo/RepoCloneService.html",
    parseStatus: "SUCCESS",
    structuredDoc: {
      schemaVersion: "1",
      project: MOCK_REPO,
      branch: MOCK_BRANCH,
      commitId: MOCK_COMMIT_ID,
      sourceFilePath: "src/main/java/com/example/repo/RepoCloneService.java",
      types: [
        {
          htmlFile: "RepoCloneService.html",
          kind: "interface",
          name: "RepoCloneService",
          qualifiedName: "com.example.repo.RepoCloneService",
          signature: "public interface RepoCloneService",
          description:
            "仓库克隆服务接口，封装 GitLab 项目拉取与本地工作区管理。",
          fields: [],
          constructors: [],
          methods: [
            {
              id: "cloneProject",
              kind: "method",
              name: "cloneProject",
              signature:
                "CloneRepoResponse cloneProject(long projectId, String branch, String terminalSessionId)",
              description: "克隆指定 GitLab 项目到本地工作区。",
              parameters: [
                {
                  name: "projectId",
                  type: "long",
                  description: "GitLab 项目 ID。",
                },
                {
                  name: "branch",
                  type: "String",
                  description: "目标分支名。",
                },
                {
                  name: "terminalSessionId",
                  type: "String",
                  description: "终端会话 ID，用于推送实时日志。",
                },
              ],
              returns: {
                type: "CloneRepoResponse",
                description: "克隆结果，包含本地路径与 commitId。",
              },
            },
          ],
        },
      ],
    },
  },
  {
    gitlabUsername: "demo-user",
    project: MOCK_REPO,
    branch: MOCK_BRANCH,
    filePath: "src/main/java/com/example/legacy/LegacyParser.java",
    commitId: MOCK_COMMIT_ID,
    docFilePath: "docs/com/example/legacy/LegacyParser.html",
    parseStatus: "FAILED",
    parseErrorMsg: "JavaDoc 解析失败：缺少 @param 标签 (line 87)",
    structuredDoc: null,
  },
];

export async function simulateMockClone(
  onProgress?: (line: string) => void,
): Promise<CloneRepoResponse> {
  const steps = [
    "[mock] Resolving GitLab project 999 ...",
    "[mock] Fetching branch main ...",
    "[mock] Cloning into /tmp/repopilot/workspaces/demo-user/999-main ...",
    "[mock] Checking out commit a1b2c3d ...",
  ];

  for (const step of steps) {
    onProgress?.(step);
    await delay(400);
  }

  return createMockCloneResponse(MOCK_BRANCH);
}

export async function simulateMockScan(
  branch: string,
  onProgress?: (line: string) => void,
): Promise<DocLocalScanResult> {
  const steps = [
    "[mock] Listing source files under src/main/java ...",
    "[mock] Found 42 Java files",
    "[mock] Generating structured docs ...",
    "[mock] Persisting 4 documents to database ...",
  ];

  for (const step of steps) {
    onProgress?.(step);
    await delay(500);
  }

  return createMockScanResult(branch, MOCK_DOC_ITEMS);
}
