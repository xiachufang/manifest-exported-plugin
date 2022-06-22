package com.xiachufang.manifest.exported

import java.io.File

/**
 * @author petterp
 */
open class ExportedExtension {

    // 是否显示日志,默认false
    var enableLog: Boolean = false

    // 定义的白名单,此名单里的exported直接为true
    var whiteFile: File? = null

    // 是否支持写入主清单文件,默认false,用户手动决定
    var enableMainManifest: Boolean = false

    fun toLog(log: String) {
        if (enableLog) println(log)
    }
}
