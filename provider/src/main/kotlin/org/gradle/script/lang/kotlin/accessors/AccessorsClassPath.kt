/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.script.lang.kotlin.accessors

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.script.lang.kotlin.cache.ScriptCache
import org.gradle.script.lang.kotlin.codegen.fileHeader
import org.gradle.script.lang.kotlin.support.CompilerClient
import org.gradle.script.lang.kotlin.support.compileToJar
import org.gradle.script.lang.kotlin.support.loggerFor
import org.gradle.script.lang.kotlin.support.serviceOf

import java.io.BufferedWriter
import java.io.File


fun accessorsClassPathFor(project: Project, classPath: ClassPath) =
    project.getOrCreateSingletonProperty {
        buildAccessorsClassPathFor(project, classPath)
            ?: AccessorsClassPath(ClassPath.EMPTY, ClassPath.EMPTY)
    }


data class AccessorsClassPath(val bin: ClassPath, val src: ClassPath)


private
fun buildAccessorsClassPathFor(project: Project, classPath: ClassPath) =
    configuredProjectSchemaOf(project)?.let { projectSchema ->
        val cacheDir =
            scriptCacheOf(project)
                .cacheDirFor(cacheKeyFor(projectSchema)) {
                    buildAccessorsJarFor(projectSchema, project.serviceOf<CompilerClient>(), classPath, outputDir = baseDir)
                }
        AccessorsClassPath(
            DefaultClassPath(accessorsJar(cacheDir)),
            DefaultClassPath(accessorsSourceDir(cacheDir)))
    }


private
fun configuredProjectSchemaOf(project: Project) =
    aotProjectSchemaOf(project) ?: jitProjectSchemaOf(project)


private
fun aotProjectSchemaOf(project: Project) =
    project
        .rootProject
        .getOrCreateSingletonProperty { multiProjectSchemaSnapshotOf(project) }
        .schema
        ?.let { it[project.path] }


private
fun jitProjectSchemaOf(project: Project) =
    project.takeIf(::enabledJitAccessors)?.let {
        require(classLoaderScopeOf(project).isLocked) {
            "project.classLoaderScope must be locked before querying the project schema"
        }
        schemaFor(project).withKotlinTypeStrings()
    }


private
fun scriptCacheOf(project: Project) = project.serviceOf<ScriptCache>()


private
fun buildAccessorsJarFor(projectSchema: ProjectSchema<String>, compilerClient: CompilerClient, classPath: ClassPath, outputDir: File) {
    val sourceFile = File(accessorsSourceDir(outputDir), "org/gradle/script/lang/kotlin/accessors.kt")
    writeAccessorsTo(sourceFile, projectSchema)
    compilerClient.compileToJar(accessorsJar(outputDir), listOf(sourceFile), logger, classPath.asFiles)
}

private
val logger by lazy { loggerFor<AccessorsClassPath>() }


private
fun accessorsSourceDir(baseDir: File) = File(baseDir, "src")


private
fun accessorsJar(baseDir: File) = File(baseDir, "gradle-script-kotlin-accessors.jar")


private
fun multiProjectSchemaSnapshotOf(project: Project) =
    MultiProjectSchemaSnapshot(
        projectSchemaSnapshotFileOf(project)?.let {
            loadMultiProjectSchemaFrom(it)
        })


private
data class MultiProjectSchemaSnapshot(val schema: Map<String, ProjectSchema<String>>?)


private
fun projectSchemaSnapshotFileOf(project: Project): File? =
    project
        .rootProject
        .file(PROJECT_SCHEMA_RESOURCE_PATH)
        .takeIf { it.isFile }


private
fun classLoaderScopeOf(project: Project) =
    (project as ProjectInternal).classLoaderScope


private
fun cacheKeyFor(projectSchema: ProjectSchema<String>): CacheKeySpec =
    CacheKeySpec.withPrefix("gradle-script-kotlin-accessors") + projectSchema.toCacheKeyString()


private
fun ProjectSchema<String>.toCacheKeyString(): String =
    (extensions.entries.asSequence() + conventions.entries.asSequence())
        .map { "${it.key}=${it.value}" }
        .sorted()
        .joinToString(separator = ":")


private
fun enabledJitAccessors(project: Project) =
    project.findProperty("org.gradle.script.lang.kotlin.accessors.auto") == "true"


private
fun writeAccessorsTo(outputFile: File, projectSchema: ProjectSchema<String>): File =
    outputFile.apply {
        parentFile.mkdirs()
        bufferedWriter().use { writer ->
            writeAccessorsFor(projectSchema, writer)
        }
    }


private
fun writeAccessorsFor(projectSchema: ProjectSchema<String>, writer: BufferedWriter) {
    writer.apply {
        write(fileHeader)
        newLine()
        appendln("import org.gradle.api.Project")
        appendln("import org.gradle.script.lang.kotlin.*")
        newLine()
        projectSchema.forEachAccessor {
            appendln(it)
        }
    }
}


/**
 * Location of the project schema snapshot taken by the _gskGenerateAccessors_ task relative to the root project.
 *
 * @see org.gradle.script.lang.kotlin.accessors.tasks.GenerateProjectSchema
 */
internal
const val PROJECT_SCHEMA_RESOURCE_PATH = "buildSrc/src/gradle-script-kotlin/resources/project-schema.json"
