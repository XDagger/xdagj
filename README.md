# welcome to XDAGJ

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FXDagger%2Fxdagj.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_shield) ![](https://github.com/XDagger/xdagj/actions/workflows/maven.yml/badge.svg) ![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/XDagger/xdagj) ![GitHub](https://img.shields.io/github/license/XDagger/xdagj) ![GitHub issues](https://img.shields.io/github/issues/XDagger/xdagj)

[中文版](./docs/README_zh.md)


## Directory
  - [System environment](#system-environment)
  - [Installation and usage](#installation-and-usage)
  - [Develop](#develop)
  - [Code](#code)
  - [Contribution](#contribution)
  - [Sponsorship](#sponsorship)
  - [Other](#other)
  - [License](#license)

## System environment
```yaml
JDK   : v15
Maven : v3.6.3
```
## Installation and usage

XDAGJ TestNet Tutorial can help you quickly access XDAGJ Test net by using wallets and mining functions
And the Private Chain Building Tutorial helps you to build a private-chain for testing and finding bugs

[XDAGJ Testnet Access Tutorial](./docs/XDAGJ_TestNet_Access_Tutorial_eng.md)

[XDAGJ Private Chain Construction Tutorial](./docs/XDAGJ_Private_Chain_Tutorial_eng.md)

[Explorer](http://146.56.240.230/)

## Develop

XDAGJ already has the basic functions as a pool, and the follow-up work is to improve the stability of the system while optimizing the existing code.Timely absorb excellent blockchain technology, and continue to inject fresh blood into XDAG

The main work of the next stage includes but not limited

- Optimize the XDAG consensus process and synchronization protocol
- LibP2P is used to replace the  DNET network and gradually improve the degree of decentralization as long as the system stability allows
- Open API interface, provide black box testing
- Add a commission to the transaction
- Use BIPxx and other specifications to improve the existing public and private key and address generation methods, and provide a more convenient and universal key storage method
- Add snapshot function to solve the problem of slow loading speed of historical main block
- Optimize the address block structure to avoid dust attacks

At the same time, we are also actively providing more application scenarios for XDAG, including but not limited

- Explore the possibility of using neo4j as the storage layer to provide a visual DAG storage
- Explore a way to add a virtual machine to XDAG to implement smart contracts and improve the usability of the system
- Explore effective cross-chain solutions to break the closed ecology of XDAG

## Code

- Git

  We use the gitflow branch model

  - `master` is the main branch, which is also used to deploy the production environment. Cannot modify the code directly at any time
  - `develop` is the development branch, always keep the latest code after completion and bug fixes
  - `feature` is a new feature branch. When developing new features, use the `develop` branch as the basis, and create the corresponding `feature/xxx` branch according to the development characteristics
  - `release` is the pre-launch branch. During the release test phase, the release branch code will be used as the benchmark test. When a set of features is developed, it will be merged into the develop branch first, and a release branch will be created when entering the test. If there is a bug that needs to be fixed during the testing process, it will be directly fixed and submitted by the developer in the release branch. When the test is completed, merge the release branch to the master and develop branches. At this time, the master is the latest code and is used to go online
  - `hotfix` is the branch for repairing urgent problems on the line. Using the `master` branch as the baseline, create a `hotfix/xxx` branch. After the repair is completed, it needs to be merged into the `master` branch and the `develop` branch

- Commit Message

  The submission message must begin with a short subject line, followed by an optional, more detailed explanatory text, which is separated from the abstract by a blank line

- Pull Request

  The pull request must be as clear and detailed as possible, including all related issues. If the pull request is to close an issue, please use the Github keyword convention [close, fix, or resolve](https://help.github.com/articles/closing-issues-via-commit-messages/). If the pull request only completes part of the problem, use the `connected` keyword. This helps our tool to correctly link the issue to the pull request

- Code Style

  Use the `formatter_eclipse.xml` or `formatter_intellij.xml` of the `xdagj` code style in the `misc/code-style` folder

- Code Review

  We value the quality and accuracy of the code. Therefore, we will review all the code that needs to be changed

## Contribution

- Security Question

  XDAGJ is still in the process of large-scale development, which means that there may be problems with existing codes or protocols, or errors that may exist in practice. If you find a security problem, I hope you can give it back as soon as possible

  If you find a problem that may affect the security of the deployed system, we hope that you can send the problem privately to xdagj@xdag.io, please do not discuss it publicly

  If the problem is a weakness of the agreement or does not affect the online system, it can be discussed publicly and posted to [issues](https://github.com/XDagger/xdagj.git)

- features

  We are very happy to add more useful and interesting new features to XDAGJ, you can talk about any interesting new features

If you are interested in the development of XDAGJ, we also welcome you to join the developer team and contribute your strength to XDAGJ. You can get in touch with us at xdagj@xdag.io

## Sponsorship

Since the Apollo Project was proposed, XDAGJ has achieved a 0 to 1 breakthrough as a milestone.The development of XDAGJ has gradually been on the right track, but due to limited resources, we hope to get your help. Your support can better help us to develop and improve XDAGJ

XDAG：+89Zijf2XsXqbdVK7rdfR4F8+RkHkAPh

## Other

[XDAGJ Libp2P Introduce](./docs/XDAGJ_Networking_Specification.md)

[XDAG WIKI](https://github.com/XDagger/xdag/wiki)

[XDAG Whitepaper](https://github.com/XDagger/xdag/blob/master/WhitePaper.md)

[XDAG Protocol](https://github.com/XDagger/xdag/blob/master/Protocol.md)



## License

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FXDagger%2Fxdagj.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_large)

