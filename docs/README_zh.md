# 欢迎来到XDAGJ

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FXDagger%2Fxdagj.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_shield) ![](https://github.com/XDagger/xdagj/actions/workflows/maven.yml/badge.svg) ![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/XDagger/xdagj) ![GitHub](https://img.shields.io/github/license/XDagger/xdagj) ![GitHub issues](https://img.shields.io/github/issues/XDagger/xdagj)



## 目录
[TOC]

## 系统环境

```yaml
JDK   : v15
Maven : v3.6.3
```

## 安装与用法

XDAGJ教程可以让您快速加入并体验XDAGJ的钱包及挖矿功能，私有链搭建教程帮助您搭建一个独有的链上环境，能更好的测试并发现错误。

[XDAGJ测试网接入教程](./XDAJ_TestNet_Turial_zh.md)

[XDAJ私有链搭建教程](XDAGJ_PrivateChain_Turial_zh.md)

[区块浏览器](http://146.56.240.230/)

## 发展

XDAGJ线已经具备了一个矿池基本的功能，后续的工作将会以优化现有代码为主。结合业界较为优秀的技术不断改进现有的XDAG系统，与时俱进，不断为XDAG注入新鲜血液。

下一阶段的主要工作包括但不限于下述内容

- 优化XDAG共识流程以及同步协议
- 使用Libp2p替换当前的dnet网络，在系统稳定性允许的前提下逐步提高去中心化程度
- 开放API接口，提供黑盒测试
- 提供各类符合Web3j规范的RPC接口
- 为交易加入手续费，提高用户的参与度
- 利用BIPxx等规范改进现有公私钥及地址产生方式，提供一种更为方便且通用的密钥存储方式
- 添加快照功能，解决历史主块加载速度慢的问题
- 优化地址块结构，避免粉尘攻击



于此同时，我们也在积极的为XDAG更多的应用场景，包括但不限于以下内容

- 探索使用neo4j作为存储层的可能，提供一种可视化的DAG存储
- 探索一种在XDAG中加入虚拟机，实现智能合约的方法，提高系统的可用性
- 探索有效的跨链方案，打破XDAG的封闭生态



## 代码规范

- Git

  我们使用如下所描述的Git分支模型：

  - `master`为主分支，也是用于部署生产环境的分支，任何时间都不能直接修改代码
  - `develop`为开发分支，始终保持最新完成以及bug修复后的代码
  - `feature`为新功能分支，开发新功能时，以`develop`分支为基础，并且按照开发特性创建对应的`feature/xxx`分支
  - `release`为预上线分支，发布提测阶段，会release分支代码为基准提测。当有一组feature开发完成，首先会合并到develop分支，进入提测时会创建release分支。如果测试过程中若存在bug需要修复，则直接由开发者在release分支修复并提交。当测试完成之后，合并release分支到master和develop分支，此时master为最新代码，用作上线。
  - `hotfix`为修复线上紧急问题的分支，以`master`分支为基线，创建`hotfix/xxx`分支，修复完成后，需要合并到`master`分支和`develop`分支

- 提交信息

  提交消息必须以一个简短的主题行开始，然后是一个可选的、更详细的解释性文本，该文本与摘要以空行分隔

- Pull 请求

  pull request必须尽可能清晰和详细，包括所有相关的问题。如果拉请求是为了关闭一个问题，请使用Github的关键字约定[关闭，修复，或解决](https://help.github.com/articles/closing-issues-via-commit-messages/)。如果pull请求只完成问题的一部分，则使用`connected`关键字。这有助于我们的工具正确地将问题链接到拉出请求

- 代码风格

  在`misc/code-style`文件夹中使用`xdagj`代码风格的`formatter_eclipse.xml`或`formatter_intellij.xml`

- 代码评审

  我们重视代码的质量和准确性。因此，我们将审查所有需要更改的代码

## 贡献

- 安全问题

  XDAGJ仍旧处于大量开发的过程，这意味着现有的代码或者协议可能存在问题，或者实际中可能存在的错误。如果您发现安全问题，希望可以尽快的将其予以反馈。

  若果发现可能影响已部署系统安全性的问题，我们希望您能在私下将问题发送至邮箱XXXX.XX.com，请勿公开讨论。

  如果问题是协议的弱点或不回影响线上系统，可以公开讨论并发布至Issues。

- 新功能的添加

  我们非常乐意为XDAGJ添加更多实用且有意思的新功能，您可以谈论任何有意思的新功能。

如果您对XDAGJ的开发感兴趣，我们也欢迎您加入到开发者团队，为XDAGJ贡献一份您的力量


## 赞助

自阿波罗计划的提出至今，社区实现了XDAGJ由0到1的突破，XDAGJ的开发工作也逐渐步入正轨。但是由于资源的有限，您的支持能更好的帮助我们开发和完善XDAGJ

XDAG：+89Zijf2XsXqbdVK7rdfR4F8+RkHkAPh

## 其他
[XDAGJ_Libp2P介绍](./XDAGJ_Networking_Specification.md)
[XDAG WIKI](https://github.com/XDagger/xdag/wiki)  
XDAG白皮书     [English](https://github.com/XDagger/xdag/blob/master/WhitePaper.md)  |  [中文](https://github.com/XDagger/xdag/blob/master/WhitePaper%20zh-cn.md)
XDAG协议规范 [English](https://github.com/XDagger/xdag/blob/master/Protocol.md)  |  [中文](https://github.com/XDagger/xdag/blob/master/Protocol-cn.md)


## 执照

![FOSSA Status](README_zh.assets/git%252Bgithub.com%252FXDagger%252Fxdagj.svg)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_large)


