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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getContainingUClass

/**
 * This issue looks for usage of classes from `com.tealium.core.internal` in any package that isn't
 * `com.tealium.core.internal` and flags a lint warning.
 * There may be some cases where a class in that package should remain un-obfuscated, and in which case
 * this issue implementation will need to be amended to consider that.
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

    /**
     * The default severity of the issue
     */
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
        // UVariable covers a lot of ares: e.g. Fields and Parameters, as well as local variable types
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

                // Call expressions are ok when referencing an internal class
                // as long as it's within this module, so we're only interested in references to
                // internal classes of other modules.
                if (type.isInternalAndNonLocal) {
                    reportReferenceToNonLocalInternalClass(
                        node,
                        type,
                        source,
                        TypeDescription.Element
                    )
                }
            }
    }

    /**
     * Checks for the following two cases:
     *  - The [reference] is an internal type and IS from this module, but the [scope] is exposing it on the public API
     *  - The [reference] is an internal type, but it IS NOT from this module
     *
     *  This check is useful for cases where the [scope] will form a part of the public API
     *  e.g. Method Return Types and parameters.
     */
    private fun assertReferencingIssue(
        scope: UElement,
        reference: PsiType,
        source: PsiElement,
        typeDescription: TypeDescription
    ) {
        if (isInternalAndLocal(scope, reference)) {
            reportReferenceToLocalInternalClass(scope, reference, source, typeDescription)
        } else if (reference.isInternalAndNonLocal) {
            reportReferenceToNonLocalInternalClass(scope, reference, source, typeDescription)
        }
    }

    private fun isInternalAndLocal(scope: UElement, type: PsiType): Boolean =
        type.isInternal && !scope.isInInternalClass && type.isFromThisModule

    private val PsiType.isInternalAndNonLocal: Boolean
        get() = isInternal
                && isTealiumOwned // needed to filter out references to `java.*` and `kotlin.*` etc.
                && !isFromThisModule

    private val PsiType.isTealiumOwned: Boolean
        get() = canonicalText.startsWith("com.tealium.")

    private val PsiType.isFromThisModule: Boolean
        get() = canonicalText.startsWith(context.project.`package`)

    private val UClass.isInternal: Boolean
        get() = qualifiedName?.contains(internalPackageRegex) ?: false

    private val UElement.isInInternalClass: Boolean
        get() = getContainingUClass()?.isInternal ?: false

    private val PsiType.isInternal: Boolean
        get() = canonicalText.contains(internalPackageRegex)

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
        referenceType: PsiType,
        element: PsiElement,
        type: TypeDescription
    ) {
        reportIssue(
            scope, element, localMessageTemplate, type.string, referenceType.canonicalText
        )
    }

    private fun reportReferenceToNonLocalInternalClass(
        scope: UElement,
        referenceType: PsiType,
        element: PsiElement,
        type: TypeDescription
    ) {
        reportIssue(
            scope, element, nonLocalMessageTemplate, type.string, referenceType.canonicalText
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
