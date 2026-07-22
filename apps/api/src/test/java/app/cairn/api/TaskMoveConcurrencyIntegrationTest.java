package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.cairn.api.core.id.Uuid7;
import app.cairn.api.core.org.OrgContext;
import app.cairn.api.support.IntegrationTestBase;
import app.cairn.api.tasks.Task;
import app.cairn.api.tasks.TaskService;
import app.cairn.api.tasks.TaskWithSectionKey;
import app.cairn.api.tasks.ordering.Ordering;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Concurrent-move race: many threads issue {@code move} against the service layer (real transactions)
 * on the same section, interleaved. The final state must be a valid total order — every task present
 * exactly once (no lost or duplicated rows), every sort_key non-empty, and the ordering strictly
 * consistent under {@code (sort_key, created_at, id)} and stable across repeated reads. Fractional keys
 * plus that tiebreak guarantee this even if two moves happen to land equal keys.
 */
class TaskMoveConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired private TaskService taskService;
    @Autowired private OrgContext orgContext;

    private static final int THREADS = 8;
    private static final int MOVES_PER_THREAD = 30;
    private static final int TASK_COUNT = 15;

    @Test
    void concurrentMovesProduceAConsistentTotalOrder() throws Exception {
        UUID orgId = Uuid7.generate();
        UUID userId = Uuid7.generate();
        UUID teamId = Uuid7.generate();
        UUID projectId = Uuid7.generate();
        UUID sectionId = Uuid7.generate();
        seed(orgId, userId, teamId, projectId, sectionId);

        List<UUID> taskIds = new ArrayList<>();
        List<String> keys = Ordering.rebalance(TASK_COUNT);
        for (int i = 0; i < TASK_COUNT; i++) {
            UUID id = Uuid7.generate();
            taskIds.add(id);
            jdbc.update(
                    "insert into tasks (id, organization_id, project_id, section_id, title, priority, completed, created_by, sort_key)"
                            + " values (?,?,?,?,?, 'none', false, ?, ?)",
                    id, orgId, projectId, sectionId, "task-" + i, userId, keys.get(i));
        }

        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    runInOrgContext(orgId, () -> {
                        ThreadLocalRandom rnd = ThreadLocalRandom.current();
                        for (int m = 0; m < MOVES_PER_THREAD; m++) {
                            UUID moved = taskIds.get(rnd.nextInt(taskIds.size()));
                            UUID anchor = taskIds.get(rnd.nextInt(taskIds.size()));
                            if (anchor.equals(moved)) {
                                continue;
                            }
                            try {
                                if (rnd.nextBoolean()) {
                                    taskService.move(moved, sectionId, anchor, null); // before anchor
                                } else {
                                    taskService.move(moved, sectionId, null, anchor); // after anchor
                                }
                            } catch (RuntimeException e) {
                                errors.add(e);
                            }
                        }
                    });
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        done.await();
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(errors).as("no move threw").isEmpty();

        List<Task> finalOrder = readOrdered(orgId, projectId);

        // no lost or duplicated tasks
        List<UUID> finalIds = finalOrder.stream().map(Task::id).toList();
        assertThat(finalIds).containsExactlyInAnyOrderElementsOf(taskIds);
        assertThat(finalIds).doesNotHaveDuplicates();

        // every sort key is a real, non-empty key
        for (Task task : finalOrder) {
            assertThat(task.sortKey()).isNotBlank();
        }

        // strictly ordered under (sort_key, created_at, id) — a valid total order
        Comparator<Task> tuple = Comparator.comparing(Task::sortKey)
                .thenComparing(Task::createdAt)
                .thenComparing(Task::id);
        for (int i = 1; i < finalOrder.size(); i++) {
            assertThat(tuple.compare(finalOrder.get(i - 1), finalOrder.get(i)))
                    .as("row %d strictly precedes row %d", i - 1, i)
                    .isNegative();
        }

        // stable: reading again yields the identical sequence
        List<UUID> again = readOrdered(orgId, projectId).stream().map(Task::id).toList();
        assertThat(again).containsExactlyElementsOf(finalIds);
    }

    private List<Task> readOrdered(UUID orgId, UUID projectId) {
        List<Task> out = new ArrayList<>();
        runInOrgContext(orgId, () -> {
            for (TaskWithSectionKey row : taskService.listByProject(projectId, null, 1000)) {
                out.add(row.task());
            }
        });
        return out;
    }

    /** Bind a request scope on the current thread so the request-scoped OrgContext resolves, then run. */
    private void runInOrgContext(UUID orgId, Runnable body) {
        ServletRequestAttributes attrs = new ServletRequestAttributes(new MockHttpServletRequest());
        RequestContextHolder.setRequestAttributes(attrs);
        try {
            orgContext.set(orgId);
            body.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private void seed(UUID orgId, UUID userId, UUID teamId, UUID projectId, UUID sectionId) {
        jdbc.update("insert into organizations (id, name) values (?, ?)", orgId, "Race Org");
        jdbc.update("insert into users (id, email, name) values (?, ?, ?)", userId, "race@acme.test", "Race");
        jdbc.update(
                "insert into memberships (id, organization_id, user_id, role, active) values (?,?,?, 'admin', true)",
                Uuid7.generate(), orgId, userId);
        jdbc.update("insert into teams (id, organization_id, name) values (?,?,?)", teamId, orgId, "General");
        jdbc.update(
                "insert into projects (id, organization_id, team_id, owner_id, name, default_view, archived)"
                        + " values (?,?,?,?,?, 'list', false)",
                projectId, orgId, teamId, userId, "Race Project");
        jdbc.update(
                "insert into sections (id, organization_id, project_id, name, sort_key) values (?,?,?,?,?)",
                sectionId, orgId, projectId, "Todo", Ordering.between(null, null));
    }
}
