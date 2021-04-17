## Build XDAGJ

Download the source code from the master branch.

Configure a new library file for it.

```shell
> cd src/c
> make_so.sh  ### or for Mac OS or Linux
```



### for Win/Mac/Linux

##### Building an executable JAR

```shell
> git clone https://github.com/XDagger/xdagj.git
> cd xdagj
> mvn clean package 
```

## Run XDAGJ

In XDAGJ，`resource/conf.setting` is a global configuration file. If specified configuration parameters are not configured，the default parameters will be used.Refer [hutool](https://www.hutool.cn/docs/#/setting/%E6%A6%82%E8%BF%B0)

To avoid stack overflow, increase the STACK value of the JVM to at least 4096k when loading the test network data.

The startup method based on the default configuration file :
```shell
> nohup java -jar --enable-preview xdagj-0.4.0-shaded.jar > xdagj.log 2>&1 &
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

#### Commands currently implemented

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

Execution parameters :

```yaml
xdag> run                       [Run,start working]
xdag> account N                 [Check your accounrs ,N is optional ]
xdag> state                     [Check your pool state if you are connected to other pool]
xdag> stats                     [Check network status]
xdag> balance                   [Check you balance]
xdag> block A                   [Check the block info use hash or address]
xdag> mainblocks N              [Print list of N (20 by default) main blocks]
xdag> minedblocks N             [Print list of N (20 by default) main blocks mined by current pool]
xdag> keygen                    [Generate new private/public key pair and set it by default]
xdag> net -l                 [List connections]
xdag> net -c ip:port       [connect a new pool]
xdag> miners                    [Printf list of activite miners in our pool]
xdag> xfer N A                  [Transfer N XDAG to the address A]
xdag> disconnect all|ip:port    [all : disconnect all miners / ip:port  disconnect the specified miner]
xdag> exit                      [Terminate xdag process]
xdag> terminate                 [Terminate xdag process]
```