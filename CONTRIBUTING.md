# 参与贡献

欢迎提交可复现的问题、修复和改进。请先说明触发步骤、Android 版本、软件版本和预期结果；截图及日志中请删除账号、Cookie、令牌和私人阅读内容。

## 本地检查

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

HTML 解析兼容问题应附最小的合成 HTML 样本，保留相关节点和属性即可。不要提交第三方小说正文、付费章节、账号数据或整页个人书架。

修改脚本宿主时，同时运行：

```sh
cd tools/comic-host
npm ci
npm test
```

依赖版本集中在 `gradle/libs.versions.toml`。请保持修改范围清楚，说明实际验证结果；新的本地设置和签名文件不要提交。
