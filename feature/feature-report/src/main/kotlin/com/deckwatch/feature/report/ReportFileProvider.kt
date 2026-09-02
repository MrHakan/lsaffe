package com.deckwatch.feature.report

import androidx.core.content.FileProvider

/**
 * The `FileProvider` that hands exported reports to other apps — §13.6.
 *
 * It exists only so that this module's provider has a class name of its own. Two library manifests
 * both declaring `androidx.core.content.FileProvider` are one node to the manifest merger, which
 * then sees two different authorities and two different path configurations for the same provider
 * and fails the build. Equipment photos are shared the same way from `feature-equipment`, so this
 * module names its own subclass and the two coexist.
 *
 * There is nothing to override: [FileProvider] does the whole job, and the paths come from
 * `@xml/deckwatch_report_paths` in the manifest.
 */
class ReportFileProvider : FileProvider()
