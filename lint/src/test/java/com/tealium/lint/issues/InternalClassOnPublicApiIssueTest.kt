package com.tealium.lint.issues

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.TextFormat
import org.junit.Ignore
import org.junit.Test

class InternalClassOnPublicApiIssueTest {

    private val moduleProxy = kotlin(
        """
        package com.tealium.prism.core.api.modules
        interface Module
        interface ModuleProxy<T : Module>
        """
    ).indented()

    // allowDuplicates(): a public `val` reports twice — once for the property (visitVariable),
    // once for its synthesized getter's return type (visitMethod). Pre-existing; see the detector.
    //
    // textFormat(RAW): assert against the raw message so expected substrings match the detector's
    // templates verbatim, backticks included. TEXT would consume them as monospace markup.
    private fun lintWith(manifestFile: TestFile, vararg files: TestFile) =
        lint()
            .files(manifestFile, moduleProxy, *files)
            .issues(InternalClassOnPublicApiIssue.ISSUE)
            .allowDuplicates()
            .textFormat(TextFormat.RAW)
            .run()

    /** [lintWith] with this module's package set to `momentsiq` — the default for most tests. */
    private fun lintOn(vararg files: TestFile) =
        lintWith(manifest().pkg("com.tealium.prism.momentsiq"), *files)

    /** Same-module internal used as a generic argument to a foreign-module type. */
    @Test
    fun `internal type as generic argument from same module is allowed`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                import com.tealium.prism.core.api.modules.ModuleProxy
                internal class MomentsIqModule : com.tealium.prism.core.api.modules.Module
                internal class MomentsIqWrapper(private val proxy: ModuleProxy<MomentsIqModule>)
                """
            ).indented()
        ).expectClean()
    }

    /** Internal type from ANOTHER module used as a generic argument — must still be reported. */
    @Test
    fun `internal type as generic argument from another module is reported`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.core.internal
                class CoreInternalModule : com.tealium.prism.core.api.modules.Module
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                import com.tealium.prism.core.api.modules.ModuleProxy
                import com.tealium.prism.core.internal.CoreInternalModule
                internal class SomeWrapper(private val proxy: ModuleProxy<CoreInternalModule>)
                """
            ).indented()
        ).expectWarningCount(1)
            // The whole point of this test: confirm it's the NON-LOCAL branch, not just any
            // warning — the bare warning count alone wouldn't catch a local/non-local mix-up.
            .expectContains("another module")
    }

    /** Mirrors core's ReferenceContainer: a public class exposing a same-module internal type. */
    @Test
    fun `public api exposing internal type from this module is reported`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                class ReferenceType
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                import com.tealium.prism.momentsiq.internal.ReferenceType
                class ReferenceContainer(private val referenceType: ReferenceType)
                """
            ).indented()
        ).expectWarningCount(1)
            // Confirm it's the LOCAL branch, not the non-local one — this type genuinely is from
            // this module, just leaking onto the public API.
            .expectContains("internal to this module")
    }

    /** Non-generic same-module internal reference, from within an internal class — allowed. */
    @Test
    fun `internal class referencing same-module internal type is allowed`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                class InternalHelper
                class InternalUser(private val helper: InternalHelper)
                """
            ).indented()
        ).expectClean()
    }

    /** A public API exposing a same-module internal type via a generic argument — must be caught. */
    @Test
    fun `public api exposing internal type from this module via generic argument is reported`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                class SameModuleInternal
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                import com.tealium.prism.momentsiq.internal.SameModuleInternal
                class PublicHolder(val items: List<SameModuleInternal>)
                """
            ).indented()
            // 2 warnings: the property itself ("Element") plus its synthesized public getter
            // ("Return type") — the pre-existing double-reporting quirk (see lintOn() comment).
            // A `private val` here would report once, like the other tests above.
        ).expectWarningCount(2)
    }

    /**
     * A type containing both a same-module and a foreign-module internal name must report both,
     * each naming its own type — not one verdict for the whole type.
     */
    @Test
    fun `type mixing local and foreign internal names reports both a local and a non-local warning`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.core.internal
                class CoreInternal
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                class MiqInternal
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                import com.tealium.prism.momentsiq.internal.MiqInternal
                import com.tealium.prism.core.internal.CoreInternal
                class PublicThing(private val map: Map<MiqInternal, CoreInternal>)
                """
            ).indented()
        ).expectWarningCount(2)
            .expectContains("internal to this module")
            .expectContains("another module")
            .expectContains("Class name: `com.tealium.prism.momentsiq.internal.MiqInternal`")
            .expectContains("Class name: `com.tealium.prism.core.internal.CoreInternal`")
    }

    /**
     * A nested child module referencing its parent's internal type (real shape:
     * `jstransformer.rhino` -> `jstransformer`) is non-local. Package-prefix matching only misfires
     * the other way — a parent reaching into a child's namespace — which needs a parent -> child
     * dependency that can't compile. Verified across every module-namespace pair in this repo.
     */
    @Test
    fun `child module referencing parent's internal type is correctly reported as non-local`() {
        lintWith(
            manifest().pkg("com.tealium.prism.jstransformer.rhino"),
            kotlin(
                """
                package com.tealium.prism.jstransformer.internal
                class JavaScriptTransformer
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.jstransformer.rhino.api
                import com.tealium.prism.jstransformer.internal.JavaScriptTransformer
                class Holder(private val transformer: JavaScriptTransformer)
                """
            ).indented()
        )
            .expectWarningCount(1)
            .expectContains("another module")
    }

    /**
     * A method-local variable can never be public API, so it must never be reported — the
     * containing-class-only check can't tell it apart from a field, hence `visitVariable`'s guard.
     */
    @Test
    fun `local variable referencing same-module internal type is allowed`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                class Thing
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                import com.tealium.prism.momentsiq.internal.Thing

                class Holder {
                    fun use() {
                        val tmp: Thing = Thing()
                    }
                }
                """
            ).indented()
        ).expectClean()
    }

    /**
     * When this module's package can't be determined, classification must be skipped rather than
     * guessing — a bare `startsWith(pkg)` would compare against the literal `"null."` and call every
     * same-module type foreign. Needs a manifest with no `package` attribute: omitting the manifest
     * entirely makes TestLintTask synthesize `lint.test.pkg`, which never exercises the null path.
     */
    @Test
    fun `same-module reference is not misclassified when project package is unknown`() {
        lintWith(
            xml("AndroidManifest.xml", "<manifest/>"),
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                class MiqInternal
                class MiqUser(private val x: MiqInternal)
                """
            ).indented()
        )
            .expectClean()
    }

    /**
     * KNOWN GAP (see `referencedTypeNames`'s KDoc in `InternalClassOnPublicApiVisitor`): for
     * `Outer<T>.Inner`, `T` lives on the outer qualifier, so it is never visited and the internal
     * type escapes detection. Kept as executable proof of the gap; un-`@Ignore` once
     * `referencedTypeNames` walks the qualifier's arguments. The control test below proves
     * detection works on this shape otherwise.
     */
    @Ignore("KNOWN GAP: Outer<T>.Inner's type argument T is never visited — see referencedTypeNames's KDoc")
    @Test
    fun `public api exposing foreign-module internal type via inner-class qualifier is reported`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.core.internal
                class CoreInternal
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                class Outer<T> {
                    inner class Inner
                }
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                import com.tealium.prism.core.internal.CoreInternal
                class PublicHolder(val x: Outer<CoreInternal>.Inner)
                """
            ).indented()
        ).expectWarningCount(1)
            .expectContains("another module")
    }

    /**
     * Isolation control for the ignored test above: the same type as a direct generic argument (no
     * `.Inner`) IS caught, proving the qualifier is what suppresses detection — not the fixture.
     */
    @Test
    fun `public api exposing foreign-module internal type as a direct generic argument is reported`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.core.internal
                class CoreInternal
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                class Outer<T>
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                import com.tealium.prism.core.internal.CoreInternal
                class PublicHolder(val x: Outer<CoreInternal>)
                """
            ).indented()
        ).expectWarningCount(2)
            .expectContains("another module")
    }

    /**
     * KNOWN LIMITATION (see `isInInternalClass`'s KDoc in `InternalClassOnPublicApiVisitor`): only
     * the containing class's package is checked, never the member's own visibility, so a `private`
     * field in a non-internal class is wrongly flagged. Kept as executable proof; un-`@Ignore` once
     * visibility is read from the accessor — NOT via `UDeclaration.visibility == PRIVATE`, which
     * reports every Kotlin property's backing field as private. See that KDoc before attempting it.
     */
    @Ignore("KNOWN LIMITATION: member visibility isn't inspected — read isInInternalClass's KDoc first")
    @Test
    fun `private field referencing same-module internal type in a non-internal class is allowed`() {
        lintOn(
            kotlin(
                """
                package com.tealium.prism.momentsiq.internal
                class Thing
                """
            ).indented(),
            kotlin(
                """
                package com.tealium.prism.momentsiq.api
                import com.tealium.prism.momentsiq.internal.Thing
                class Holder {
                    private val thing: Thing = Thing()
                }
                """
            ).indented()
        ).expectClean()
    }
}
