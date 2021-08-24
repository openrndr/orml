import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.CompositeASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import javax.inject.Inject

abstract class MarkdownToJekyllTask constructor() : DefaultTask() {
    @get:Incremental
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @Inject
    abstract fun getWorkerExecutor(): WorkerExecutor

    @get:Input
    abstract val ignore: ListProperty<String>

    @get:Input
    abstract val titles: MapProperty<String, String>

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        inputChanges.getFileChanges(inputDir).forEach { change ->
            if (change.fileType == FileType.DIRECTORY) return@forEach
            if (change.file.name != "README.md") return@forEach

            val baseName = change.file.toPath().toList().takeLast(2).first().toString()
            if (baseName in ignore.get()) {
                return@forEach
            }
            val name = "${baseName}.markdown"
            val targetFile = outputDir.file(name).get().asFile
            if (change.changeType == ChangeType.REMOVED) {
                targetFile.delete()
            } else {
                val contents = change.file.readText()
                val title = titles.get()[baseName]
                val base = project.projectDir
                val processed = processMarkdownToJekyll(contents, title = title, permalink = baseName.toString(), sourcePath = change.file.relativeTo(base).path)
                targetFile.writeText(processed)
            }
        }
    }

    fun processMarkdownToJekyll(
        markdown: String,
        title: String? = null,
        permalink: String,
        sourcePath: String,

        ): String {
        val flavour = CommonMarkFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        val baseUrl = "https://github.com/openrndr/orml/raw/orml-0.3/"

        val output = StringBuilder()
        var title = title
        val stack = mutableListOf<String>()

        val transformers = mutableListOf<Pair<List<String>, (List<String>) -> String>>()
        val accumulators = mutableListOf<MutableList<String>>()

        parsedTree.visit { enter ->
            if (enter) {
                stack.add(this.type.name)
            }
            if (enter) {
                if (this is CompositeASTNode) {
                    if (stack.takeLast(2) == listOf("INLINE_LINK", "LINK_DESTINATION")) {
                        transformers.add(stack.map { it } to { texts ->
                            val text = texts.joinToString("")
                            when {
                                text.startsWith("https://") -> text
                                text.endsWith(".kt") -> "$baseUrl$permalink/$text"
                                text.endsWith(".png") -> "$baseUrl$permalink/$text"
                                text.endsWith(".jpg") -> "$baseUrl$permalink/$text"
                                text.endsWith(".gif") -> "$baseUrl$permalink/$text"
                                text.endsWith("README.md") -> "${text.dropLast(9)}"
                                else -> text
                            }
                        })
                        accumulators.add(mutableListOf())
                    } else if (stack.takeLast(2) == listOf("PARAGRAPH", "INLINE_LINK")) {
                        transformers.add(stack.map { it } to { texts ->
                            val text = texts.joinToString("")
                            if (text.contains("https://vimeo.com")) {
                                val r = Regex(".*https://vimeo.com/([0-9]+) .*")
                                val m = r.find(text) ?: error("no vimeo link found")
                                val id = m.groupValues[1]
                                "{% include vimeoPlayer.html id=$id %}"

                            } else {
                                text
                            }

                        })
                        accumulators.add(mutableListOf())
                    }
                } else if (this is LeafASTNode && accumulators.isEmpty()) {
                    if (stack.takeLast(4) == listOf("MARKDOWN_FILE", "ATX_1", "ATX_CONTENT", "TEXT")) {
                        val text = (getTextInNode(markdown))
                        if (text.matches(Regex("^#([^#]+)"))) {
                            val m = Regex("^#([^#]+)").find(text)
                            if (m != null) {
                                if (title == null) {
                                    title = m.groups[1]?.value
                                }
                            }
                        }
                        output.append(" ${this.getTextInNode(markdown)}")
                        output.append("\n{: .no_toc}\n")
                    } else {
                        output.append(this.getTextInNode(markdown))
                    }
                } else if (this is LeafASTNode && accumulators.isNotEmpty()) {
                    val accumulator = accumulators.last()
                    accumulator.add(this.getTextInNode(markdown).toString())
                }
            }
            if (!enter) {
                if (this is CompositeASTNode) {
                    if (transformers.isNotEmpty()) {
                        if (transformers.last().first == stack) {
                            val text = transformers.last().second(accumulators.last())
                            transformers.removeLast()
                            accumulators.removeLast()
                            if (accumulators.isNotEmpty()) {
                                accumulators.last().add(text)
                            } else {
                                output.append(text)
                            }
                        }
                    }
                }
                stack.removeLast()
            }
        }
        val header = """---
# This file is automatically generated from '$sourcePath' by the markdownToJekyll Gradle task. Do not edit this file.             
layout: page
title: ${title ?: permalink}
permalink: /$permalink/
parent: ORML
---
# Table of contents
{: .no_toc .text-delta}
1. TOC
{:toc}        
"""
        return header + output.toString()
    }

    fun ASTNode.visit(f: ASTNode.(enter: Boolean) -> Unit) {
        this.f(true)
        for (child in this.children) {
            child.visit(f)
        }
        this.f(false)
    }

}
