# Codey Maven Central Release Guide

本文档用于把 `codey-common`、`codey-core` 和 `codey-boot-starter` 作为正式产物发布到公共 Maven 仓库。

## 发布范围

- `codey-common`：公共 DTO、工具协议和元数据模型
- `codey-core`：核心运行时能力
- `codey-boot-starter`：给 Spring Boot 项目直接接入的 starter

说明：

- `codey-console` 和 `codey-demo-api` 已在 POM 中配置 `maven.deploy.skip=true`
- 这两个模块仍会参与本地构建，但不会上传到公共仓库

## 发布前置条件

1. 拥有 Sonatype Central Portal 账号，并创建好发布 Token
2. 拥有可用的 GPG 密钥对，并能在当前机器执行签名
3. 确认 GitHub 仓库 `https://github.com/cdy-codey/codey` 可正常访问
4. 确认项目仓库 URL、Tag 和发布信息已经准备完成

说明：

- 当前 POM 使用 `GitHub` 作为主 `SCM` 地址
- 当前发布坐标使用 `io.github.cdy-codey`
- 如果后续变更 GitHub 组织名或仓库名，需要同步更新 `groupId` 和 `SCM`

## Maven settings.xml

把以下内容加入 Maven 的 `settings.xml`：

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>${env.GPG_PASSPHRASE}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>

  <activeProfiles>
    <activeProfile>gpg</activeProfile>
  </activeProfiles>
</settings>
```

## 环境变量

发布前请设置以下环境变量：

```powershell
$env:GPG_KEY_ID="你的 GPG key id"
$env:GPG_PASSPHRASE="你的 GPG passphrase"
```

## 本地验证

先执行一次本地校验，确认源码包、javadoc 包和签名链路都正常：

```bash
mvn -Prelease clean verify
```

如果只想验证 Starter 以及其依赖模块，可以执行：

```bash
mvn -Prelease -pl codey-boot-starter -am clean verify
```

## 正式发布

执行正式发布命令：

```bash
mvn -Prelease clean deploy
```

说明：

- `release` profile 会自动附加 `sources.jar`
- `release` profile 会自动附加 `javadoc.jar`
- `release` profile 会自动执行 `gpg` 签名
- `central-publishing-maven-plugin` 会把构件上传到 Sonatype Central Portal

## Spring Boot 使用方式

发布成功后，业务项目可直接引入 Starter：

```xml
<dependency>
    <groupId>io.github.cdy-codey</groupId>
    <artifactId>codey-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 建议的发布顺序

1. 在仓库中打 Tag：`v0.1.0`
2. 执行 `mvn -Prelease clean verify`
3. 执行 `mvn -Prelease clean deploy`
4. 在 Central Portal 中确认发布状态
5. 更新 README 中的依赖坐标示例
