# 欢迎来到XDAGJ

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FXDagger%2Fxdagj.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_shield) ![](https://github.com/XDagger/xdagj/actions/workflows/maven.yml/badge.svg) ![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/XDagger/xdagj) ![GitHub](https://img.shields.io/github/license/XDagger/xdagj) ![GitHub issues](https://img.shields.io/github/issues/XDagger/xdagj)


[English Version](../README.md)

## 目录
  - [系统环境](#系统环境)
  - [安装与用法](#安装与用法)
  - [发展](#发展)
    - [火星计划](#火星计划)
  - [代码规范](#代码规范)
  - [贡献](#贡献)
  - [赞助](#赞助)
  - [其他](#其他)
  - [执照](#执照)

## 系统环境

```yaml
JDK   : v17
Maven : v3.9.1
```

### JDK 17 下载
Eclipse Temurin™ 17 Latest Releases(https://adoptium.net/temurin/releases/)

### Maven 3.9.x 下载
Apache Maven 3.9.x Latest Releases(https://maven.apache.org/download.cgi)


## 安装与用法

XDAGJ教程可以让您快速加入并体验XDAGJ的钱包及挖矿功能，私有链搭建教程帮助您搭建一个独有的链上环境，能更好的测试并发现错误

[XDAGJ测试网接入教程](XDAGJ_TestNet_Tutorial_zh.md)

[XDAGJ私有链搭建教程](XDAGJ_Devnet_Tutorial_zh.md)

[XDAGJ测试网区块浏览器](http://146.56.240.230/)

## 发展

火星计划一方面规划XDAG未来的发展路径，凝聚社区力量，推动XDAG向新的高度演进；另一方面利用全球排名第一的Java编程语言完善XDAG元宇宙生态体系，吸引开发者加入。

### 火星计划

四个阶段：

#### 探索阶段：XDAGJ测试网上线（已上线，公测中）

- [x] 部署XDAGJ测试网环境,开放公测
  
- [x] 接入RandomX算法
  
- [x] 接入libp2p协议
  
- [x] 测试网区块链浏览器
  
- [x] 测试币获取


#### 登陆阶段：XDAGJ主网上线

- [x] 完善测试案例：逐步完善现有功能的测试案例
  
- [x] 完善日志功能：提供较为完整的日志功能，便于后期问题排查
  
- [x] 优化同步协议：改进现有的同步协议，提高同步效率
  
- [x] 实现快照功能：降低矿池运行的成本，加速启动
  
- [x] 实现RPC功能：接入Web3j，实现接口的规范化
  
- [x] 挖矿协议改进：引入较成熟的Stratum协议，方便矿机的接入与使用
  
- [x] 轻量级钱包应用：加入浏览器钱包
  
- [x] 规范公私钥格式，遵循BIPXX规范，加入助记词方式生成公私钥对

#### 拓展阶段：XDAGJ & EVM 拓展

- [x] 修改地址块结构，增加手续费
  
- [x] 优化改善移动端钱包，提高用户体验
  
- [ ] 支持XRC协议
  
- [ ] 降低矿池门槛，逐步开放白名单从而实现完全去中心化

#### 繁荣阶段：XDAGJ & DeFi

- [ ] 跨链：兼容多种区块链系统接入，实现XDAG与其它链世界的互通
  
- [ ] 加入预言机
  
- [ ] 加入分布式交易所

## 代码规范

- Git

  我们使用如下所描述的gitflow分支模型：

  - `master`为主分支，也是用于部署生产环境的分支，任何时间都不能直接修改代码
  - `develop`为开发分支，始终保持最新完成以及bug修复后的代码
  - `feature`为新功能分支，开发新功能时，以`develop`分支为基础，并且按照开发特性创建对应的`feature/xxx`分支
  - `release`为预上线分支，发布提测阶段，会release分支代码为基准提测。当有一组feature开发完成，首先会合并到develop分支，进入提测时会创建release分支。如果测试过程中若存在bug需要修复，则直接由开发者在release分支修复并提交。当测试完成之后，合并release分支到master和develop分支，此时master为最新代码，用作上线
  - `hotfix`为修复线上紧急问题的分支，以`master`分支为基线，创建`hotfix/xxx`分支，修复完成后，需要合并到`master`分支和`develop`分支

- 提交信息

  提交消息必须以一个简短的主题行开始，然后是一个可选的、更详细的解释性文本，该文本与摘要以空行分隔

- Pull 请求

  pull request必须尽可能清晰和详细，包括所有相关的问题。如果拉请求是为了关闭一个问题，请使用Github的关键字约定[关闭，修复，或解决](https://help.github.com/articles/closing-issues-via-commit-messages/)。如果pull请求只完成问题的一部分，则使用`connected`关键字。这有助于我们的工具正确地将问题链接到拉出请求

- 代码风格

  在`misc/code-style`文件夹中使用`xdagj`代码风格的`formatter_eclipse.xml`或`formatter_intellij.xml`

- 代码评审

  我们重视代码的质量和准确性。因此，我们将审查所有需要更改的代码

## 常见问题

- 时间同步问题

[XDAGJ时间同步](XDAGJ_Time_Synchronization_zh.md)
## 贡献

- 安全问题

  XDAGJ仍旧处于大量开发的过程，这意味着现有的代码或者协议可能存在问题，或者实际中可能存在的错误。如果您发现安全问题，希望可以尽快的将其予以反馈

  若果发现可能影响已部署系统安全性的问题，我们希望您能在私下将问题发送至邮箱 xdagj@xdag.io，请勿公开讨论

  如果问题是协议的弱点或不回影响线上系统，可以公开讨论并发布至Issues

- 新功能的添加

  我们非常乐意为XDAGJ添加更多实用且有意思的新功能，您可以谈论任何有意思的新功能

如果您对XDAGJ的开发感兴趣，我们也欢迎您加入到开发者团队，为XDAGJ贡献一份您的力量


## 赞助

自阿波罗计划的提出至今，社区实现了XDAGJ由0到1的突破，XDAGJ的开发工作也逐渐步入正轨。您的支持不仅帮助我们快速开发和完善XDAGJ， 同时也是对我们工作的肯定！

XDAG：+89Zijf2XsXqbdVK7rdfR4F8+RkHkAPh

## 其他
[XDAGJ Libp2P介绍](./XDAGJ_Networking_Specification.md)

[XDAG WIKI](https://github.com/XDagger/xdag/wiki)  

[XDAG白皮书](https://github.com/XDagger/xdag/blob/master/WhitePaper%20zh-cn.md)

[XDAG协议规范](https://github.com/XDagger/xdag/blob/master/Protocol-cn.md)


## 执照


[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FXDagger%2Fxdagj.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_large)


