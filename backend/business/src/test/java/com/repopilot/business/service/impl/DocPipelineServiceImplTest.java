package com.repopilot.business.service.impl;

import com.repopilot.business.dto.DocRefreshResult;
import com.repopilot.business.entity.DocFile;
import com.repopilot.business.entity.DocTask;
import com.repopilot.business.mapper.DocFileMapper;
import com.repopilot.business.mapper.DocTaskMapper;
import com.repopilot.business.service.docgen.DocGeneratorRegistry;
import com.repopilot.business.service.docgen.JavaDocGenerator;
import com.repopilot.business.service.gitlab.GitLabDocClient;
import com.repopilot.business.service.gitlab.model.CommitFileChange;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocPipelineServiceImplTest {

    @Mock
    private DocTaskMapper docTaskMapper;
    @Mock
    private DocFileMapper docFileMapper;
    @Mock
    private GitLabDocClient gitLabDocClient;

    private DocPipelineServiceImpl service;
    private Path docOutputRoot;

    @BeforeEach
    void setUp() throws Exception {
        DocGeneratorRegistry registry = new DocGeneratorRegistry(List.of(new JavaDocGenerator()));
        service = new DocPipelineServiceImpl(docTaskMapper, docFileMapper, gitLabDocClient, registry);
        docOutputRoot = Files.createTempDirectory("repopilot-doc-test-");
        setField("docOutputRoot", docOutputRoot.toString());
    }

    @Test
    void refresh_shouldReturnNoNewCommits_whenBaselineEqualsHead() {
        when(gitLabDocClient.getHeadCommit("token", "proj", "main")).thenReturn("c1");
        when(docTaskMapper.selectOne(any())).thenReturn(task("c1", "SUCCESS"));

        DocRefreshResult result = service.refresh("proj", "main", "token");

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

        when(docTaskMapper.insert(any())).thenReturn(1);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(gitLabDocClient.listCommitFileChanges("token", "proj", "c3")).thenReturn(List.of(
                new CommitFileChange(null, "src/Test.java", CommitFileChange.ChangeType.MODIFIED)
        ));
        when(gitLabDocClient.readFileContent("token", "proj", "src/Test.java", "c3"))
                .thenReturn("/** hello */\npublic class Test {}\n");
        when(docFileMapper.selectOne(any())).thenReturn(null);
        when(docFileMapper.insert(any())).thenReturn(1);

        DocRefreshResult result = service.refresh("proj", "main", "token");

        assertThat(result.getDetectedCommitIds()).containsExactly("c2", "c3");
        assertThat(result.getSkippedCommitIds()).containsExactly("c2");
        assertThat(result.getCreatedTaskCommitIds()).containsExactly("c3");
        assertThat(result.getFailedTaskCommitIds()).isEmpty();

        ArgumentCaptor<DocFile> fileCaptor = ArgumentCaptor.forClass(DocFile.class);
        verify(docFileMapper).insert(fileCaptor.capture());
        DocFile saved = fileCaptor.getValue();
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

        when(docTaskMapper.insert(any())).thenReturn(1);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(gitLabDocClient.listCommitFileChanges("token", "proj", "c2")).thenReturn(List.of(
                new CommitFileChange(null, "README.md", CommitFileChange.ChangeType.MODIFIED)
        ));

        DocRefreshResult result = service.refresh("proj", "main", "token");

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

        when(docTaskMapper.insert(any())).thenReturn(1);
        when(docTaskMapper.updateById(any())).thenReturn(1);
        when(gitLabDocClient.listCommitFileChanges("token", "proj", "c2")).thenReturn(List.of(
                new CommitFileChange("src/OldFile.java", null, CommitFileChange.ChangeType.DELETED)
        ));
        when(docFileMapper.selectOne(any())).thenReturn(null);
        when(docFileMapper.insert(any())).thenReturn(1);

        DocRefreshResult result = service.refresh("proj", "main", "token");

        assertThat(result.getCreatedTaskCommitIds()).containsExactly("c2");
        assertThat(result.getFailedTaskCommitIds()).isEmpty();

        ArgumentCaptor<DocFile> fileCaptor = ArgumentCaptor.forClass(DocFile.class);
        verify(docFileMapper).insert(fileCaptor.capture());
        DocFile saved = fileCaptor.getValue();
        assertThat(saved.getFilePath()).isEqualTo("src/OldFile.java");
        assertThat(saved.getDocFilePath()).isNull();
        assertThat(saved.getParseErrorMsg()).isEqualTo("File deleted");

        verify(gitLabDocClient, never()).readFileContent(eq("token"), eq("proj"), eq("src/OldFile.java"), eq("c2"));
    }

    private static DocTask task(String commitId, String status) {
        DocTask task = new DocTask();
        task.setCommitId(commitId);
        task.setStatus(status);
        return task;
    }

    private void setField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = DocPipelineServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
//这是一个测试文件，用于测试DocPipelineServiceImpl类的refresh方法。它使用Mockito框架模拟了GitLabDocClient、DocTaskMapper和DocFileMapper的行为，并验证了在不同情况下refresh方法的逻辑是否正确。测试覆盖了以下场景：
//1. 当基线提交和头提交相同时，应该返回没有新提交的结果，并且不调用listCommitIdsSince方法。
//2. 当有新提交时，应该跳过已经处理过的提交，并且增量处理新的提交，最终保存新的文档文件内容到数据库。
//3. 当提交的文件变更包含删除Java文件时，应该标记该文件为已删除，并且不尝试读取文件内容。
