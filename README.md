# manifest-exported-plugin
[![manifest-exported-plugin](https://jitpack.io/v/xiachufang/manifest-exported-plugin.svg)](https://jitpack.io/#xiachufang/manifest-exported-plugin) [![ktlint](https://img.shields.io/badge/code%20style-%E2%9D%A4-FF4081.svg)](https://ktlint.github.io/)

用于快速适配 **Android12-Manifest-exported** 的插件。

## 📚 背景

从 `Android12` 开始，如果我们的 `tagSdk` >=31, 即以 `Android 12` 或更高版本为目标平台时，且包含使用 [intent 过滤器](https://developer.android.com/guide/components/intents-filters#Receiving)的 [activity](https://developer.android.com/guide/components/activities/intro-activities)、[服务](https://developer.android.com/guide/components/services)或[广播接收器](https://developer.android.com/guide/components/broadcasts)，则必须为这些应用组件显式声明 [`android:exported`](https://developer.android.com/guide/topics/manifest/activity-element#exported) 属性。

对于我们的主model，我们可以手动处理，但是对于第三方的aar,此时就只有在业务manifest中进行重写，比较麻烦。而且查找起来也并不容易。

***manifest-exported-plugin 就是对上述任务进行自动适配的一款插件。***

## 👨‍💻‍ 依赖方式

### 添加jitpack仓库

**build.gradle**

Gradle7.0 以下

```groovy
buildscript {
	repositories {
			// ...
			maven { url 'https://jitpack.io' }
	}
}
```

Gradle7.0+,并且已经对依赖方式进行过调整，则可能需要添加到如下位置：

> **settings.gradle**
>
> ```groovy
> pluginManagement {
>     repositories {
>         //...
>            maven { url 'https://jitpack.io' }
>        }
>  }
> ```

### Gradle

```groovy
dependencies {
      classpath 'com.github.xiachufang:manifest-exported-plugin:1.1.0'
}
```

## 👨‍🔧‍ 使用方式

### 添加插件

在主app Model中添加：

```groovy
apply plugin: 'com.xiachufang.manifest.exported'
```

> 或
>
> ```
> plugins {
>  id 'com.xiachufang.manifest.exported'
> }
> ```

### build.gradle

```groovy
apply plugin: 'com.xiachufang.manifest.exported'
...
  
exported {
    // 是否写入主Model
    enableMainManifest false
    // 规则
    ruleFile new File("$projectDir/xxx.json")
    // 输出文件,默认-app/build/exported/outManifestLog.md
		outPutFile null
}
```

## 配置参数说明

#### enableMainManifest

是否对主 model-AndroidManifest 进行修改

对于主model,属于业务可控的，建议开发者自行调整。

插件默认不会对主 model-AndroidManifest 进行修改,如果发现可用匹配上述规则的，即会进行修正。

开发者可根据日志中的提示，进行修改。

> 注意：这个操作会对Manifest的展示样式造成一定影响，建议一般不要打开。

### outPutFile

日志输出目录，默认 app/build/exported/outManifest.md

### ruleFile

具体的规则json文件, 格式如下：

```json
{
  "actionRules": [
    "android.intent.action.MAIN"
  ],
  "whiteNames": [],
  "blackPackages": [],
  "blackNames": [],
  "blackIgnores": []
}
```

#### actionRules

默认判断规则，用于当前没有配置 exported 的修改逻辑,如果当前存在exported,则跳过。

具体判断逻辑: 

如果 `intent-filter` - `action` 对应的 **android:name** 与 **actionRules** 中任意一条匹配,则将 `exported` 修改为 **true** ,否则为**false** 。

#### whiteNames

白名单类名,如果遇到此类,并且使用了 intent-filter ,则会将 `exported` 修改为 **true**

#### blackPackages

黑名单 包名合集,对于此包名下的类,如果使用了 intent-filter ,则会将 `exported` 直接修改为 **false**

#### blackNames

黑名单 类名合集，如果遇到此合集中的类,并且使用了 intent-filter ,则会将 `exported` 直接修改为 **true** 。判断逻辑会与上面 `blackPackages` 一起判断,两者满足其一即可。

#### blackIgnores

黑名单 下要忽视的类,在黑名单判断时,如果遇到此类，则使用默认规则 `actionRules` 判断。



## 📰 相关截图说明

默认情况下插件的输出目录如下所示，主 model/build/exportred/outManifest.md,默认日志如下：

![image-20220627110207568](https://tva1.sinaimg.cn/large/e6c9d24ely1h3mmdgxn8mj229j0u011e.jpg)

## 💭 注意事项

对于主model下的 `manifest` ，默认不进行适配(可开关 **enableManifest** )，会通过日志进行输出，建议大家自行对比调整。

> 为什么默认不对主 `model` 进行适配？
>
> - 对于业务 `model` ,我们建议开发者自行适配,这属于我们可控范围，适配来说主要就是为了不可控的，即第三方 `aar`
> - 修改之后，会影响原有的 `manifest` 代码风格，需要重新格式化一下，相比默认的，增加了不少空格，暂时不知道怎么解决。


## 原理简述

在ProgressXXXMainManifest任务之前进行插入,通过对manifest进行修改，从而实现exported的适配。
