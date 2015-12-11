package com.facebook.testing.screenshot.build

import org.gradle.api.*

class ScreenshotsPluginExtension {
    def testApkTarget = "packageDebugAndroidTest"
    def connectedAndroidTestTarget = "connectedAndroidTest"
    def customTestRunner = false
    def recordDir = "screenshots"
    def addCompileDeps = true

    // Deprecated. We automatically detect adb now. Using this will
    // throw an error.
    @Deprecated
    public void setAdb(String path) {
      throw new IllegalArgumentException("Use of 'adb' is deprecated, we automatically detect it now")
    }
}

class ScreenshotsPlugin implements Plugin<Project> {
  void apply(Project project) {
    project.extensions.create("screenshots", ScreenshotsPluginExtension)

    def recordMode = false
    def verifyMode = false

    def codeSource = ScreenshotsPlugin.class.getProtectionDomain().getCodeSource();
    def jarFile = new File(codeSource.getLocation().toURI().getPath());

    // We'll figure out the adb in afterEvaluate
    def adb = null

    if (project.screenshots.addCompileDeps) {
      addRuntimeDep(project)
    }

    project.task('pullScreenshots') << {
      project.exec {
        def output = getTestApkOutput(project)

        executable = 'python'
        environment('PYTHONPATH', jarFile)

        args = ['-m', 'android_screenshot_tests.pull_screenshots', "--apk", output.toString()]

        if (recordMode) {
          args += ["--record", project.screenshots.recordDir]
        } else if (verifyMode) {
          args += ["--verify", project.screenshots.recordDir]
        }
      }
    }

    project.task("clearScreenshots") << {
      project.exec {
        executable = adb
        args = ["shell", "rm", "-rf", "/sdcard/screenshots"]
        ignoreExitValue = true
      }
    }

    project.afterEvaluate {
      adb = project.android.getAdbExe().toString()
      project.task("screenshotTests")
      project.screenshotTests.dependsOn project.clearScreenshots
      project.screenshotTests.dependsOn project.screenshots.connectedAndroidTestTarget
      project.screenshotTests.dependsOn project.pullScreenshots

      project.pullScreenshots.dependsOn project.screenshots.testApkTarget
    }

    if (!project.screenshots.customTestRunner) {
       project.android.defaultConfig {
           testInstrumentationRunner = 'com.facebook.testing.screenshot.ScreenshotTestRunner'
       }
    }

    project.task("recordMode") << {
      recordMode = true
    }

    project.task("verifyMode") << {
      verifyMode = true
    }
  }

  String getTestApkOutput(Project project) {
    return project.tasks.getByPath(project.screenshots.testApkTarget).getOutputs().getFiles().getSingleFile().getAbsolutePath()
  }

  void addRuntimeDep(Project project) {
    def implementationVersion = getClass().getPackage().getImplementationVersion()

    if (!implementationVersion) {
      println("WARNING: you shouldn't see this in normal operation, file a bug report if this is not a framework test")
      implementationVersion = '0.2.4.py3'
    }

    project.dependencies.androidTestCompile('com.facebook.testing.screenshot:core:' + implementationVersion)
  }
}
