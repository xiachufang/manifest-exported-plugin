# exported日志输出

## 当前插件配置
- enableMainManifest: [true]
- blackPackages
  - [com.sina.weibo]
- whiteNames
  - [.simple.TestActivity]
- blackIgnores
  - [null]
- actionRules
  - [android.intent.action.MAIN]
- logOutPath: [app/outManifestLog]

## App-AndroidManifest
> 这里是你的业务主model下需要调整的,建议手动处理。
#### 开始处理-> [main]
- package: [com.xiachufang.manifest.exported]
- path: /Users/petterp/Documents/Android/kotlin/gradle/ManifestExportedPlugin/app/src/main/AndroidManifest.xml
- 未匹配到符合规则的节点,处理结束

> 主model处理结束。
---


## aar-AndroidManifest
> 这里是你的其他model或者aar下需要调整的,插件会自动进行处理。
#### 开始处理-> [core-11.11.1]
- package: [com.sina.weibo]
- path: /Users/petterp/.gradle/caches/transforms-3/39a1ff11aad198c4f451cad5b8020820/transformed/core-11.11.1/AndroidManifest.xml
- 未匹配到符合规则的节点,处理结束

