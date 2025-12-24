package com.tealium.gradle.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.gradle.api.logging.Logger
import java.io.File
import java.io.IOException

object GitHelper {

    fun isModified(
        rootDir: File,
        projectName: String,
        baseBranch: String,
        incomingBranch: String,
        logger: Logger
    ): Boolean {
        if (baseBranch.isEmpty()) {
            logger.warn("No base branch found. Was GITHUB_BASE_REF set?")
            return false
        }
        if (incomingBranch.isEmpty()) {
            logger.warn("No incoming branch found; using HEAD. Was GITHUB_HEAD_REF set?")
            return false
        }

        logger.lifecycle("Comparing ($incomingBranch) with base ($baseBranch)")

        val repo = Git.open(rootDir)

        val old = prepareTreeParser(repo.repository, baseBranch)
        val new = prepareTreeParser(repo.repository, incomingBranch)

        val diff = repo.diff()
            .setNewTree(new)
            .setOldTree(old)
            .setPathFilter(PathFilter.create("$projectName/"))
            .call()

        val changePaths = diff.flatMap(::getChangedPaths)
        changePaths.forEach {
            logger.info("Changed: $it")
        }

        return if (changePaths.isNotEmpty()) {
            logger.lifecycle("Found changes for $projectName")
            true
        } else {
            logger.lifecycle("No changes for $projectName; skipping.")
            false
        }
    }

    private fun getChangedPaths(change: DiffEntry): List<String> = when (change.changeType) {
        DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.MODIFY, DiffEntry.ChangeType.COPY -> {
            listOf(change.newPath)
        }

        DiffEntry.ChangeType.DELETE -> {
            listOf(change.oldPath)
        }

        DiffEntry.ChangeType.RENAME -> {
            listOf(change.newPath, change.oldPath)
        }

        else -> emptyList()
    }

    @Throws(IOException::class)
    private fun prepareTreeParser(repository: Repository, ref: String): AbstractTreeIterator {
        val head: Ref = repository.findRef(ref)
            ?: throw IllegalStateException("Failed to resolve ref of $ref")

        RevWalk(repository).use { walk ->
            val commit: RevCommit =
                walk.parseCommit(head.objectId)
            val tree: RevTree =
                walk.parseTree(commit.tree.id)
            val treeParser =
                CanonicalTreeParser()
            repository.newObjectReader().use { reader -> treeParser.reset(reader, tree.id) }
            walk.dispose()
            return treeParser
        }
    }
}