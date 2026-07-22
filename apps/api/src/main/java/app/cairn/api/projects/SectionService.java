package app.cairn.api.projects;

import app.cairn.api.core.error.ApiException;
import app.cairn.api.core.error.ConflictException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.tasks.TaskService;
import app.cairn.api.tasks.ordering.Ordering;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sections domain service. New sections append to the end of their project; ordering uses the same
 * fractional {@link Ordering} keys as tasks. Deleting a non-empty section requires either that it be
 * empty or a {@code moveTo} target section to relocate its tasks first.
 */
@Service
public class SectionService {

    private final SectionRepository sections;
    private final ProjectService projects;
    private final TaskService tasks;

    public SectionService(
            SectionRepository sections, ProjectService projects, @Lazy TaskService tasks) {
        this.sections = sections;
        this.projects = projects;
        this.tasks = tasks;
    }

    public List<Section> listByProject(UUID projectId) {
        projects.requireProject(projectId);
        return sections.listByProject(projectId);
    }

    public Section get(UUID id) {
        return sections.findById(id).orElseThrow(() -> NotFoundException.of("Section"));
    }

    /** Assert a section exists within the given project (used by tasks on create/move). */
    public void requireSectionInProject(UUID sectionId, UUID projectId) {
        if (!sections.existsInProject(sectionId, projectId)) {
            throw NotFoundException.of("Section");
        }
    }

    @Transactional
    public Section create(UUID projectId, String name) {
        projects.requireProject(projectId);
        String sortKey = Ordering.after(sections.maxSortKey(projectId).orElse(null));
        UUID id = sections.insert(projectId, name.trim(), sortKey);
        return sections.findById(id).orElseThrow();
    }

    @Transactional
    public Section rename(UUID id, String name) {
        Section section = get(id);
        sections.updateName(section.id(), name.trim());
        return sections.findById(id).orElseThrow();
    }

    /**
     * Delete a section. If it still holds tasks, either {@code moveTo} (a different section in the same
     * project) must be given to relocate them first, or the call fails with 409.
     */
    @Transactional
    public void delete(UUID id, UUID moveTo) {
        Section section = get(id);
        boolean hasTasks = tasks.sectionHasTasks(id);
        if (hasTasks) {
            if (moveTo == null) {
                throw new ConflictException("Section is not empty; pass moveTo to relocate its tasks");
            }
            if (moveTo.equals(id)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "moveTo must be a different section");
            }
            requireSectionInProject(moveTo, section.projectId());
            tasks.reassignSection(id, moveTo);
        }
        sections.delete(id);
    }

    /** Reposition a section among its siblings using fractional ordering keys. */
    @Transactional
    public Section move(UUID id, UUID beforeSectionId, UUID afterSectionId) {
        Section moved = get(id);
        List<Section> ordered = sections.listByProject(moved.projectId());
        ordered.removeIf(s -> s.id().equals(id));

        String afterKey = null;
        String beforeKey = null;
        if (afterSectionId != null && beforeSectionId != null) {
            afterKey = keyOf(ordered, afterSectionId);
            beforeKey = keyOf(ordered, beforeSectionId);
        } else if (afterSectionId != null) {
            int idx = indexOf(ordered, afterSectionId);
            if (idx >= 0) {
                afterKey = ordered.get(idx).sortKey();
                beforeKey = idx + 1 < ordered.size() ? ordered.get(idx + 1).sortKey() : null;
            }
        } else if (beforeSectionId != null) {
            int idx = indexOf(ordered, beforeSectionId);
            if (idx >= 0) {
                beforeKey = ordered.get(idx).sortKey();
                afterKey = idx - 1 >= 0 ? ordered.get(idx - 1).sortKey() : null;
            }
        } else {
            afterKey = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).sortKey();
        }

        String newKey = Ordering.between(afterKey, beforeKey);
        if (Ordering.needsRebalance(newKey)) {
            newKey = rebalanceAndPlace(moved, ordered, afterKey, beforeKey);
        } else {
            sections.updateSortKey(id, newKey);
        }
        return sections.findById(id).orElseThrow();
    }

    private String rebalanceAndPlace(Section moved, List<Section> ordered, String afterKey, String beforeKey) {
        // Insert moved into the ordered list at the slot implied by the anchors, then reassign fresh keys.
        int insertAt = ordered.size();
        for (int i = 0; i < ordered.size(); i++) {
            if (beforeKey != null && ordered.get(i).sortKey().equals(beforeKey)) {
                insertAt = i;
                break;
            }
            if (afterKey != null && ordered.get(i).sortKey().equals(afterKey)) {
                insertAt = i + 1;
            }
        }
        List<UUID> ids = new java.util.ArrayList<>(ordered.stream().map(Section::id).toList());
        ids.add(insertAt, moved.id());
        List<String> keys = Ordering.rebalance(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            sections.updateSortKey(ids.get(i), keys.get(i));
        }
        return keys.get(insertAt);
    }

    private static int indexOf(List<Section> ordered, UUID id) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String keyOf(List<Section> ordered, UUID id) {
        int idx = indexOf(ordered, id);
        return idx >= 0 ? ordered.get(idx).sortKey() : null;
    }
}
