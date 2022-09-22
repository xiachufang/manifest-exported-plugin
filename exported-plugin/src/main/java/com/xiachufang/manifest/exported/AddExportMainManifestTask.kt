package com.xiachufang.manifest.exported

import groovy.util.Node
import groovy.util.NodeList
import groovy.util.XmlParser
import groovy.xml.XmlUtil
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.java.archives.ManifestException
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.com.google.gson.Gson
import org.jetbrains.kotlin.com.google.gson.JsonSyntaxException

/**
 * 在processDebugMainManifest之前对manifest进行处理
 *
 * @author petterp To 2022/6/21
 */
open class AddExportMainManifestTask : DefaultTask() {

    // mainManifest
    private lateinit var mainManifest: File

    // 第三方aar的Manifest
    private lateinit var manifests: Set<File>

    private val exportedErrorMessage =
        " Manifest merger failed : android:exported needs to be explicitly specified for <activity>. Apps targeting Android 12 and higher are required to specify an explicit value for `android:exported` when the corresponding component has an intent filter defined. See https://developer.android.com/guide/topics/manifest/activity-element#exported for details"
    private var exportedError = false

    private var blackPackages = mutableListOf<String>()
    private var actionRules = mutableListOf("android.intent.action.MAIN")
    private var blackNames = mutableListOf<String>()
    private var blackIgnores = mutableListOf<String>()
    private var whiteNames = mutableListOf<String>()
    private var enableMainManifest = false
    private var outPutFile: File? = null

    private val qNameKey = "android:name"
    private val qExportedKey = "android:exported"

    fun setManifests(files: Set<File>) {
        this.manifests = files
    }

    fun setMainManifest(mainManifest: File) {
        this.mainManifest = mainManifest
    }

    fun setExtArg(arg: ExportedExtension) {
        enableMainManifest = arg.enableMainManifest
        outPutFile = arg.outPutFile
        initRules(arg.ruleFile)
    }

    private fun initRules(file: File?) {
        if (file == null) return
        val json = file.readText(Charsets.UTF_8)
        try {
            Gson().fromJson(json, RulesListBean::class.java)?.let {
                blackPackages.addAll(it.blackPackages)
                blackIgnores.addAll(it.blackIgnores)
                blackNames.addAll(it.blackNames)
                whiteNames.addAll(it.whiteNames)
                if (it.actionRules.isNotEmpty()) {
                    actionRules.clear()
                    actionRules.addAll(it.actionRules)
                }
            }
        } catch (t: JsonSyntaxException) {
            throw JsonSyntaxException("解析规则异常,请检查你的json格式是否正常,位置:[${file.path}]")
        }
    }

    @TaskAction
    fun action() {
        // 默认不对主manifest做处理,交给系统自行处理,主要原因是这里是我们业务可控制部分
        println("-----exported->start------")
        val builder: StringBuilder = StringBuilder()
        builder.addRulesLog()
        exportedMain(builder)
        if (exportedError) {
            builder.append("## 处理终止,请手动处理主model。")
            writeOut(builder)
            throw ManifestException(exportedErrorMessage)
        }
        exportedAar(builder)
        writeOut(builder)
        println("-----exported->End-------")
    }

    private fun exportedMain(builder: StringBuilder) {
        builder.append("## App-AndroidManifest\n")
        builder.append("> 这里是你的业务主model下需要调整的,建议手动处理。\n")
        exportedManifest(mainManifest, builder, true)
        builder.append("> 主model处理结束。\n")
        builder.append("---\n\n\n")
    }

    private fun exportedAar(builder: StringBuilder) {
        builder.append("## aar-AndroidManifest\n")
        builder.append("> 这里是你的其他model或者aar下需要调整的,插件会自动进行处理。\n")
        manifests.forEach {
            exportedManifest(it, builder, false)
        }
    }

    private fun writeOut(outBuilder: StringBuilder) {
        outPutFile?.apply {
            createFileIfNoExists()
            writeText(outBuilder.toString())
        }
    }

    private fun exportedManifest(file: File, outBuilder: StringBuilder, isMain: Boolean) {
        val aarName = file.parentFile.name
        if (!file.exists()) return

        val xml = XmlParser(false, false).parse(file)
        val packageName = xml.attributes()["package"].toString()
        val applicationNode = xml.nodeList().firstOrNull { it.name() == "application" }
        if (applicationNode === null) return

        val nodes = applicationNode.children().asSequence().mapNotNull {
            it as? Node
        }.filter { it ->
            val name = it.name()
            (name == "activity" || name == "receiver" || name == "service") &&
                it.nodeList().any { it.name() == "intent-filter" }
        }.toList().takeIf {
            it.isNotEmpty()
        }
        if (nodes === null) return

        outBuilder.append("#### 开始处理-> [$aarName]\n")
        outBuilder.append("- package: [$packageName]\n")
        outBuilder.append("- path: ${file.path}\n")
        var updateSum = 0
        val isBlackPackage = packageName.isBlackPackage
        nodes.forEachIndexed { _, node ->
            var exportedStateToBlack = false
            var exportedStateToWhite = false
            val isUpdateSuccess =
                if (node.isWhite) {
                    if (node.isExported != true) {
                        node.isExported = true
                        exportedStateToWhite = true
                        true
                    } else false
                } else if (!node.isIgnore && (isBlackPackage || node.isBlack)) {
                    if (node.isExported != false) {
                        node.isExported = false
                        exportedStateToBlack = true
                        true
                    } else false
                } else node.updateExported()
            if (!isUpdateSuccess) return@forEachIndexed
            updateSum++
            outBuilder.addLog(updateSum, node, exportedStateToBlack, exportedStateToWhite)
        }
        val isWrite = updateSum > 0
        if (isWrite) outBuilder.append("- 处理结束,已处理 $updateSum 个\n\n")
        else {
            outBuilder.append("- 未匹配到符合规则的节点,处理结束\n\n")
            return
        }
        // 主main且禁止写入,则写入报错
        if (isMain && !enableMainManifest) {
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
        append("- enableMainManifest: [$enableMainManifest]\n")
        addRulesLog("- blackPackages\n", blackPackages)
        addRulesLog("- whiteNames\n", whiteNames)
        addRulesLog("- blackIgnores\n", blackIgnores)
        addRulesLog("- actionRules\n", actionRules)
        append("- logOutPath: [$outPutFile]\n\n")
    }

    private fun StringBuilder.addRulesLog(name: String, item: List<String>) {
        append(name)
        if (item.isEmpty()) {
            append("  - [null]\n")
            return
        }
        item.forEach {
            append("  - [$it]\n")
        }
    }

    private fun StringBuilder.addLog(pos: Int, node: Node, isBlack: Boolean, isWhite: Boolean) {
        val configure = if (isBlack) "黑名单" else if (isWhite) "白名单" else "无"
        append("  $pos. name:[${node.nodeName}],exported:[${node.isExported}],特殊配置:[$configure]\n")
    }

    private val Node.nodeName: String?
        get() = attributes()["android:name"]?.toString()

    private val Node.isIgnore: Boolean
        get() {
            val name = nodeName
            return blackIgnores.any { name == it }
        }

    private val Node.isWhite: Boolean
        get() {
            val name = nodeName
            return whiteNames.any { name == it }
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
                it.name() == "action" && it.anyTag(qNameKey, actionRules)
            }
        }
        this.isExported = isExported
        return true
    }

    private fun Node.nodeList() = (this.value() as NodeList).mapNotNull {
        // 用于防止某些不标准的写法,如//xx 注释直接写到了manifest里
        it as? Node
    }

    private fun Node.anyTag(key: String, values: List<String>): Boolean {
        // 如果规则为null,直接返回false,对于无法匹配的,做出扼制,不应让其显示声明出来
        if (values.isEmpty()) return false
        return attributes()[key]?.let { v ->
            val value = v.toString()
            values.any {
                it == value
            }
        } ?: false
    }

    private fun File.createFileIfNoExists() {
        if (exists()) return
        if (!parentFile.exists()) {
            parentFile.mkdirs()
        }
        createNewFile()
    }
}
