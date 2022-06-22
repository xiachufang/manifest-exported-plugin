package com.xiachufang.manifest.exported

import com.android.build.api.variant.VariantFilter
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.tasks.ProcessApplicationManifest
import java.util.* // ktlint-disable no-wildcard-imports
import kotlin.collections.ArrayList
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 适配Android12 exported 的插件
 * @author petterp To 2022/6/21
 */
class ExportedPlugin : Plugin<Project> {
    private val variantNames = ArrayList<String>()
    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin(AppPlugin::class.java)) return
        project.extensions.create(EXPORTED_EXT, ExportedExtension::class.java)
        project.task(TASK_NAME)
        val ext = project.properties[EXPORTED_EXT] as ExportedExtension
        readVariant(project)
        project.afterEvaluate {
            addMainManifestTask(ext, project)
        }
    }

    private fun readVariant(p: Project) {
        val appExtension = p.extensions.getByType(AppExtension::class.java)
        appExtension.variantFilter { variantFilter: VariantFilter ->
            variantNames.add(
                variantFilter.name
            )
        }
    }

    /**
     * 添加task到processxxxMainManifest之后
     * 如 processDebugMainManifest
     * */
    private fun addMainManifestTask(ext: ExportedExtension, p: Project) {
        variantNames.forEach {
            val t = p.tasks.getByName(
                String.format(
                    "process%sMainManifest",
                    it.capitalized()
                )
            ) as ProcessApplicationManifest
            val exportedTask =
                p.tasks.create("$it$TASK_NAME", AddExportMainManifestTask::class.java)
            exportedTask.setExtArg(ext)
            exportedTask.setMainManifest(t.mainManifest.get())
            exportedTask.setManifests(t.getManifests().files)
            t.dependsOn(exportedTask)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun String.capitalized(): String {
        return this.replaceFirstChar {
            if (it.isLowerCase())
                it.titlecase(Locale.getDefault())
            else it.toString()
        }
    }

    companion object {
        private const val EXPORTED_EXT = "exported"
        private const val TASK_NAME = "ManifestExportedTask"
    }
}
