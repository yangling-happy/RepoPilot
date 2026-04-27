package com.repopilot.business.service.impl;

import com.repopilot.business.config.UserWorkspaceProperties;
import com.repopilot.business.dto.DocLocalScanResult;
import com.repopilot.business.dto.DocRefreshResult;
import com.repopilot.business.entity.DocFile;
import com.repopilot.business.entity.DocTask;
import com.repopilot.business.mapper.DocFileMapper;
import com.repopilot.business.mapper.DocTaskMapper;
import com.repopilot.business.service.docgen.DocGeneratorRegistry;
import com.repopilot.business.service.docgen.JavaDocGenerator;
import com.repopilot.business.service.gitlab.GitLabDocClient;
import com.repopilot.business.service.workspace.UserWorkspaceResolver;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocPipelineServiceImplTest {

    private static final String USERNAME = "alice";

    @Mock
    private DocTaskMapper docTaskMapper;
    @Mock
    private DocFileMapper docFileMapper;
    @Mock
    private GitLabDocClient gitLabDocClient;

    private DocPipelineServiceImpl service;
    private Path workspaceBaseDir;

    @BeforeEach
    void setUp() throws Exception {
        DocGeneratorRegistry registry = new DocGeneratorRegistry(List.of(new JavaDocGenerator()));
        workspaceBaseDir = Files.createTempDirectory("repopilot-workspace-test-");
        UserWorkspaceProperties workspaceProperties = new UserWorkspaceProperties();
        workspaceProperties.setBaseDir(workspaceBaseDir.toString());
        service = new DocPipelineServiceImpl(
                docTaskMapper,
                docFileMapper,
                gitLabDocClient,
                registry,
                new UserWorkspaceResolver(workspaceProperties));
    }

    @Test
    void refresh_shouldReturnNoNewCommits_whenLocalHeadUnchangedAfterPull() throws Exception {
        String projectId = "2";
        String branch = "main";
        Path origin = Files.createTempDirectory(workspaceBaseDir, "origin-refresh-nochange-");

        try (Git originGit = Git.init().setDirectory(origin.toFile()).call()) {
            String initialHead = commitFile(originGit, "src/Test.java", "/** hello */\npublic class Test {}\n", "init");
            checkoutOrCreateBranch(originGit, branch);
            cloneToWorkspace(origin, projectId, branch);

            DocRefreshResult result = service.refresh(USERNAME, projectId, branch, "token");

            assertThat(result.getGitlabUsername()).isEqualTo(USERNAME);
            assertThat(result.getProject()).isEqualTo(projectId);
            assertThat(result.getBranch()).isEqualTo(branch);
            assertThat(result.getBaselineCommit()).isEqualTo(initialHead);
            assertThat(result.getHeadCommit()).isEqualTo(initialHead);
            assertThat(result.getNewCommitCount()).isEqualTo(0);
            assertThat(result.getMessage()).isEqualTo("No new commits.");
            verifyNoInteractions(docTaskMapper, docFileMapper, gitLabDocClient);
        }
    }

    @Test
    void refresh_shouldGenerateDocFromLocalDiffAfterPull() throws Exception {
        String projectId = "3";
        String branch = "main";
        Path origin = Files.createTempDirectory(workspaceBaseDir, "origin-refresh-modified-");

        try (Git originGit = Git.init().setDirectory(origin.toFile()).call()) {
            String oldHead = commitFile(originGit, "src/Test.java", "/** hello */\npublic class Test {}\n", "init");
            checkoutOrCreateBranch(originGit, branch);
            cloneToWorkspace(origin, projectId, branch);
            String newHead = commitFile(originGit, "src/Test.java", "/** updated */\npublic class Test {}\n", "update-test");

            stubTaskInsertWithId(42L);
            when(docTaskMapper.updateById(any())).thenReturn(1);
            when(docFileMapper.selectOne(any())).thenReturn(null);
            when(docFileMapper.insert(any())).thenReturn(1);

            DocRefreshResult result = service.refresh(USERNAME, projectId, branch, "token");

            assertThat(result.getBaselineCommit()).isEqualTo(oldHead);
            assertThat(result.getHeadCommit()).isEqualTo(newHead);
            assertThat(result.getDetectedCommitIds()).contains(newHead);
            assertThat(result.getCreatedTaskCommitIds()).containsExactly(newHead);
            assertThat(result.getFailedTaskCommitIds()).isEmpty();

            ArgumentCaptor<DocTask> taskCaptor = ArgumentCaptor.forClass(DocTask.class);
            verify(docTaskMapper).insert(taskCaptor.capture());
            DocTask savedTask = taskCaptor.getValue();
            assertThat(savedTask.getGitlabUsername()).isEqualTo(USERNAME);
            assertThat(savedTask.getEventId()).startsWith("doc-refresh-" + newHead + "-");

            ArgumentCaptor<DocFile> fileCaptor = ArgumentCaptor.forClass(DocFile.class);
            verify(docFileMapper).insert(fileCaptor.capture());
            DocFile saved = fileCaptor.getValue();
            assertThat(saved.getTaskId()).isEqualTo(42L);
            assertThat(saved.getGitlabUsername()).isEqualTo(USERNAME);
            assertThat(saved.getProject()).isEqualTo(projectId);
            assertThat(saved.getBranch()).isEqualTo(branch);
            assertThat(saved.getFilePath()).isEqualTo("src/Test.java");
            assertThat(saved.getCommitId()).isEqualTo(newHead);
            assertThat(saved.getParseStatus()).isEqualTo("SUCCESS");
            assertThat(saved.getDocFilePath()).isNotBlank();
            assertThat(Files.exists(Path.of(saved.getDocFilePath()))).isTrue();
            verifyNoInteractions(gitLabDocClient);
        }
    }

    @Test
    void refresh_shouldSkipUnsupportedFileSuffix_withoutReadingGitlabContent() throws Exception {
        String projectId = "4";
        String branch = "main";
        Path origin = Files.createTempDirectory(workspaceBaseDir, "origin-refresh-unsupported-");

        try (Git originGit = Git.init().setDirectory(origin.toFile()).call()) {
            commitFile(originGit, "README.md", "# init\n", "init");
            checkoutOrCreateBranch(originGit, branch);
            cloneToWorkspace(origin, projectId, branch);
            String newHead = commitFile(originGit, "README.md", "# changed\n", "update-readme");

            stubTaskInsertWithId(43L);
            when(docTaskMapper.updateById(any())).thenReturn(1);

            DocRefreshResult result = service.refresh(USERNAME, projectId, branch, "token");

            assertThat(result.getCreatedTaskCommitIds()).containsExactly(newHead);
            assertThat(result.getFailedTaskCommitIds()).isEmpty();
            verifyNoInteractions(docFileMapper);
            verifyNoInteractions(gitLabDocClient);
        }
    }

    @Test
    void refresh_shouldMarkFileDeleted_whenLocalDiffContainsDeletedJavaFile() throws Exception {
        String projectId = "5";
        String branch = "main";
        Path origin = Files.createTempDirectory(workspaceBaseDir, "origin-refresh-deleted-");

        try (Git originGit = Git.init().setDirectory(origin.toFile()).call()) {
            commitFile(originGit, "src/OldFile.java", "/** old */\npublic class OldFile {}\n", "init");
            checkoutOrCreateBranch(originGit, branch);
            cloneToWorkspace(origin, projectId, branch);
            String newHead = deleteFileAndCommit(originGit, "src/OldFile.java", "delete-old-file");

            stubTaskInsertWithId(44L);
            when(docTaskMapper.updateById(any())).thenReturn(1);
            when(docFileMapper.selectOne(any())).thenReturn(null);
            when(docFileMapper.insert(any())).thenReturn(1);

            DocRefreshResult result = service.refresh(USERNAME, projectId, branch, "token");

            assertThat(result.getCreatedTaskCommitIds()).containsExactly(newHead);
            assertThat(result.getFailedTaskCommitIds()).isEmpty();

            ArgumentCaptor<DocFile> fileCaptor = ArgumentCaptor.forClass(DocFile.class);
            verify(docFileMapper).insert(fileCaptor.capture());
            DocFile saved = fileCaptor.getValue();
            assertThat(saved.getTaskId()).isEqualTo(44L);
            assertThat(saved.getGitlabUsername()).isEqualTo(USERNAME);
            assertThat(saved.getFilePath()).isEqualTo("src/OldFile.java");
            assertThat(saved.getDocFilePath()).isNull();
            assertThat(saved.getParseErrorMsg()).isEqualTo("File deleted");

            verify(gitLabDocClient, never()).readFileContent(any(), any(), any(), any());
        }
    }

    @Test
    void scanLocal_shouldGenerateDocsFromLocalRepository_withoutGitlabApi() throws Exception {
        String commitId = "1234567890abcdef1234567890abcdef12345678";
        Path repo = repoPath(USERNAME, "2");
        Files.createDirectories(repo.resolve(".git/refs/heads"));
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.writeString(repo.resolve(".git/refs/heads/main"), commitId + "\n");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/Test.java"), "/** hello */\npublic class Test {}\n");
        Files.writeString(repo.resolve("README.md"), "# Test\n");

        stubTaskInsertWithId(55L);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(docFileMapper.selectOne(any())).thenReturn(null);
        when(docFileMapper.insert(any())).thenReturn(1);

        DocLocalScanResult result = service.scanLocal(USERNAME, "2", "main");

        assertThat(result.getGitlabUsername()).isEqualTo(USERNAME);
        assertThat(result.getProject()).isEqualTo("2");
        assertThat(result.getBranch()).isEqualTo("main");
        assertThat(result.getCommitId()).isEqualTo(commitId);
        assertThat(result.getScannedFileCount()).isEqualTo(2);
        assertThat(result.getGeneratedFileCount()).isEqualTo(1);
        assertThat(result.getSkippedFileCount()).isEqualTo(1);
        assertThat(result.getFailedFileCount()).isZero();
        assertThat(result.getGeneratedFilePaths()).containsExactly("src/Test.java");

        ArgumentCaptor<DocTask> taskCaptor = ArgumentCaptor.forClass(DocTask.class);
        verify(docTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getGitlabUsername()).isEqualTo(USERNAME);
        assertThat(taskCaptor.getValue().getEventId()).startsWith("doc-local-scan-" + commitId + "-");

        ArgumentCaptor<DocFile> fileCaptor = ArgumentCaptor.forClass(DocFile.class);
        verify(docFileMapper).insert(fileCaptor.capture());
        DocFile saved = fileCaptor.getValue();
        assertThat(saved.getTaskId()).isEqualTo(55L);
        assertThat(saved.getGitlabUsername()).isEqualTo(USERNAME);
        assertThat(saved.getProject()).isEqualTo("2");
        assertThat(saved.getBranch()).isEqualTo("main");
        assertThat(saved.getFilePath()).isEqualTo("src/Test.java");
        assertThat(saved.getCommitId()).isEqualTo(commitId);
        assertThat(saved.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getDocFilePath()).isNotBlank();
        assertThat(Files.exists(Path.of(saved.getDocFilePath()))).isTrue();
        verifyNoInteractions(gitLabDocClient);
    }

    @Test
    void scanLocal_shouldSkipFilesIgnoredByRootGitignore() throws Exception {
        String commitId = "abcdef7890abcdef1234567890abcdef12345678";
        Path repo = repoPath(USERNAME, "3");
        Files.createDirectories(repo.resolve(".git/refs/heads"));
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.writeString(repo.resolve(".git/refs/heads/main"), commitId + "\n");
        Files.writeString(repo.resolve(".gitignore"), "target/\n");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/Test.java"), "/** hello */\npublic class Test {}\n");
        Files.createDirectories(repo.resolve("target"));
        Files.writeString(repo.resolve("target/Ignored.java"), "/** ignored */\npublic class Ignored {}\n");

        stubTaskInsertWithId(56L);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(docFileMapper.selectOne(any())).thenReturn(null);
        when(docFileMapper.insert(any())).thenReturn(1);

        DocLocalScanResult result = service.scanLocal(USERNAME, "3", "main");

        assertThat(result.getGeneratedFilePaths()).containsExactly("src/Test.java");
        assertThat(result.getGeneratedFilePaths()).doesNotContain("target/Ignored.java");
        assertThat(result.getGeneratedFileCount()).isEqualTo(1);
        verifyNoInteractions(gitLabDocClient);
    }

    private void stubTaskInsertWithId(long id) {
        doAnswer(invocation -> {
            DocTask task = invocation.getArgument(0);
            task.setId(id);
            return 1;
        }).when(docTaskMapper).insert(any(DocTask.class));
    }

    private void checkoutOrCreateBranch(Git git, String branch) throws GitAPIException, IOException {
        String localRef = "refs/heads/" + branch;
        if (localRef.equals(git.getRepository().getFullBranch())) {
            return;
        }
        if (git.getRepository().findRef(localRef) == null) {
            git.checkout().setCreateBranch(true).setName(branch).call();
            return;
        }
        git.checkout().setName(branch).call();
    }

    private String cloneToWorkspace(Path originRepo, String projectId, String branch) throws Exception {
        Path localRepo = repoPath(USERNAME, projectId);
        Files.createDirectories(localRepo.getParent());
        try (Git cloned = Git.cloneRepository()
                .setURI(originRepo.toUri().toString())
                .setDirectory(localRepo.toFile())
                .setBranch("refs/heads/" + branch)
                .call()) {
            return cloned.getRepository().resolve("HEAD").name();
        }
    }

    private String commitFile(Git git, String relativePath, String content, String message) throws Exception {
        Path target = git.getRepository().getWorkTree().toPath().resolve(relativePath);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content);
        git.add().addFilepattern(relativePath.replace('\\', '/')).call();
        git.commit()
                .setMessage(message)
                .setAuthor("tester", "tester@example.com")
                .setCommitter("tester", "tester@example.com")
                .call();
        return git.getRepository().resolve("HEAD").name();
    }

    private String deleteFileAndCommit(Git git, String relativePath, String message) throws Exception {
        Path target = git.getRepository().getWorkTree().toPath().resolve(relativePath);
        Files.deleteIfExists(target);
        git.rm().addFilepattern(relativePath.replace('\\', '/')).call();
        git.commit()
                .setMessage(message)
                .setAuthor("tester", "tester@example.com")
                .setCommitter("tester", "tester@example.com")
                .call();
        return git.getRepository().resolve("HEAD").name();
    }

    private Path repoPath(String username, String projectId) {
        return workspaceBaseDir.resolve("workspace").resolve(username).resolve("repos").resolve("project-" + projectId);
    }
}
