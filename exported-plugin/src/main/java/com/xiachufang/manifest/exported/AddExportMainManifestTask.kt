package com.xiachufang.manifest.exported

import groovy.util.Node
import groovy.util.NodeList
import groovy.util.XmlParser
import groovy.xml.XmlUtil
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.java.archives.ManifestException
import org.gradle.api.tasks.TaskAction

/**
 * 在processDebugMainManifest之前对manifest进行处理
 * @author petterp To 2022/6/21
 */
open class AddExportMainManifestTask : DefaultTask() {

    private lateinit var extArg: ExportedExtension

    // mainManifest
    private lateinit var mainManifest: File

    // 第三方aar的Manifest
    private lateinit var manifests: Set<File>

    private var exportedError = false

    private val qNameKey = "android:name"
    private val qExportedKey = "android:exported"

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
        println("-----exported->start------")
        // 默认不对主manifest做处理,交给系统自行处理,主要原因是这里是我们业务可控制部分
        val builder: StringBuilder = StringBuilder()
        builder.append("# exported日志输出\n\n")
        builder.append("## 当前插件配置\n")
        builder.append("- enableMainManifest: [${extArg.enableMainManifest}]\n")
        builder.append("- actionRules\n")
        extArg.actionRules.forEach {
            builder.append("  - [$it]\n")
        }
        builder.append("- logOutPath: [${extArg.logOutPath}]\n\n")

        builder.append("## App-AndroidManifest\n")
        builder.append("> 这里是你的业务主model下需要调整的,建议手动处理。\n")
        exportedManifest(mainManifest, builder, extArg.enableMainManifest)
        builder.append("> 主model处理结束。\n")
        builder.append("---\n\n\n")

        builder.append("## aar-AndroidManifest\n")
        builder.append("> 这里是你的其他model或者aar下需要调整的,插件会自动进行处理。\n")
        manifests.forEach {
            exportedManifest(it, builder, true)
        }
        if (exportedError) {
            builder.append("## 处理终止,请手动处理主model。")
            writeOut(builder)
            throw ManifestException(" Manifest merger failed : android:exported needs to be explicitly specified for <activity>. Apps targeting Android 12 and higher are required to specify an explicit value for `android:exported` when the corresponding component has an intent filter defined. See https://developer.android.com/guide/topics/manifest/activity-element#exported for details")
        }
        writeOut(builder)
        println("-----exported->End------")
    }

    private fun writeOut(outBuilder: StringBuilder) {
        val wikiFileDir = File("${extArg.logOutPath}/exported")
        if (!wikiFileDir.exists()) wikiFileDir.mkdir()
        val wikiFile = File(wikiFileDir, "outManifestLog.md")
        if (wikiFile.exists()) wikiFile.delete()
        wikiFile.writeText(outBuilder.toString())
    }

    private fun exportedManifest(file: File, outBuilder: StringBuilder, isWrite: Boolean) {
        val aarName = file.parentFile.name
        outBuilder.append("#### 开始处理-> [$aarName]\n")
        outBuilder.append("- path= ${file.path}\n")
        if (!file.exists()) {
            outBuilder.append("- 文件不存在,已跳过\n\n")
            return
        }
        val xml = XmlParser(false, false).parse(file)
        // 先拿出appNode
        val applicationNode = xml.nodeList().firstOrNull {
            it.name() == "application"
        }
        if (applicationNode === null) {
            outBuilder.append("- 未匹配到ApplicationNode,已跳过\n\n")
            return
        }
        // 过滤使用 intent 过滤器的 activity、服务 或 广播接收器 && exported未显式声明
        val nodes = applicationNode.children().asSequence().mapNotNull {
            it as? Node
        }.filter { it ->
            val name = it.name()
            (name == "activity" || name == "receiver" || name == "service") &&
                it.attribute(qExportedKey) == null && it.nodeList().any {
                it.name() == "intent-filter"
            }
        }.toList().takeIf {
            it.isNotEmpty()
        }
        if (nodes === null) {
            outBuilder.append("- 未命中可修改的节点,已跳过\n\n")
            return
        }
        outBuilder.append("- 已找到[${nodes.size}]处exported需要适配 \n")
        nodes.forEachIndexed { index, it ->
            val isExported = it.nodeList().any { node ->
                node.nodeList().any {
                    it.name() == "action" &&
                        it.anyTag(qNameKey, extArg.actionRules)
                }
            }
            outBuilder.append("  ${index + 1}. name:[${it.attributes()["android:name"]}],exported:[$isExported]\n")
            it.attributes()["android:exported"] = "$isExported"
        }
        outBuilder.append("- 处理结束\n\n")
        if (isWrite) {
            val result = XmlUtil.serialize(xml)
            file.writer(Charsets.UTF_8).use {
                it.write(result)
            }
        } else {
            exportedError = true
        }
    }

    private fun Node.nodeList() = (this.value() as NodeList).mapNotNull {
        // 用于防止某些不标准的写法,如//xx 注释直接写到了manifest里
        it as? Node
    }

    private fun Node.anyTag(key: String, values: Array<String>): Boolean {
        // 如果规则为null,直接返回false,对于无法匹配的,做出扼制,不应让其显示声明出来
        if (values.isEmpty()) return false
        return attributes()[key]?.let { v ->
            val value = v.toString()
            values.any {
                it == value
            }
        } ?: false
    }
}
