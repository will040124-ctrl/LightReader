# 安全问题

请勿在公开 Issue 中附上令牌、Cookie、签名私钥、网站账号或私人数据库。
疑似安全问题可以通过 GitHub 仓库的 Security → Advisories → Report a
vulnerability 提交；如果入口不可用，可先创建不含利用细节和敏感数据的 Issue，
请求维护者提供私下沟通方式。

漫画脚本在独立 JavaScript 沙箱中执行，网络请求由宿主按用户确认的域名控制。
请只安装你理解并信任的脚本。兼容层不代表任意第三方脚本都已通过安全审计。
