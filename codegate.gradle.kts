// codegate.gradle.kts
// Adds a Gradle task `codegate` that opens CodeGate AI Pro on this project.
// First run downloads the app from this repo's releases and keeps it cached.

tasks.register("codegate") {
    group = "codegate"
    description = "Opens CodeGate AI Pro on this project (downloads it the first time)."

    val version = (project.findProperty("codegate.version") as String?) ?: "1.0.0"
    val projectPath = project.rootDir.absolutePath
    notCompatibleWithConfigurationCache("downloads and launches an external app")

    doLast {
        val repo = "tharikDev13/CodeGateAIPro-releases"
        val bundleId = "com.boat.codegateaipro"

        if (!System.getProperty("os.name").startsWith("Mac"))
            throw GradleException("CodeGate AI Pro supports macOS only for now.")
        if (System.getProperty("os.arch") != "aarch64")
            throw GradleException("This build is for Apple Silicon Macs only.")

        fun run(vararg cmd: String): Int = ProcessBuilder(*cmd).inheritIO().start().waitFor()
        fun quiet(vararg cmd: String): Int {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            p.inputStream.readBytes()
            return p.waitFor()
        }
        fun isRunning() = quiet("/usr/bin/pgrep", "-f", "CodeGateAIPro.app/Contents/MacOS") == 0

        // Same cache folder as the Xcode launcher, so one download serves both
        val base = File(System.getProperty("user.home"),
            "Library/Application Support/CodeGateAIProLauncher/$version")
        val app = File(base, "CodeGateAIPro.app")

        if (!app.exists()) {
            base.mkdirs()
            val url = "https://github.com/$repo/releases/download/v$version/CodeGateAIPro-mac-$version.zip"
            logger.lifecycle("Downloading CodeGate AI Pro $version ...")
            val zip = File(base, "app.zip")
            java.net.URI(url).toURL().openStream().use { input ->
                zip.outputStream().use { input.copyTo(it) }
            }
            logger.lifecycle("Installing ...")
            if (run("/usr/bin/ditto", "-x", "-k", zip.absolutePath, base.absolutePath) != 0)
                throw GradleException("Could not unzip the download.")
            quiet("/usr/bin/xattr", "-dr", "com.apple.quarantine", app.absolutePath)
            zip.delete()
            if (!app.exists()) throw GradleException("App not found after unzip.")
        }

        // If it is already running, close it so the new launch starts fresh on this project
        if (isRunning()) {
            quiet("/usr/bin/osascript", "-e", "tell application id \"$bundleId\" to quit")
            var waited = 0
            while (isRunning() && waited < 50) { Thread.sleep(100); waited++ }
            if (isRunning()) throw GradleException("CodeGate AI Pro is still running. Quit it (Cmd+Q) and run again.")
        }

        logger.lifecycle("Opening CodeGate AI Pro on $projectPath")
        run("/usr/bin/open", "-n", app.absolutePath, "--args", "--project", projectPath)
    }
}
