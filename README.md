********# Welcome to XDAGJ

## About

XDAGJ is a Java implementation of XDAG. It is rebuilt on the basis of C Version XDAG. For more information, please check [XDagger](https://github.com/XDagger/xdag) .

Alpha version is being improved.
## Development environment

```yaml
JDK   : v1.8
Maven : 3.6.3
```

Make sure the above environment is installed before you build this project.



## Documentation

[XDAGJ_Documentation.md](./XDAGJ_Documentation.md)

### for project
The generated files are stored in MainNet/TestNet during runtime. It depends on whether you access main network or the test network.
```yaml
* dnet_key.dat	 : Used to verify your password
* wallet_*.dat	 : Store your accounts' private key
* Rocksdb        : Storage blocks
* sumsStoreDir   : Blocks sums storage path
* storeDir       : Block information storage path (include block, account and orphan)   
```

## Build XDAGJ

Download the source code from the master branch.

Configure a new library file for it.

```shell
> cd src/c
## for Windows
> make_so.bat  
## for Mac OS or Linux
> make_so.sh
```



### for Win/Mac/Linux

##### Building an executable JAR

```shell
> git clone https://github.com/XDagger/xdagj.git
> cd xdagj
> mvn clean package 
```

## Run XDAGJ

In XDAGJ，`resource/xdag.config` is a global configuration file. If specified configuration parameters are not configured，the default parameters will be used.Refer [hutool](https://www.hutool.cn/docs/#/setting/%E6%A6%82%E8%BF%B0)

To avoid stack overflow, increase the STACK value of the JVM to at least 4096k when loading the test network data.

The startup method based on the default configuration file :
```shell
> java -jar xdagj.jar
> run ##start the program
> terminate ##trun off
```

Startup method with the appointed configuration file:

```shell	
> java -jar xdagj.jar -f [file storage path] -t -p [ip：port]  -P [CFG]
```

For more commands:

```shell
> java -jar xdagj.jar -h
```

#### Build test enviroment
Refer [How to build a test environment](./docs/Build_Test_Environment.md)

#### Telnet Server and Commands currently implemented

start para:

```yaml
-t              [Use testnet]
-f yourpath     [Change the file storage path to yourpath ]
-p ip:port      [Bind ip:port as whitelist address]
-P (CFG)
                [set pool config; CFG is miners:maxip:maxconn:fee:reward:direct:fund
                  miners - maximum allowed number of miners
                  maxip - maximum allowed number of miners connected from single ip
                  maxconn - maximum allowed number of miners with the same address
                  fee - pool fee in percent
                  reward - reward to miner who got a block in percent
                  direct - reward to miners participated in earned block in percent
                  fund - community fund fee in percent
```
Telnet Server:

IP and Port in xdag.conf
```yaml
root@localhost xdagj % telnet 127.0.0.1 7001
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
Enter password> ******
xdag> help
    account     print first [SIZE] (20 by default) our addresses with their amounts
    balance     print balance of the address [ADDRESS] or total balance for all our addresses
    block       print extended info for the block corresponding to the address or hash [A]
    disconnect  disconnect all connections or specified miners
    exit        exit from app/script
    help        command help
    keygen      generate new private/public key pair and set it by default
    lastblocks  print latest [SIZE] (20 by default, max limit 100) main blocks
    mainblocks  print latest [SIZE] (20 by default, max limit 100) main blocks
    minedblocks print list of [SIZE] (20 by default) main blocks mined by current pool
    miners      for pool, print list of recent connected miners
    net         run transport layer command, try 'net --help'
    run         run libp2pNode after loading local blocks if option -r is used
    state       print the program state
    stats       print statistics for loaded and all known blocks
    terminate   terminate both daemon and this program
    ttop        display and update sorted information about threads
    xfer        transfer [AMOUNT] XDAG to the address [ADDRESS]
```

### Libp2p and Discovery

---

The discovery module is currently enabled by default, Non-seed nodes will automatically connect to running seed nodes.If you need to modify the seed node, you can modify the xdag.config and 

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


