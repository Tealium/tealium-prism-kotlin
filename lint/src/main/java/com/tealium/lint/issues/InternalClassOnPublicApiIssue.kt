@file:Suppress("UnstableApiUsage")

package com.tealium.lint.issues

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getContainingUClass

/**
 * This issue looks for references to any Tealium type declared under a `*.internal.*` package
 * (any module, not just `core`'s) and flags a lint warning in two cases:
 *  - the internal type is from a *different* module than the reference — always flagged, since a
 *    consuming module can't rely on another module's internal package staying stable/unobfuscated.
 *  - the internal type is from *this* module, but the reference itself sits on the public API
 *    (e.g. a public class's constructor param or a public method's return type) — flagged because
 *    it leaks the internal type to external consumers who can't see it's meant to be private.
 * A reference to a same-module internal type from other code whose containing class is itself in
 * an internal package is allowed. NOTE: this is narrower than "any non-public code" — a `private`
 * member of a class in a *non*-internal package is still flagged today, which is a known
 * imprecision (the check inspects only the containing class's package, not member visibility —
 * see [InternalClassOnPublicApiVisitor.isInInternalClass] before attempting a visibility-based
 * fix; the obvious approach is wrong).
 *
 * A few internal-package classes are deliberately kept un-obfuscated by explicit keep rules (e.g.
 * `ComponentDiscoveryService`, looked up by name from the manifest), and this check has no way to
 * know that — it would need amending to exempt them. Note that such an exemption would only mean
 * "this name survives R8", NOT "this class is safe to depend on": internal packages carry no
 * API-stability guarantee, and nothing in this check enforces that separate concern.
 */
object InternalClassOnPublicApiIssue {
    private const val ID = "InternalClassOnPublicApiIssue"

    private const val PRIORITY = 7

    private const val DESCRIPTION =
        "A class from the internal package is exposed on the public API. ** It may be obfuscated on build. **"

    private const val EXPLANATION = """
        Consider repackaging to a public package, or make a public facing replacement to expose instead.
    """

    private val CATEGORY = Category.CUSTOM_LINT_CHECKS

    private val SEVERITY = Severity.WARNING

    val ISSUE = Issue.create(
        ID,
        DESCRIPTION,
        EXPLANATION,
        CATEGORY,
        PRIORITY,
        SEVERITY,
        Implementation(
            InternalClassOnPublicApiDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )
    )

    class InternalClassOnPublicApiDetector : Detector(), Detector.UastScanner {
        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(
                UMethod::class.java,
                UVariable::class.java,
                UCallExpression::class.java
            )

        override fun createUastHandler(context: JavaContext): UElementHandler =
            InternalClassOnPublicApiVisitor(context)
    }
}

class InternalClassOnPublicApiVisitor(private val context: JavaContext) : UElementHandler() {
    private val internalPackageRegex = Regex("com\\.tealium[.a-zA-Z]*\\.internal\\..*")

    override fun visitVariable(node: UVariable) {
        // UVariable covers a lot of areas: e.g. Fields and Parameters, as well as local variable
        // types. A method-local variable can never be part of the public API regardless of its
        // visibility, so it's never a LocalLeak or worth flagging as one — skip it outright rather
        // than let it fall through to the (containing-class-only) isInInternalClass check, which
        // has no way to know this declaration itself is local to a method body.
        if (node is ULocalVariable) return

        val type = node.type
        val source = node.typeReference?.sourcePsi ?: return

        assertReferencingIssue(node, type, source, TypeDescription.Element)
    }

    override fun visitMethod(node: UMethod) {
        // Only checking the Method return type here.
        val type = node.returnType ?: return
        val source = node.returnTypeReference?.sourcePsi ?: return

        assertReferencingIssue(node, type, source, TypeDescription.ReturnType)
    }

    override fun visitCallExpression(node: UCallExpression) {
        // This should cover most use sites of variables.
        listOfNotNull(node.receiverType, node.returnType)
            .forEach { type ->
                val source = node.sourcePsi ?: return

                // Call expressions are ok when referencing an internal class as long as it's
                // within this module, so we only report the non-local classification here — never
                // the local-leak one (a call expression is not itself "the public API").
                type.internalTypeNames.forEach { bareTypeName ->
                    if (classify(node, bareTypeName) == InternalRef.NonLocal) {
                        reportReferenceToNonLocalInternalClass(
                            node,
                            bareTypeName,
                            source,
                            TypeDescription.Element
                        )
                    }
                }
            }
    }

    /**
     * Reports once per internal type name found in [reference] (not once per call site) — a type
     * like `Map<LocalInternal, ForeignInternal>` can contain both a same-module leak and a
     * different-module reference at once, and each deserves its own warning naming its own type.
     *
     *  - a name IS from this module, but [scope] is exposing it on the public API → local leak
     *  - a name IS NOT from this module → always flagged, regardless of [scope]
     */
    private fun assertReferencingIssue(
        scope: UElement,
        reference: PsiType,
        source: PsiElement,
        typeDescription: TypeDescription
    ) {
        reference.internalTypeNames.forEach { bareTypeName ->
            when (classify(scope, bareTypeName)) {
                InternalRef.LocalLeak ->
                    reportReferenceToLocalInternalClass(scope, bareTypeName, source, typeDescription)
                InternalRef.NonLocal ->
                    reportReferenceToNonLocalInternalClass(scope, bareTypeName, source, typeDescription)
                null -> Unit
            }
        }
    }

    private enum class InternalRef { LocalLeak, NonLocal }

    /**
     * Classifies a single internal type name as a local leak (from this module, but [scope] is on
     * the public API), a non-local reference (from a different module — always flagged), or
     * neither (from this module, referenced from non-public same-module code — allowed; or module
     * identity couldn't be determined at all, see [isFromThisModule]).
     */
    private fun classify(scope: UElement, bareTypeName: String): InternalRef? {
        val fromThisModule = isFromThisModule(bareTypeName) ?: return null
        return when {
            fromThisModule -> InternalRef.LocalLeak.takeUnless { scope.isInInternalClass }
            // "com.tealium." filters out references to `java.*` and `kotlin.*` etc.
            bareTypeName.startsWith("com.tealium.") -> InternalRef.NonLocal
            else -> null
        }
    }

    /**
     * True if [bareTypeName] is declared in this module, or `null` if this module's own package
     * couldn't be determined ([JavaContext.getProject]'s `package` is `@Nullable` — e.g. a project
     * with no manifest package). `null` here means "can't tell," and callers must treat that as
     * "skip classification," never as `false`: `bareTypeName.startsWith("$pkg.")` with a null [pkg]
     * would silently compare against the literal string `"null."`, which is always false and would
     * misclassify every same-module internal type as foreign — a confidently wrong answer instead
     * of an honest "unknown."
     *
     * [bareTypeName] must be a single unparameterized FQN (e.g. one entry from [internalTypeNames])
     * — passing a whole, possibly-parameterized [PsiType.canonicalText] here would let a generic
     * argument's own package get shadowed by its container's package (see [internalTypeNames]).
     *
     * Comparison is by package prefix, which relies on module namespaces not nesting one inside
     * another in the direction of a dependency. This repo's nested modules (`core` / `core.ktx`,
     * `jstransformer` / `jstransformer.rhino`) only ever depend child → parent, and a child's
     * namespace is longer than its parent's, so the prefix test is correct for every real pair
     * today. If a module ever depends on another whose namespace is nested inside its own, this
     * would misread it as local — `context.project.allLibraries` (the resolved dependency graph,
     * available in real builds though not under `TestLintTask`) would be the way to detect that.
     */
    private fun isFromThisModule(bareTypeName: String): Boolean? {
        val pkg = context.project.`package` ?: return null
        return bareTypeName == pkg || bareTypeName.startsWith("$pkg.")
    }

    /**
     * This type's own name plus every generic type argument's name, recursively.
     *
     * GAP: for an inner-class type `Outer<T>.Inner`, `T` lives on the outer qualifier — `Inner`'s
     * own [PsiClassType.parameters] are empty — so `T` is never visited. No SDK module declares
     * that shape today, so it's left unfixed; a fix would need the qualifier's arguments via
     * `resolveGenerics()`.
     */
    private val PsiType.referencedTypeNames: List<String>
        get() = when (this) {
            is PsiClassType -> listOf(rawType().canonicalText) + parameters.flatMap { it.referencedTypeNames }
            is PsiArrayType -> componentType.referencedTypeNames
            is PsiWildcardType -> bound?.referencedTypeNames.orEmpty()
            else -> listOf(canonicalText)
        }

    /**
     * [referencedTypeNames] filtered down to the ones that are actually from an `internal` package.
     *
     * [assertReferencingIssue] and [classify] both decide per name in this list, rather than on the
     * whole (possibly-parameterized) type as one string. That distinction matters twice over: a
     * generic argument's own package can otherwise be shadowed by its container's package — e.g.
     * `ModuleProxy<T>` is declared in `core`, so a naive whole-string check on
     * `ModuleProxy<MomentsIqModule>` would judge `MomentsIqModule` (from `momentsiq.internal`) by
     * `ModuleProxy`'s package instead of its own — and a single type can contain more than one
     * internal name at once (e.g. `Map<LocalInternal, ForeignInternal>`), each needing its own,
     * independent classification and report rather than one verdict for the whole type.
     */
    private val PsiType.internalTypeNames: List<String>
        get() = referencedTypeNames.filter { it.contains(internalPackageRegex) }

    private val UClass.isInternal: Boolean
        get() = qualifiedName?.contains(internalPackageRegex) ?: false

    /**
     * Whether this element sits inside a class in an internal package — the detector's
     * approximation of "this reference is not on the public API". Deliberately coarse: it
     * inspects only the containing class's package, never the declaration's own visibility.
     *
     * WARNING FOR ANYONE TIGHTENING THIS: the obvious fix — "skip if `UDeclaration.visibility ==
     * PRIVATE`" — is wrong. On Kotlin sources a property compiles to *two* UAST nodes (a backing
     * field visited via [UVariable], its accessor via [UMethod]), and `UVariable.visibility` on
     * the backing field is **always `PRIVATE`** regardless of the property's declared visibility
     * — so filtering `visitVariable` on it would suppress every Kotlin property, public ones
     * included. Kotlin `internal` is a further trap: it compiles to a JVM-public, name-mangled
     * accessor (e.g. `getFoo$module_name`), so `UastVisibility` can't identify it either; a real
     * fix must read visibility from the accessor and handle `internal` separately. (Verified by
     * probing `UastVisibility` on Kotlin sources under `TestLintTask`.)
     */
    private val UElement.isInInternalClass: Boolean
        get() = getContainingUClass()?.isInternal ?: false

    private val nonLocalMessageTemplate = """
            %s is referencing a class from an internal package in another module and may be obfuscated when built for release.
            Class name: `%s`
        """
    private val localMessageTemplate = """
            %s is on the public API, but is exposing a class that is internal to this module and may be obfuscated when built for release.
            Class name: `%s`
        """

    private fun reportReferenceToLocalInternalClass(
        scope: UElement,
        bareTypeName: String,
        element: PsiElement,
        type: TypeDescription
    ) {
        reportIssue(
            scope, element, localMessageTemplate, type.string, bareTypeName
        )
    }

    private fun reportReferenceToNonLocalInternalClass(
        scope: UElement,
        bareTypeName: String,
        element: PsiElement,
        type: TypeDescription
    ) {
        reportIssue(
            scope, element, nonLocalMessageTemplate, type.string, bareTypeName
        )
    }

    private fun reportIssue(
        node: UElement? = null,
        element: PsiElement,
        messageTemplate: String,
        vararg args: Any?
    ) {
        context.report(
            issue = InternalClassOnPublicApiIssue.ISSUE,
            scope = node,
            location = context.getLocation(element),
            message = messageTemplate.format(*args)
        )
    }

    enum class TypeDescription(val string: String) {
        ReturnType("Return type"),
        Element("Element")
    }
}
