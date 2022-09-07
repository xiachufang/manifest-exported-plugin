package com.xiachufang.manifest.exported

import com.google.gson.annotations.SerializedName

data class RulesListBean(
    @SerializedName("actionRules")
    val actionRules: List<String> = listOf(),
    @SerializedName("blackIgnores")
    val blackIgnores: List<String> = listOf(),
    @SerializedName("blackNames")
    val blackNames: List<String> = listOf(),
    @SerializedName("blackPackages")
    val blackPackages: List<String> = listOf(),
    @SerializedName("whiteNames")
    val whiteNames: List<String> = listOf()
)
