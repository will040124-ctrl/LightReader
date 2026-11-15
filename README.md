# 轻阅 LightReader

一款以阅读为中心的 Android 小说、漫画与文档阅读器。使用 Kotlin 和 Jetpack Compose 构建，以水彩藏书室、实体书架和安静的纸面排版组织阅读体验。

![轻阅水彩阅读室](app/src/main/res/drawable-nodpi/watercolor_reading_room.png)

## 阅读体验

- **小说**：书架、书籍详情、章节导航、书签、进度定位、连续阅读与位置记忆；支持拖动翻页、平移／覆盖效果和工具栏显隐。
- **本地资料**：导入 EPUB 2/3、TXT、HTML、Markdown、PDF、CBZ 和图片 ZIP；PDF 支持适页与适宽，漫画支持翻页与连续下滑。
- **阅读设置**：字号、行距、字体、纸色、夜读和沉浸模式；适配手机及平板宽屏布局。
- **内容管理**：发现、搜索、榜单和标签；本地下载、阅读记录备份与恢复。
- **漫画源**：导入 JavaScript 源脚本或仓库索引，安装前核对访问域名；对 Venera 风格的基础接口提供兼容实现。

软件本身不提供书籍合集。第三方来源的可用性、登录和访问条件由相应站点决定。

## 漫画源与致谢

漫画源兼容设计参考了 [Venera](https://github.com/venera-app/venera) 及其社区的脚本接口与使用方式。感谢 Venera 项目和 [venera-configs 漫画源社区](https://github.com/venera-app/venera-configs) 的作者、维护者。兼容层由本项目实现，具体许可说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

当前不是对所有 Venera 源的完整兼容保证：基础搜索、详情、章节与图片读取、MD5／AES-ECB 和部分图片重排已实现；依赖其他宿主功能或账号登录的脚本可能需要调整。安装第三方源时，请保留作者声明并遵守其许可证及站点条款。

## 构建

需要 JDK 21、Android SDK，以及 Android SDK Platform **android-37.0**。应用运行最低版本为 Android 8.0（API 26），targetSdk 为 35。依赖版本和 Gradle 版本已锁定。

```sh
git clone https://github.com/will040124-ctrl/LightReader.git
cd LightReader
# 配置 ANDROID_HOME，或在未提交的 local.properties 中设置 sdk.dir
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows 使用 `gradlew.bat`。调试 APK 位于 `app/build/outputs/apk/debug/`。

`optimized` 构建用于无调试标记的压缩预览包，默认使用测试签名；发布自己的应用应配置自己的签名。`stable` 构建不会附带项目维护者的签名密钥，`benchmark`、`releaseQa` 和 `promoCapture` 是隔离的测试／演示构建。

## 代码结构

```text
app/src/main/java/com/will/lightreader/
  ui/        Compose 页面、实体书架和阅读排版
  data/      Room 书库、下载、章节缓存与记录备份
  source/    小说来源、Web 会话、漫画脚本宿主与本地导入
  domain/    书籍、目录和内容模型
  widget/    继续阅读桌面组件
```

HTML 解析器的第三方依赖已打包进应用，正常 Android 构建不需要 Node.js。修改这部分宿主时可运行：

```sh
cd tools/comic-host
npm ci
npm test
npm run build
```

## 参与和许可

欢迎提交可复现的问题和改进，见 [CONTRIBUTING.md](CONTRIBUTING.md)。请勿提交账号、Cookie、私钥、个人数据库或第三方小说正文；公开的章节测试样本使用原创合成内容。

项目原创源码采用 [MIT License](LICENSE)。第三方组件、脚本和外部内容保留原有许可及权利声明，见 [第三方声明与致谢](THIRD_PARTY_NOTICES.md)。请遵守内容来源的使用条款和版权要求。
