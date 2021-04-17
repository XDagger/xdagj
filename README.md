# Welcome to XDAGJ
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FXDagger%2Fxdagj.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_shield)
![](https://github.com/XDagger/xdagj/actions/workflows/maven.yml/badge.svg)


## About

XDAGJ is a Java implementation of XDAG. It is rebuilt on the basis of C Version XDAG. For more information, please check [XDagger](https://github.com/XDagger/xdag) .

Alpha version is being improved.

## Donation

XDAG：+89Zijf2XsXqbdVK7rdfR4F8+RkHkAPh

## Development environment

```yaml
JDK   : v15
Maven : 3.6.3
```

Make sure the above environment is installed before you build this project.



## Documentation

[XDAGJ_Documentation.md](./docs/XDAGJ_Documentation.md)

[XDAGJ_Roadmap](./docs/XDAGJ_Roadmap.md)

[XDAGJ_Networking_Specification](./docs/XDAGJ_Networking_Specification.md)

### for project
The generated files are stored in MainNet/TestNet during runtime. It depends on whether you access main network or the test network.
```yaml
* dnet_key.dat	 : Used to verify your password
* wallet_*.dat	 : Store your accounts' private key
* Rocksdb        : Storage blocks
* sumsStoreDir   : Blocks sums storage path
* storeDir       : Block information storage path (include block, account and orphan)   
```

## Contribute

This is an Open Source Project and we welcome all sorts of contributions, including reporting issues, contributing code, and helping us improve our community. Here are the instructions to get you started.

### Reporting Issues

Whether you find any errors, bugs or inconsistencies in the code or documents of the XDAGJ project, or have any other problems or suggestions, please let us know on the Issue section.

The main Issues for bug reporting are as follows:

* [XDAGJ/issues](https://github.com/XDagger/xdagj/issues)

## Implementation Design

When considering design proposals for implementations, we are looking for:

- A description of the problem this design proposal solves
- Discussion of the tradeoffs involved
- Discussion of the proposed solution

### Git

We use a simple git branching model:

- `master` must always work
- `develop` is the branch for development
- create feature-branches to merge into `develop`
- all commits must pass testing so that git bisect is easy to run

Just stay current with `develop` (rebase).

### Commit messages

Commit messages must start with a short subject line, followed by an optional, more detailed explanatory text which is separated from the summary by an empty line.

### Code

Write clean code. Universally formatted code promotes ease of writing, reading, and maintenance.

### Documentation

Update documentation when creating or modifying features. Test your documentation changes for clarity, concision, and correctness, as well as a clean documentation build.

### Pull Requests

The pull request must be as clear as possible and as detailed as possible, including all related issues. If the pull request is meant to close an issue please use the Github keyword conventions of [closes, fixes, or resolves](https://help.github.com/articles/closing-issues-via-commit-messages/). If the pull request only completes part of an issue use the connects keywords. This helps our tools properly link issues to pull requests.

### Code Style

use xdagj code style formatter_eclipse.xml or formatter_intellij.xml at misc/code-style folder import to your IDE for format code.


### Code Review

We value the quality and accuracy of the code. Therefor, we will review all the codes that need to be changed.

### Merge Approval

We use `Thank you for your efforts.` in comments on the code review to indicate acceptance. A change **requires** `Thank you for your efforts.` from the maintainers of each component affected. If you know whom it may be, ping them.




## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FXDagger%2Fxdagj.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FXDagger%2Fxdagj?ref=badge_large)