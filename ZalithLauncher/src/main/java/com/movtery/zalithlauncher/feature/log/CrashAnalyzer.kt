
package com.movtery.zalithlauncher.feature.log

/**
 * Offline Minecraft crash analyzer.
 * Uses local pattern matching. No AI, internet, or external API required.
 */
object CrashAnalyzer {

    data class Finding(
        val title: String,
        val explanation: String,
        val suggestedFix: String,
        val matchedText: String
    )

    data class Report(
        val findings: List<Finding>,
        val summary: String,
        val likelyCause: String?
    ) {
        val hasFindings: Boolean
            get() = findings.isNotEmpty()
    }

    private data class Rule(
        val markers: List<String>,
        val title: String,
        val explanation: String,
        val fix: String
    )

    private val rules = listOf(

        // MOD DEPENDENCIES AND LOADING

        Rule(
            listOf(
                "ModResolutionException",
                "Could not find required mod",
                "depends on",
                "requires version",
                "Missing mandatory dependencies"
            ),
            "Missing mod dependency",
            "A mod requires another mod or a specific version that is not installed.",
            "Install the missing dependency and check its required version."
        ),

        Rule(
            listOf(
                "Incompatible mod set",
                "Incompatible mod set!",
                "Incompatible mods found",
                "Mod resolution encountered an incompatible mod set"
            ),
            "Incompatible mods",
            "One or more installed mods cannot work together.",
            "Check the listed incompatible mods. Update, downgrade, or remove the conflicting mod."
        ),

        Rule(
            listOf(
                "DuplicateModsFoundException",
                "Duplicate mods",
                "Found duplicate mods"
            ),
            "Duplicate mod detected",
            "Multiple copies or versions of a mod were detected.",
            "Remove duplicate JAR files and keep only the intended version."
        ),

        Rule(
            listOf(
                "ModLoadingException",
                "ModLoadingError",
                "Failed to load mod",
                "Failed to load mods"
            ),
            "Mod loading failure",
            "A mod failed during its loading process.",
            "Inspect the exception immediately after the mod name. Check compatibility and dependencies."
        ),

        Rule(
            listOf(
                "EntrypointException",
                "Exception during entrypoint",
                "Failed to load entrypoint",
                "Could not execute entrypoint"
            ),
            "Fabric entrypoint failure",
            "A Fabric mod failed while initializing its entrypoint.",
            "Update the affected mod and its dependencies. Check whether it supports your Minecraft and Fabric Loader versions."
        ),

        Rule(
            listOf(
                "ModLoadingStage",
                "fml.modloading",
                "Failed to complete lifecycle event"
            ),
            "Forge or NeoForge loading failure",
            "A mod failed during a Forge or NeoForge loading stage.",
            "Check the mod loading error and the first underlying exception. Test without recently added mods."
        ),

        // JAVA CLASS AND METHOD ERRORS

        Rule(
            listOf("NoClassDefFoundError", "ClassNotFoundException"),
            "Missing Java class",
            "A required Java class could not be found.",
            "Install the missing library or dependency required by the affected mod."
        ),

        Rule(
            listOf("NoSuchMethodError"),
            "Missing Java method",
            "A mod is trying to call a method that does not exist in the installed version of a library or another mod.",
            "Update or downgrade the affected mods together so their versions are compatible."
        ),

        Rule(
            listOf("NoSuchFieldError"),
            "Missing Java field",
            "A mod expects a field that is unavailable in the installed game or library version.",
            "Check for mods built for a different Minecraft version. Update or remove the incompatible mod."
        ),

        Rule(
            listOf("AbstractMethodError"),
            "Incompatible Java implementation",
            "A mod or library does not implement a method expected by another component.",
            "Check library and mod version compatibility."
        ),

        Rule(
            listOf("IncompatibleClassChangeError"),
            "Java class compatibility error",
            "A class changed in a way that is incompatible with another mod.",
            "Update or downgrade the conflicting mods and their shared libraries."
        ),

        Rule(
            listOf("VerifyError"),
            "Java bytecode verification failure",
            "The Java runtime rejected invalid or incompatible class bytecode.",
            "Check recently installed mods, transformations, and Java compatibility."
        ),

        Rule(
            listOf("UnsupportedClassVersionError"),
            "Java version mismatch",
            "A mod was compiled for a newer Java version than the active runtime.",
            "Select the Java runtime required by your Minecraft version."
        ),

        Rule(
            listOf("ClassFormatError", "ClassFormatException"),
            "Invalid Java class file",
            "A class file may be corrupted or incompatible.",
            "Re-download the affected mod or library and check its compatibility."
        ),

        Rule(
            listOf("LinkageError"),
            "Java library linkage error",
            "Two components may be using incompatible versions of the same class or library.",
            "Check for duplicate libraries and incompatible mod versions."
        ),

        // MIXIN AND TRANSFORMATION ERRORS

        Rule(
            listOf(
                "MixinApplyError",
                "Mixin apply failed",
                "Critical injection failure"
            ),
            "Mixin application failure",
            "A mod failed to modify the expected game code.",
            "Update the affected mod and check for conflicts with other mods."
        ),

        Rule(
            listOf(
                "InvalidMixinException",
                "InvalidInjectionException",
                "InjectionError"
            ),
            "Mixin injection conflict",
            "A mixin could not find or modify its intended target.",
            "Check whether the mod supports your exact Minecraft version and whether another mod changes the same code."
        ),

        Rule(
            listOf(
                "Mixin config",
                "MixinConfigs",
                "Could not find mixin config"
            ),
            "Mixin configuration problem",
            "A mod's mixin configuration may be missing, invalid, or incorrectly packaged.",
            "Re-download the mod and check whether it is intended for your loader."
        ),

        Rule(
            listOf(
                "Mixin transformation failed",
                "MixinTransformerError",
                "Error loading class"
            ),
            "Class transformation failure",
            "A class transformation failed while the game was loading.",
            "Check the first exception and recently installed mods. Look for incompatible mixins."
        ),

        // REGISTRY AND CONTENT ERRORS

        Rule(
            listOf(
                "Missing registry",
                "Unknown registry",
                "Failed to load registry",
                "Registry remapping failed"
            ),
            "Registry loading problem",
            "A modded block, item, entity, or other registry entry could not be resolved.",
            "Check removed or updated content mods and make sure required mods are installed."
        ),

        Rule(
            listOf(
                "Missing data pack",
                "Failed to load datapack",
                "Couldn't load tag",
                "Unknown registry key"
            ),
            "Datapack or registry error",
            "A datapack or modded content reference could not be loaded.",
            "Check datapacks and content mods. Restore missing dependencies or remove broken datapacks."
        ),

        Rule(
            listOf(
                "Failed to parse",
                "JsonParseException",
                "JsonSyntaxException"
            ),
            "Invalid JSON data",
            "A mod configuration, recipe, loot table, or resource file contains invalid JSON.",
            "Check the file named in the error. Restore its default configuration or reinstall the affected mod."
        ),

        // CONFIGURATION ERRORS

        Rule(
            listOf(
                "Config loading failed",
                "Failed to load config",
                "TomlParser",
                "Invalid TOML"
            ),
            "Broken mod configuration",
            "A mod configuration file may contain invalid or unsupported values.",
            "Back up the configuration and let the mod regenerate its default config."
        ),

        Rule(
            listOf(
                "Config file is corrupt",
                "Malformed configuration",
                "ConfigurationException"
            ),
            "Configuration parsing failure",
            "A configuration file could not be read correctly.",
            "Back up and reset the affected configuration file."
        ),

        // MEMORY AND PERFORMANCE

        Rule(
            listOf(
                "OutOfMemoryError",
                "Java heap space",
                "GC overhead limit exceeded"
            ),
            "Java memory exhaustion",
            "Minecraft ran out of available Java heap memory.",
            "Reduce heavy mods and close other apps. Use a memory allocation appropriate for your device."
        ),

        Rule(
            listOf(
                "StackOverflowError"
            ),
            "Stack overflow",
            "A recursive operation may have repeated too many times.",
            "Check recently installed mods and possible circular interactions."
        ),

        Rule(
            listOf(
                "ConcurrentModificationException"
            ),
            "Concurrent modification",
            "A collection was modified while another operation was iterating through it.",
            "Check for mod conflicts or bugs involving simultaneous game operations."
        ),

        // GRAPHICS AND RENDERING

        Rule(
            listOf(
                "GL_OUT_OF_MEMORY",
                "OpenGL out of memory",
                "Failed to allocate texture"
            ),
            "GPU memory exhaustion",
            "The renderer could not allocate enough graphics memory.",
            "Lower render distance, texture resolution, and shader quality."
        ),

        Rule(
            listOf(
                "GL_INVALID_OPERATION",
                "GL_INVALID_ENUM",
                "OpenGL error"
            ),
            "OpenGL rendering error",
            "A graphics operation was rejected by the active OpenGL implementation.",
            "Disable shaders and rendering mods temporarily. Check renderer compatibility."
        ),

        Rule(
            listOf(
                "Shader compilation failed",
                "Failed to compile shader",
                "Shader compile error"
            ),
            "Shader compilation failure",
            "A shader or resource pack contains code the renderer could not compile.",
            "Disable the shader or use a version compatible with your renderer and Minecraft version."
        ),

        Rule(
            listOf(
                "LWJGLException",
                "GLFW error",
                "Failed to create GLFW window"
            ),
            "Graphics or window initialization failure",
            "The graphics or windowing system failed to initialize.",
            "Check renderer compatibility and graphics settings."
        ),

        // NATIVE CRASHES

        Rule(
            listOf(
                "EXCEPTION_ACCESS_VIOLATION",
                "SIGSEGV",
                "SIGABRT",
                "Fatal signal"
            ),
            "Native or renderer crash",
            "The process crashed inside native code.",
            "Test with the default renderer and without shaders. Check native libraries and graphics compatibility."
        ),

        Rule(
            listOf(
                "UnsatisfiedLinkError",
                "dlopen failed",
                "Native library",
                "couldn't load library"
            ),
            "Native library loading failure",
            "A required native library could not be loaded.",
            "Check architecture compatibility and reinstall the affected component or runtime."
        ),

        // AUDIO

        Rule(
            listOf(
                "OpenAL error",
                "AL_INVALID_OPERATION",
                "Failed to initialize OpenAL"
            ),
            "Audio initialization failure",
            "The audio system failed to initialize or perform an operation.",
            "Check audio settings and mods that modify sound. Restart the game."
        ),

        // NETWORK AND DOWNLOADS

        Rule(
            listOf(
                "UnknownHostException",
                "SocketTimeoutException",
                "Failed to download",
                "Connection refused"
            ),
            "Network or download failure",
            "A required file or server could not be reached.",
            "Check your internet connection and retry."
        ),

        // ARCHIVE AND FILE ERRORS

        Rule(
            listOf(
                "ZipException",
                "invalid LOC header",
                "zip END header not found"
            ),
            "Corrupted mod or archive",
            "A JAR, ZIP, or other archive may be incomplete or corrupted.",
            "Delete the damaged download and download the file again."
        ),

        Rule(
            listOf(
                "FileNotFoundException",
                "NoSuchFileException"
            ),
            "Missing file",
            "A required file or resource could not be found.",
            "Check the file path and reinstall the affected mod or resource."
        )
    )

    @JvmStatic
    fun analyze(logText: String): Report {
        if (logText.isBlank()) {
            return Report(
                emptyList(),
                "No log text provided.",
                null
            )
        }

        val text = if (logText.length > MAX_CHARS) {
            logText.takeLast(MAX_CHARS)
        } else {
            logText
        }

        val lower = text.lowercase()

        val findings = rules.mapNotNull { rule ->
            val marker = rule.markers.firstOrNull {
                lower.contains(it.lowercase())
            } ?: return@mapNotNull null

            val matchingLine = text.lineSequence()
                .firstOrNull {
                    it.contains(marker, ignoreCase = true)
                }
                ?.trim()
                ?.take(MAX_MATCH_LENGTH)
                ?: marker

            Finding(
                rule.title,
                rule.explanation,
                rule.fix,
                matchingLine
            )
        }

        val cause = findings.firstOrNull()?.title

        val summary = when {
            findings.isEmpty() ->
                "No known crash pattern detected. Check the final exception and first 'Caused by:' section."

            findings.size == 1 ->
                "Found one possible problem: $cause."

            else ->
                "Found ${findings.size} possible problem patterns. These are clues, not guaranteed root causes."
        }

        return Report(
            findings,
            summary,
            cause
        )
    }

    private const val MAX_CHARS = 2 * 1024 * 1024
    private const val MAX_MATCH_LENGTH = 240
}
