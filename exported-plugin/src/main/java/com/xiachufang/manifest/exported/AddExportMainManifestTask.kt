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
 *
 * @author petterp To 2022/6/21
 */
open class AddExportMainManifestTask : DefaultTask() {

    private lateinit var extArg: ExportedExtension

    // mainManifest
    private lateinit var mainManifest: File

    // 第三方aar的Manifest
    private lateinit var manifests: Set<File>

    private val exportedErrorMessage =
        " Manifest merger failed : android:exported needs to be explicitly specified for <activity>. Apps targeting Android 12 and higher are required to specify an explicit value for `android:exported` when the corresponding component has an intent filter defined. See https://developer.android.com/guide/topics/manifest/activity-element#exported for details"
    private var exportedError = false
    private var blackPackages = mutableListOf<String>()
    private var blackNames = mutableListOf<String>()
    private var blackIgnores = mutableListOf<String>()

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
        // 默认不对主manifest做处理,交给系统自行处理,主要原因是这里是我们业务可控制部分
        println("-----exported->start------")
        initBlackRules()
        val builder: StringBuilder = StringBuilder()
        builder.addRulesLog()
        exportedMain(builder)
        exportedAar(builder)
        if (exportedError) {
            builder.append("## 处理终止,请手动处理主model。")
            writeOut(builder)
            throw ManifestException(exportedErrorMessage)
        }
        writeOut(builder)
        println("-----exported->End------")
    }

    private fun initBlackRules() {
    }

    private fun exportedMain(builder: StringBuilder) {
        builder.append("## App-AndroidManifest\n")
        builder.append("> 这里是你的业务主model下需要调整的,建议手动处理。\n")
        exportedManifest(mainManifest, builder, extArg.enableMainManifest)
        builder.append("> 主model处理结束。\n")
        builder.append("---\n\n\n")
    }

    private fun exportedAar(builder: StringBuilder) {
        builder.append("## aar-AndroidManifest\n")
        builder.append("> 这里是你的其他model或者aar下需要调整的,插件会自动进行处理。\n")
        manifests.forEach {
            exportedManifest(it, builder, true)
        }
    }

    private fun writeOut(outBuilder: StringBuilder) {
        val wikiFileDir = File("${extArg.logOutPath}/exported")
        if (!wikiFileDir.exists()) wikiFileDir.mkdir()
        val wikiFile = File(wikiFileDir, "outManifestLog.md")
        if (wikiFile.exists()) wikiFile.delete()
        wikiFile.writeText(outBuilder.toString())
    }

    private fun exportedManifest(file: File, outBuilder: StringBuilder, isMain: Boolean) {
        val aarName = file.parentFile.name
        outBuilder.append("#### 开始处理-> [$aarName]\n")
        if (!file.exists()) {
            outBuilder.append("- 文件不存在,已跳过\n\n")
            return
        }
        val xml = XmlParser(false, false).parse(file)
        val packageName = xml.attributes()["package"].toString()
        outBuilder.append("- package: [$packageName]\n")
        outBuilder.append("- path: ${file.path}\n")
        val applicationNode = xml.nodeList().firstOrNull { it.name() == "application" }
        if (applicationNode === null) {
            outBuilder.append("- 未匹配到ApplicationNode,已跳过\n\n")
            return
        }

        val nodes = applicationNode.children().asSequence().mapNotNull {
            it as? Node
        }.filter { it ->
            val name = it.name()
            (name == "activity" || name == "receiver" || name == "service") &&
                it.nodeList().any { it.name() == "intent-filter" }
        }.toList().takeIf {
            it.isNotEmpty()
        }
        if (nodes === null) {
            outBuilder.append("- 未找到可修改的节点,已跳过\n\n")
            return
        }

        var updateSum = 0
        val isBlackPackage = packageName.isBlackPackage
        nodes.forEachIndexed { _, node ->
            val isIgnore = node.isIgnore
            var exportedStateToBlack = false
            val isUpdateSuccess =
                if (!isIgnore && (isBlackPackage || node.isBlack)) {
                    node.isExported = false
                    exportedStateToBlack = true
                    true
                } else node.updateExported()
            if (!isUpdateSuccess) return@forEachIndexed
            updateSum++
            outBuilder.addLog(updateSum, node, exportedStateToBlack)
        }
        val isWrite = updateSum > 0
        if (isWrite) outBuilder.append("- 处理结束,已处理 $updateSum 个\n\n")
        else {
            outBuilder.append("- 未匹配到符合规则的节点,处理结束\n\n")
            return
        }
        // 主main且禁止写入,则写入报错
        if (isMain && !extArg.enableMainManifest) {
            exportedError = true
            return
        }
        val result = XmlUtil.serialize(xml)
        file.writer(Charsets.UTF_8).use {
            it.write(result)
        }
        return
    }

    private fun StringBuilder.addRulesLog() {
        append("# exported日志输出\n\n")
        append("## 当前插件配置\n")
        append("- enableMainManifest: [${extArg.enableMainManifest}]\n")
        append("- actionRules\n")
        extArg.actionRules.forEach {
            append("  - [$it]\n")
        }
        append("- logOutPath: [${extArg.logOutPath}]\n\n")
    }

    private fun StringBuilder.addLog(pos: Int, node: Node, isBlack: Boolean) {
        append("  $pos. name:[${node.nodeName}],exported:[${node.isExported}],是否黑名单:[$isBlack]\n")
    }

    private val Node.nodeName: String?
        get() = attributes()["android:name"]?.toString()

    private val Node.isIgnore: Boolean
        get() {
            val name = nodeName
            return blackIgnores.any { name == it }
        }

    private val Node.isBlack: Boolean
        get() {
            val name = nodeName
            return blackNames.any { name == it }
        }

    private val String.isBlackPackage
        get() = blackPackages.any { it == this }

    private var Node.isExported: Boolean?
        set(value) {
            attributes()[qExportedKey] = "$value"
        }
        get() = attributes()[qExportedKey]?.toString()?.toBoolean()

    private fun Node.updateExported(): Boolean {
        if (attribute(qExportedKey) != null) return false
        val isExported = nodeList().any { node ->
            node.nodeList().any {
                it.name() == "action" && it.anyTag(qNameKey, extArg.actionRules)
            }
        }
        this.isExported = isExported
        return true
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
