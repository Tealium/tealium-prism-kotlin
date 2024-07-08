package com.tealium.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.tealium.lint.issues.AndroidLogUsageIssue
import com.tealium.lint.issues.InternalClassOnPublicApiIssue

/**
 * This is the lint registry referenced in the `build.gradle` and is just a lookup for all issues
 * that we are registering for lint checks.
 */
@Suppress("UnstableApiUsage")
class TealiumLintRegistry : IssueRegistry() {
    override val issues =
        listOf(
            InternalClassOnPublicApiIssue.ISSUE,
            AndroidLogUsageIssue.ISSUE
        )

    override val api: Int = CURRENT_API

    override val minApi: Int = 8
}