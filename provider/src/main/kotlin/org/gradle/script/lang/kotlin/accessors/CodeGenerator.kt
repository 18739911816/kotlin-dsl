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

internal
fun ProjectSchema<String>.forEachAccessor(action: (String) -> Unit) {
    extensions.forEach { (name, type) ->
        extensionAccessorFor(name, type)?.let(action)
    }
    conventions.forEach { (name, type) ->
        if (name !in extensions) {
            conventionAccessorFor(name, type)?.let(action)
        }
    }
}


private
fun extensionAccessorFor(name: String, type: String): String? =
    if (isLegalExtensionName(name))
        """
            /**
             * Retrieves the [$name][$type] project extension.
             */
            val Project.`$name`: $type get() =
                extensions.getByName("$name") as $type

            /**
             * Configures the [$name][$type] project extension.
             */
            fun Project.`$name`(configure: $type.() -> Unit): Unit =
                extensions.configure("$name", configure)

        """.replaceIndent()
    else null


private
fun conventionAccessorFor(name: String, type: String): String? =
    if (isLegalExtensionName(name))
        """
            /**
             * Retrieves the [$name][$type] project convention.
             */
            val Project.`$name`: $type get() =
                convention.getPluginByName<$type>("$name")

            /**
             * Configures the [$name][$type] project convention.
             */
            fun Project.`$name`(configure: $type.() -> Unit): Unit =
                configure(`$name`)

        """.replaceIndent()
    else null


internal
fun isLegalExtensionName(name: String): Boolean =
    isKotlinIdentifier("`$name`")
        && name.indexOfAny(invalidNameChars) < 0


private
val invalidNameChars = charArrayOf('.', '/', '\\')

// captures standard and quoted (`...`) identifiers, captures identifier in the groups 1 (regular) or 2 (quoted)
private val KOTLIN_IDENTIFIER_RE = """^([\p{Alpha}_]\w*)$|`((?:[^`\\]|\\.)*)`$""".toRegex()
private val KOTLIN_RESERVED_IDENTIFIERS = hashSetOf(
    "_", "__", "___", "typealias", "interface", "continue", "package", "return", "object", "while", "break", "class"
    , "throw", "false", "super", "typeof", "when", "true", "this", "null", "else", "try", "val", "var", "fun", "for"
    , "is", "in", "if", "do", "as")

private
fun isKotlinIdentifier(candidate: String): Boolean = KOTLIN_IDENTIFIER_RE.matches(candidate) && (candidate !in KOTLIN_RESERVED_IDENTIFIERS)


