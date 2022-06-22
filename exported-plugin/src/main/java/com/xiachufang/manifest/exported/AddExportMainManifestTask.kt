package com.xiachufang.manifest.exported

import groovy.namespace.QName
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import java.io.File
import java.io.PrintWriter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * 对于AppExport
 * @author petterp To 2022/6/21
 */
open class AddExportMainManifestTask : DefaultTask() {

    private lateinit var extArg: ExportedExtension

    // mainManifest
    private lateinit var mainManifest: File

    // 第三方aar的Manifest
    private lateinit var manifests: Set<File>

    private val qAndroidKey =
        QName("http://schemas.android.com/apk/res/android", "name", "android")
    private val qExportedKey =
        QName("http://schemas.android.com/apk/res/android", "exported", "android")

    fun setManifests(files: Set<File>) {
        this.manifests = files
    }

    fun setMainManifest(mainManifest: File) {
        this.mainManifest = mainManifest
    }

    fun setExtArg(arg: ExportedExtension) {
        this.extArg = arg
    }

    @TaskAction
    fun action() {
        println("-----exported->start")
        // 默认不对主manifest做处理,交给系统自行处理,主要原因是这里是我们业务可控制部分
        if (extArg.enableMainManifest)
            exportedManifest(mainManifest)
        manifests.forEach {
            exportedManifest(it)
        }
        println("-----exported->End")
    }

    private fun exportedManifest(file: File) {
        extArg.toLog("-----开始处理指定File：${file.path}")
        if (!file.exists()) {
            throw RuntimeException("${file.path}不存在")
        }
        val xml = XmlParser().parse(file)
        // 先拿出appNode
        val applicationNode = xml.children()[0] as Node
        val exportedTag = "android:exported"
        // 过滤出指定node,只有没有添加exported的才主动添加,避免系统报错
        val nodes = applicationNode.children().asSequence().map {
            it as Node
        }.filter {
            val name = it.name()
            (name == "activity" || name == "receiver" || name == "service") &&
                it.attribute(qExportedKey) == null
        }.toList().takeIf {
            it.isNotEmpty()
        }
        if (nodes === null) {
            extArg.toLog("未匹配到指定项,跳过此File")
            return
        }
        nodes.forEach { it ->
            // 只有包含了过滤器并且action不为null才处理
            val isExported = it.nodeList().any { node ->
                node.name() == "intent-filter" && node.nodeList().any {
                    it.name() == "action"
                }
            }
            it.attributes()[exportedTag] = "$isExported"
        }
        val pw = PrintWriter(file)
        pw.write(groovy.xml.XmlUtil.serialize(xml))
        pw.close()
    }

    private fun Node.nodeList() = (this.value() as NodeList).map { it as Node }

    private fun Node.anyTag(key: String, vararg values: String) =
        attributes()[key]?.let { value ->
            values.any {
                it == value.toString()
            }
        } ?: false
}
