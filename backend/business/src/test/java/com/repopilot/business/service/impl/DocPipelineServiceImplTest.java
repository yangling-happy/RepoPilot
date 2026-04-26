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
import com.repopilot.business.service.gitlab.model.CommitFileChange;
import com.repopilot.business.service.workspace.UserWorkspaceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void refresh_shouldReturnNoNewCommits_whenBaselineEqualsHead() {
        when(gitLabDocClient.getHeadCommit("token", "proj", "main")).thenReturn("c1");
        when(docTaskMapper.selectOne(any())).thenReturn(task("c1", "SUCCESS"));

        DocRefreshResult result = service.refresh(USERNAME, "proj", "main", "token");

        assertThat(result.getGitlabUsername()).isEqualTo(USERNAME);
        assertThat(result.getBaselineCommit()).isEqualTo("c1");
        assertThat(result.getHeadCommit()).isEqualTo("c1");
        assertThat(result.getNewCommitCount()).isEqualTo(0);
        assertThat(result.getMessage()).isEqualTo("No new commits.");
        verify(gitLabDocClient, never()).listCommitIdsSince(anyString(), anyString(), any(), anyString());
        verifyNoInteractions(docFileMapper);
    }

    @Test
    void refresh_shouldSkipHandledCommit_andProcessNewCommitIncrementally() {
        when(gitLabDocClient.getHeadCommit("token", "proj", "main")).thenReturn("c3");
        when(docTaskMapper.selectOne(any())).thenReturn(task("c1", "SUCCESS"));
        when(gitLabDocClient.listCommitIdsSince("token", "proj", "c1", "c3"))
                .thenReturn(List.of("c2", "c3"));
        when(docTaskMapper.selectCount(any())).thenReturn(1L, 0L);

        stubTaskInsertWithId(42L);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(gitLabDocClient.listCommitFileChanges("token", "proj", "c3")).thenReturn(List.of(
                new CommitFileChange(null, "src/Test.java", CommitFileChange.ChangeType.MODIFIED)
        ));
        when(gitLabDocClient.readFileContent("token", "proj", "src/Test.java", "c3"))
                .thenReturn("/** hello */\npublic class Test {}\n");
        when(docFileMapper.selectOne(any())).thenReturn(null);
        when(docFileMapper.insert(any())).thenReturn(1);

        DocRefreshResult result = service.refresh(USERNAME, "proj", "main", "token");

        assertThat(result.getDetectedCommitIds()).containsExactly("c2", "c3");
        assertThat(result.getSkippedCommitIds()).containsExactly("c2");
        assertThat(result.getCreatedTaskCommitIds()).containsExactly("c3");
        assertThat(result.getFailedTaskCommitIds()).isEmpty();

        ArgumentCaptor<DocTask> taskCaptor = ArgumentCaptor.forClass(DocTask.class);
        verify(docTaskMapper).insert(taskCaptor.capture());
        DocTask savedTask = taskCaptor.getValue();
        assertThat(savedTask.getGitlabUsername()).isEqualTo(USERNAME);
        assertThat(savedTask.getEventId()).startsWith("doc-refresh-c3-");

        ArgumentCaptor<DocFile> fileCaptor = ArgumentCaptor.forClass(DocFile.class);
        verify(docFileMapper).insert(fileCaptor.capture());
        DocFile saved = fileCaptor.getValue();
        assertThat(saved.getTaskId()).isEqualTo(42L);
        assertThat(saved.getGitlabUsername()).isEqualTo(USERNAME);
        assertThat(saved.getProject()).isEqualTo("proj");
        assertThat(saved.getBranch()).isEqualTo("main");
        assertThat(saved.getFilePath()).isEqualTo("src/Test.java");
        assertThat(saved.getCommitId()).isEqualTo("c3");
        assertThat(saved.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getDocFilePath()).isNotBlank();
        assertThat(Files.exists(Path.of(saved.getDocFilePath()))).isTrue();
    }

    @Test
    void refresh_shouldSkipUnsupportedFileSuffix_withoutReadingContent() {
        when(gitLabDocClient.getHeadCommit("token", "proj", "main")).thenReturn("c2");
        when(docTaskMapper.selectOne(any())).thenReturn(task("c1", "SUCCESS"));
        when(gitLabDocClient.listCommitIdsSince("token", "proj", "c1", "c2")).thenReturn(List.of("c2"));
        when(docTaskMapper.selectCount(any())).thenReturn(0L);

        stubTaskInsertWithId(43L);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(gitLabDocClient.listCommitFileChanges("token", "proj", "c2")).thenReturn(List.of(
                new CommitFileChange(null, "README.md", CommitFileChange.ChangeType.MODIFIED)
        ));

        DocRefreshResult result = service.refresh(USERNAME, "proj", "main", "token");

        assertThat(result.getCreatedTaskCommitIds()).containsExactly("c2");
        assertThat(result.getFailedTaskCommitIds()).isEmpty();
        verify(gitLabDocClient, never()).readFileContent(eq("token"), eq("proj"), eq("README.md"), eq("c2"));
        verifyNoInteractions(docFileMapper);
    }

    @Test
    void refresh_shouldMarkFileDeleted_whenDiffContainsDeletedJavaFile() {
        when(gitLabDocClient.getHeadCommit("token", "proj", "main")).thenReturn("c2");
        when(docTaskMapper.selectOne(any())).thenReturn(task("c1", "SUCCESS"));
        when(gitLabDocClient.listCommitIdsSince("token", "proj", "c1", "c2")).thenReturn(List.of("c2"));
        when(docTaskMapper.selectCount(any())).thenReturn(0L);

        stubTaskInsertWithId(44L);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(gitLabDocClient.listCommitFileChanges("token", "proj", "c2")).thenReturn(List.of(
                new CommitFileChange("src/OldFile.java", null, CommitFileChange.ChangeType.DELETED)
        ));
        when(docFileMapper.selectOne(any())).thenReturn(null);
        when(docFileMapper.insert(any())).thenReturn(1);

        DocRefreshResult result = service.refresh(USERNAME, "proj", "main", "token");

        assertThat(result.getCreatedTaskCommitIds()).containsExactly("c2");
        assertThat(result.getFailedTaskCommitIds()).isEmpty();

        ArgumentCaptor<DocFile> fileCaptor = ArgumentCaptor.forClass(DocFile.class);
        verify(docFileMapper).insert(fileCaptor.capture());
        DocFile saved = fileCaptor.getValue();
        assertThat(saved.getTaskId()).isEqualTo(44L);
        assertThat(saved.getGitlabUsername()).isEqualTo(USERNAME);
        assertThat(saved.getFilePath()).isEqualTo("src/OldFile.java");
        assertThat(saved.getDocFilePath()).isNull();
        assertThat(saved.getParseErrorMsg()).isEqualTo("File deleted");

        verify(gitLabDocClient, never()).readFileContent(eq("token"), eq("proj"), eq("src/OldFile.java"), eq("c2"));
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

    private static DocTask task(String commitId, String status) {
        DocTask task = new DocTask();
        task.setCommitId(commitId);
        task.setStatus(status);
        return task;
    }

    private void stubTaskInsertWithId(long id) {
        doAnswer(invocation -> {
            DocTask task = invocation.getArgument(0);
            task.setId(id);
            return 1;
        }).when(docTaskMapper).insert(any(DocTask.class));
    }

    private Path repoPath(String username, String projectId) {
        return workspaceBaseDir.resolve("workspace").resolve(username).resolve("repos").resolve("project-" + projectId);
    }
}
