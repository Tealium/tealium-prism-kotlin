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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod

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
        override fun getApplicableUastTypes(): List<Class<out UElement>>? =
            listOf(UMethod::class.java, UParameter::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            InternalClassOnPublicApiVisitor(context)
    }
}

class InternalClassOnPublicApiVisitor(private val context: JavaContext) : UElementHandler() {
    private val internalPackageRegex = Regex("com\\.tealium[.a-zA-Z]*\\.internal\\..*")
    private val internalPackage = "com.tealium.core.internal"

    override fun visitMethod(node: UMethod) {
        if (node.isInInternalClass) return

        assertReturnType(node)
    }

    override fun visitParameter(node: UParameter) {
        if (node.isInInternalClass) return

        val method = node.getContainingUMethod() ?: return

        assertParameterType(method, node)
    }

    private fun assertReturnType(method: UMethod) {
        val type = method.returnType ?: return

        if (type.isInternal) {
            val source = method.returnTypeReference?.sourcePsi ?: return
            reportReturnTypeIssue(method, source, type.canonicalText)
        }
    }

    private fun assertParameterType(method: UMethod, param: UParameter) {
        if (param.type.isInternal) {
            val psi = param.typeReference?.sourcePsi
            if (psi != null) {
                reportParameterIssue(psi, param.type.canonicalText)
            }
        }
    }

    private val UClass.isInternal: Boolean
        get() = qualifiedName?.startsWith(internalPackage) ?: false

    private val UMethod.isInInternalClass : Boolean
        get() = getContainingUClass()?.isInternal ?: false

    private val UParameter.isInInternalClass : Boolean
        get() = getContainingUClass()?.isInternal ?: false

    private val PsiType.isInternal: Boolean
        get() = canonicalText.contains(internalPackageRegex)

    private fun reportReturnTypeIssue(node: UMethod, element: PsiElement, type: String) {
        reportIssue(
            node, element, messageTemplate.format("Return type", type)
        )
    }

    private fun reportParameterIssue(param: PsiElement, type: String) {
        reportIssue(
            null, param, messageTemplate.format("Parameter", type)
        )
    }

    private val messageTemplate = """
            %s is referencing an class from `${internalPackage}.*` and may be obfuscated when built for release.
            
            Class name: `%s`
        """

    private fun reportIssue(node: UMethod? = null, element: PsiElement, message: String) {
        context.report(
            issue = InternalClassOnPublicApiIssue.ISSUE,
            scopeClass = node,
            location = context.getNameLocation(element),
            message = message
        )
    }
}
