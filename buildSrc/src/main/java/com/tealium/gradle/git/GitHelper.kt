package com.tealium.gradle.git

import com.tealium.gradle.getPropertyOrEnvironmentVariable
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
import org.gradle.api.Project
import java.io.IOException

object GitHelper {

    fun isModified(
        project: Project,
        baseBranch: String? = null,
        incomingBranch: String? = null
    ): Boolean {
        val incoming = incomingBranch
            ?: project.getPropertyOrEnvironmentVariable("GITHUB_HEAD_REF", "")
        if (incoming.isEmpty()) {
            throw IllegalStateException("No incoming branch found; using HEAD. Was GITHUB_HEAD_REF set?")
        }

        val base = baseBranch
            ?: project.getPropertyOrEnvironmentVariable("GITHUB_BASE_REF", "")
        if (base.isEmpty()) {
            throw IllegalStateException("No base branch found. Was GITHUB_BASE_REF set?")
        }

        val repo = Git.open(project.rootDir)
        project.logger.lifecycle("Comparing ($incoming) with base ($base)")

        val old = prepareTreeParser(repo.repository, base)
        val new = prepareTreeParser(repo.repository, incoming)

        val diff = repo.diff()
            .setNewTree(new)
            .setOldTree(old)
            .setPathFilter(PathFilter.create("${project.name}/"))
            .call()

        val changePaths = diff.flatMap(::getChangedPaths)
        changePaths.forEach {
            project.logger.info("Changed: $it")
        }

        return if (changePaths.isNotEmpty()) {
            project.logger.lifecycle("Found changes for ${project.name}")
            true
        } else {
            project.logger.lifecycle("No changes for ${project.name}; skipping.")
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