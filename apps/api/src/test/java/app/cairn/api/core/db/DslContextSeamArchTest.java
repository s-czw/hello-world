package app.cairn.api.core.db;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the org-filter seam (D-028): only classes in {@code app.cairn.api.core.db} may declare a
 * field or a code-unit (constructor/method) parameter of type {@link org.jooq.DSLContext}. Everyone
 * else must go through {@link OrgScopedDsl}, so no query can bypass the {@code organization_id} filter.
 *
 * <p>Generated jOOQ classes ({@code app.cairn.api.jooq}) are excluded from the import.
 */
@AnalyzeClasses(
        packages = "app.cairn.api",
        importOptions = {ImportOption.DoNotIncludeTests.class, DslContextSeamArchTest.ExcludeGeneratedJooq.class})
class DslContextSeamArchTest {

    private static final String DSL_CONTEXT = "org.jooq.DSLContext";
    private static final String CORE_DB = "app.cairn.api.core.db..";

    private static final DescribedPredicate<JavaCodeUnit> HAS_DSL_CONTEXT_PARAMETER =
            new DescribedPredicate<>("has an " + DSL_CONTEXT + " parameter") {
                @Override
                public boolean test(JavaCodeUnit codeUnit) {
                    return codeUnit.getRawParameterTypes().stream()
                            .anyMatch(t -> t.getName().equals(DSL_CONTEXT));
                }
            };

    @ArchTest
    static final ArchRule dslContextFieldsOnlyInCoreDb =
            fields()
                    .that()
                    .haveRawType(DSL_CONTEXT)
                    .should()
                    .beDeclaredInClassesThat()
                    .resideInAPackage(CORE_DB)
                    .because("only core.db may hold a DSLContext; others use OrgScopedDsl (D-028 seam)");

    @ArchTest
    static final ArchRule dslContextParametersOnlyInCoreDb =
            codeUnits()
                    .that(HAS_DSL_CONTEXT_PARAMETER)
                    .should()
                    .beDeclaredInClassesThat()
                    .resideInAPackage(CORE_DB)
                    .because("only core.db may inject/accept a DSLContext; others use OrgScopedDsl (D-028 seam)");

    /** Keep generated jOOQ code out of the seam analysis. */
    static final class ExcludeGeneratedJooq implements ImportOption {
        @Override
        public boolean includes(com.tngtech.archunit.core.importer.Location location) {
            return !location.contains("/app/cairn/api/jooq/");
        }
    }
}
