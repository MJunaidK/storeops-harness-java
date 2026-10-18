package com.storeops;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AnalyzeClasses(packages = "com.storeops", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final List<String> MODULES = List.of("activities", "programmes", "staff", "alerts", "reports");
    private static final Pattern MODULE_PACKAGE = Pattern.compile("com\\.storeops\\.(activities|programmes|staff|alerts|reports)\\..*");
    private static final Set<String> FORBIDDEN_EXCEPTIONS = Set.of(
        "java.lang.RuntimeException",
        "java.lang.IllegalArgumentException",
        "java.lang.IllegalStateException",
        "java.lang.Exception");

    /** Rule 1: nothing outside module X may access com.storeops.X.repository. */
    @ArchTest
    void repositoriesAreOnlyAccessedFromTheirOwnModule(JavaClasses classes) {
        for (String module : MODULES) {
            noClasses().that().resideOutsideOfPackage("com.storeops." + module + "..")
                .should().dependOnClassesThat().resideInAPackage("com.storeops." + module + ".repository..")
                .because("module " + module + " owns its repository")
                .check(classes);
        }
    }

    /** Rule 2. */
    @ArchTest
    static final ArchRule CONTROLLERS_NEVER_ACCESS_REPOSITORIES = noClasses().that().resideInAPackage("..controller..")
        .should().dependOnClassesThat().resideInAPackage("..repository..");

    /** Rule 3: modules form no dependency cycles (shared infrastructure is not a module slice). */
    @ArchTest
    static final ArchRule MODULES_ARE_FREE_OF_CYCLES = slices().assignedFrom(moduleSlices()).should().beFreeOfCycles();

    /** Rule 4: controllers and services throw only AppError subclasses. */
    @ArchTest
    static final ArchRule NO_GENERIC_EXCEPTIONS_IN_CONTROLLERS_AND_SERVICES = noClasses()
        .that().resideInAnyPackage("..controller..", "..service..")
        .should().callConstructorWhere(constructsForbiddenException())
        .because("only AppError subclasses may be thrown");

    /** Rule 5. */
    @ArchTest
    static final ArchRule ACTIVITIES_AND_PROGRAMMES_DO_NOT_DEPEND_ON_ALERTS_OR_REPORTS = noClasses()
        .that().resideInAnyPackage("com.storeops.activities..", "com.storeops.programmes..")
        .should().dependOnClassesThat().resideInAnyPackage("com.storeops.alerts..", "com.storeops.reports..");

    /** Rule 6: reports reads other modules only through their service and model packages. */
    @ArchTest
    static final ArchRule REPORTS_DEPEND_ON_OTHER_MODULES_ONLY_THROUGH_SERVICE_AND_MODEL = noClasses()
        .that().resideInAPackage("com.storeops.reports..")
        .should().dependOnClassesThat(
            resideInAnyPackage("com.storeops.activities..", "com.storeops.programmes..", "com.storeops.staff..",
                "com.storeops.alerts..")
                .and(not(resideInAnyPackage("..service..", "..model.."))));

    private static SliceAssignment moduleSlices() {
        return new SliceAssignment() {
            @Override
            public String getDescription() {
                return "business modules";
            }

            @Override
            public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                Matcher matcher = MODULE_PACKAGE.matcher(javaClass.getName());
                return matcher.matches() ? SliceIdentifier.of(matcher.group(1)) : SliceIdentifier.ignore();
            }
        };
    }

    private static DescribedPredicate<JavaConstructorCall> constructsForbiddenException() {
        return new DescribedPredicate<>("construct RuntimeException, IllegalArgumentException, IllegalStateException or Exception") {
            @Override
            public boolean test(JavaConstructorCall call) {
                return FORBIDDEN_EXCEPTIONS.contains(call.getTargetOwner().getFullName());
            }
        };
    }
}
