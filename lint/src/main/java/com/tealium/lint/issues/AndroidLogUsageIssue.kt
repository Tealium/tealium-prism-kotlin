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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod

/**
 * This issue looks for usage of the Android built in logger. Tealium classes should be using our
 * own logger, so that it can be controlled by the remote configuration changes.
 *
 * And method use on the `android.util.Log` object will be flagged as a lint warning if it happens
 * outside of the Tealium logger implementation.
 */
object AndroidLogUsageIssue {
    private const val ID = "AndroidLogUsageIssue"

    private const val PRIORITY = 5

    private const val DESCRIPTION =
        "Android log is in use, but all Tealium logging should be via it's own logger."

    private const val EXPLANATION = """
        Tealium logging has it's own log restrictions that are customisable by the developer and can be remotely updated at run time. 
        Using Android's logging directly circumvents this and will therefore not be controllable by Tealium's logger.
        
        Please switch to using the logger provided by the `TealiumContext`
    """

    private val CATEGORY = Category.CUSTOM_LINT_CHECKS

    private val SEVERITY = Severity.INFORMATIONAL

    val ISSUE = Issue.create(
        ID,
        DESCRIPTION,
        EXPLANATION,
        CATEGORY,
        PRIORITY,
        SEVERITY,
        Implementation(
            AndroidLogUsageDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )
    )

    class AndroidLogUsageDetector : Detector(), Detector.UastScanner {
        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UCallExpression::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            AndroidLogUsageVisitor(context)
    }
}

class AndroidLogUsageVisitor(private val context: JavaContext) : UElementHandler() {
    private val androidLogger = "android.util.Log"

    override fun visitCallExpression(node: UCallExpression) {
        if (node.getContainingUClass().isTealiumLogger) return

        assertNotAndroidLog(node)
    }

    private fun assertNotAndroidLog(expression: UCallExpression) {
        if (expression.isAndroidLog) {
            reportIssue(expression.getContainingUMethod()!!, expression.sourcePsi!!)
        }
    }

    private val UClass?.isTealiumLogger: Boolean
        get() = (this?.qualifiedName ?: "") == "com.tealium.prism.core.internal.LoggerImpl"

    private val UCallExpression.isAndroidLog: Boolean
        get() = (resolve()?.containingClass?.qualifiedName ?: "") == androidLogger

    private fun reportIssue(node: UMethod? = null, element: PsiElement) {
        context.report(
            issue = AndroidLogUsageIssue.ISSUE,
            scopeClass = node,
            location = context.getNameLocation(element),
            message = """
                Usage of `$androidLogger` is not allowed in Tealium code. 
                
                Please use the logger provided on the `TealiumContext`
            """.trimIndent()
        )
    }
}

