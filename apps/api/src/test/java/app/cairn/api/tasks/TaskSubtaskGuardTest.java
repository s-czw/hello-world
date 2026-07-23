package app.cairn.api.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.core.error.ConflictException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.orgs.member.Role;
import app.cairn.api.projects.ProjectService;
import app.cairn.api.projects.SectionService;
import app.cairn.api.tasks.event.TaskEvents;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Unit test for the one-level subtask guard (C2): a subtask cannot itself have subtasks. */
@ExtendWith(MockitoExtension.class)
class TaskSubtaskGuardTest {

    @Mock TaskRepository tasks;
    @Mock ProjectService projects;
    @Mock SectionService sections;
    @Mock MembershipService memberships;
    @Mock ApplicationEventPublisher events;

    private TaskService service() {
        return new TaskService(tasks, projects, sections, memberships, events);
    }

    private final UUID caller = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final AuthPrincipal principal = new AuthPrincipal(caller, orgId, Role.ADMIN, "a@x.io");

    private Task task(UUID id, UUID parentId) {
        return new Task(
                id, UUID.randomUUID(), null, null, "T", null, "none", null,
                false, null, caller, "a", parentId, parentId == null ? null : "a",
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    void addingSubtaskToASubtaskConflicts() {
        UUID parentId = UUID.randomUUID();
        // the "parent" is itself a subtask (has its own parent)
        when(tasks.findById(parentId)).thenReturn(Optional.of(task(parentId, UUID.randomUUID())));

        assertThatThrownBy(() -> service().createSubtask(principal, parentId, "child"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("one level");

        verify(tasks, never()).insertSubtask(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addingSubtaskToATopLevelTaskSucceeds() {
        UUID parentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task parent = new Task(
                parentId, projectId, null, null, "Parent", null, "none", null,
                false, null, caller, "a", null, null, OffsetDateTime.now(), OffsetDateTime.now());
        UUID newId = UUID.randomUUID();

        when(tasks.findById(parentId)).thenReturn(Optional.of(parent));
        when(tasks.maxSubtaskSortKey(parentId)).thenReturn(Optional.empty());
        when(tasks.insertSubtask(eq(projectId), eq(parentId), any(), eq("child"), eq(caller), any()))
                .thenReturn(newId);
        when(tasks.findById(newId)).thenReturn(Optional.of(task(newId, parentId)));

        Task created = service().createSubtask(principal, parentId, "child");

        assertThat(created.id()).isEqualTo(newId);
        assertThat(created.isSubtask()).isTrue();
        verify(events).publishEvent(any(TaskEvents.TaskCreated.class));
    }
}
