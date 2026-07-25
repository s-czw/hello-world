package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.cairn.api.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the invariant established by migration {@code V4__sort_key_c_collation.sql}: every
 * fractional-ordering key column must compare BYTE-WISE, not linguistically.
 *
 * <p>Base62 keys ("V", "l", "t") only order correctly under {@code C} collation. Under a linguistic
 * collation (en_US.utf8, most ICU locales) case becomes a tie-breaker instead of a primary weight, so
 * 'l' &lt; 't' &lt; 'V' and every ordered list silently rotates. That is environment-dependent: it passes
 * on a C.UTF-8 cluster and fails on an en_US.utf8 one, which is exactly how it reached CI unnoticed.
 *
 * <p>These assertions are deliberately about schema metadata rather than row order, so they fail on ANY
 * database locale — including a byte-wise one where a regression would otherwise look fine.
 */
class SortKeyCollationIntegrationTest extends IntegrationTestBase {

    @Test
    void everyOrderingKeyColumnIsCCollated() {
        List<String> offenders = jdbc.queryForList(
                """
                select table_name || '.' || column_name
                         || ' (collation=' || coalesce(collation_name, '<database default>') || ')'
                  from information_schema.columns
                 where table_schema = 'public'
                   and column_name like '%sort_key%'
                   and coalesce(collation_name, '') <> 'C'
                 order by 1
                """,
                String.class);

        assertThat(offenders)
                .as(
                        "ordering-key columns must be declared COLLATE \"C\" so row order is identical on "
                            + "every database locale — add it to the column (see V4__sort_key_c_collation.sql)")
                .isEmpty();
    }

    @Test
    void allExpectedOrderingColumnsArePresentAndChecked() {
        // Belt-and-braces: if a column is renamed away from the '%sort_key%' pattern, the test above would
        // silently pass while checking nothing. Pin the known set.
        List<String> columns = jdbc.queryForList(
                """
                select table_name || '.' || column_name
                  from information_schema.columns
                 where table_schema = 'public' and column_name like '%sort_key%'
                 order by 1
                """,
                String.class);

        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "portfolio_projects.sort_key",
                        "portfolios.sort_key",
                        "sections.sort_key",
                        "tasks.sort_key",
                        "tasks.subtask_sort_key");
    }
}
