package com.xiachufang.manifest.exported

/**
 * @author petterp
 */
open class ExportedExtension {

    // 对于intent-filter-action的匹配规则,满足任意一个即可
    var actionRules: Array<String> = arrayOf("android.intent.action.MAIN")

    // 是否支持写入主清单文件,默认false,用户手动决定
    var enableMainManifest: Boolean = false

    // 日志输出位置,会输出以下内容
    var logOutPath: String = ""
}
