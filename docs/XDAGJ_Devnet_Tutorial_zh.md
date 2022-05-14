# XDAGJ私有链搭建教程


  - [系统环境与硬件要求](#系统环境与硬件要求)
  - [构建XDAGJ客户端](#构建XDAGJ客户端)
  - [修改矿池参数](#修改矿池参数)
  - [矿池用法](#矿池用法)
    - [矿工接入](#矿工接入)
    - [矿池启动参数](#矿池启动参数)
    - [矿池命令行参数](#矿池命令行参数)
  - [其他](#其他)

## 系统环境与硬件要求

- 系统环境

  ```yaml
  JDK   : v17
  Maven : v3.8.3
  ```

  请确保您的操作系统中已经具备上述环境，其中JDK版本必须确保为17，maven 版本必须 > 3.8，否则无法与 JDK17 兼容

- 硬件参数

  **由于RandomX算法对内存要求较高，为了确保XDAGJ矿池的正常运行，请确保系统可用内存大于5.5G**



## 构建XDAGJ客户端

## UNIX

- 下载源码

  ```shell
  git clone https://github.com/XDagger/xdagj.git
  ```

- 安装依赖
### MacOS
  ```shell
  brew install cmake openssl libtool gmp autoconf 
  
  ```
### Linux(Ubuntu)
  ```shell
  apt-get install cmake gcc build-essential pkg-config libssl-dev libgmp-dev libtool libsecp256k1-dev librandomx-dev
  ```

- 在当前目录下进入 script 文件夹执行编译脚本

  ```shell
  cd  script
  ./xdag.sh -d
  ```

  命令说明

  ```shell
  -t 使用测试网环境
  -m 使用主网环境
  -D 跳过 mvn clean package 时的测试任务
  ```

  **首次运行 xdagj 的时候，建议先执行 xdag.sh ，确保程序可以正常启动后再根据自身的需要进行配置内容的替换**

  Xdag.sh 会对 xdagj 项目进行构建，构建成功之后会创建在工程根目录下创建一个 pool 的文件夹，用于存放程序运行时所需的配置文件以及数据。

- 非首次启动 xdagj，或者想直接直接后台启动，可以在 pool 文件夹下找到对应的 xdagj-{version}-shaded.jar 包单独运行

  ```shell
  cd pool
  nohup java -jar xdagj-0.4.8-shaded.jar > xdagj.log 2>&1 &
  #等待系统启动完毕，采用telnet接入
  telnet 127.0.0.1:6001
  ```

  telnet 默认密码为 `ADMIN_TELNET_PASSWORD`，可在配置文件中自行修改。具体参数含义见下节

## Window

TODO



## 修改矿池参数

配置文件位于`src/main/resources/xdag-xxx.config`，打包后位于程序的根目录下（./pool）具体的含义如下，不修改则启用默认配置。其中XDAGJ的白名单为可选模式，配置项为空则允许所有节点加入，限定后只允许对应的ip接入

- xdag-devnet.config 为例

```yaml
# 管理员控制
admin.telnet.port            # telnet 远程连接 ip，默认为 127.0.0.1
admin.telnet.port            # telnet 远程连接端口，默认为 6001
admin.telnet.password	   # telnet 远程连接密码

# 矿池设置（适用于矿工连接）
pool.ip                         # 矿工连接的 ip，默认为 127.0.0.1
pool.port                     # 矿工连接地址对应的端口，默认为 7001
pool.tag                      # 矿池标识，即矿池 identifier

# 奖励设置
poolRation                 # 挖矿矿池抽成比例(1-100)，默认为 5
rewardRation             # 出块矿工奖励比例(1-100)，默认为 5
fundRation                # 基金会抽成比例(1-100)，默认为 5
directRation              # 参与奖励比例(1-100)，默认为 5

# 节点设置（用于矿池之间的相互连接）
node.ip                                          # 矿池之间相互连接的 ip，默认为 127.0.0.1
node.port                                      # 矿池之间相互连接的端口，默认为 8001
node.maxInboundConnectionsPerIp  # 矿池之间允许入站连接数，默认为 8
node.whiteIPs                               # 白名单列表，可选。在该列表内的 ip 才允许被连接

# Node RPC Config
rpc.enabled              # 是否开启 RPC 功能，默认为 true
rpc.http.host            # rpc 地址，默认为 127.0.0.1
rpc.http.port            # rpc http 端口，默认为 10001
rpc.ws.port             # rpc websocket 端口，默认为 10002

# 矿工限制
miner.globalMinerLimit               # 矿池最大允许接入矿工数量
miner.globalMinerChannelLimit   # 矿池最大允许接入连接数
miner.maxConnectPerIp             # 相同ip地址允许最多的接入矿工数
miner.maxMinerPerAccount       #相同钱包账户允许最多的接入矿工数
```



## 矿池用法

### 矿工接入

详见[接入测试网教程](XDAGJ_TestNet_Tutorial_zh.md)

### 矿池启动参数

```yaml
-t                      [作为测试网接入]
-f yourpath             [修改区块的存储路径 ]
-p ip:port              [暴露给对等矿池的连接，即白名单内的名单]
-P (CFG)                [设置矿池对应的参数; CFG is miners:maxip:maxconn:fee:reward:direct:fund
   miners               - 最大所能允许接入的矿工数量
   maxip                - 每一个ip所能接入的最大
   maxconn              - 相同地址允许接入的最大矿工数量
   fee                  - 每产生一个主块矿池获得的奖励
   reward               - 奖励最大难度主块的矿工
   direct               - 给予参与挖矿的矿工奖励份额
   fund                 - 基金会抽成比例
```

### 矿池命令行参数

- 查看您的XDAG地址

  ```she
  xdag>account [N]
  [N为可选项，显示N个账户信息，默认20]
  ```

- 查看矿池网络状态

  ```shell
  xdag>state
  [显示矿池的连接信息，是否连接上其它对等矿池]
  ```

- 查看链上状态

  ```shell
  xdag>stats
  [查看当前链上的统计信息]
  ```

- 查询主块信息

  ```shell
  xdag>mainblocks [N]
  [N为可选项，显示最新N个主块信息，默认20]
  ```

- 查询自身出块情况

  ```shell
  xdag>minedblocks
  [N为可选项，显示最新N个本地产生的区块信息，默认20]
  ```

- 查询余额

  ```shell
  xdag>balance
  [显示自身的余额]
  ```

- 转账操作

  ```shell
  xdag>xfer amount addressto
  [转amount个金额的XDAG至地址为addressto的账户]
  ```

- 查看区块详细信息

  ```shell
  xdag>block  blockhash
  [查询哈希为 blockhash 的区块的详细信息]
  ```

- 查看已链接的矿池信息

  ```shell
  xdag>net -l
  ```

- 连接新矿池

  ```shell
  xdag>net -c IP:Port
  [连接对等节点，添加矿池]
  ```

- 查看已链接的矿工信息

  ```shell
  xdag>miners
  ```

- 退出

  ```shell
  xdag>terminate
  ```



## 其他

至此，您已经可以使用XDAGJ构建一个属于您独有的私有链环境

您可以对现有的功能进行测试，寻找任何有可能令系统出错或者崩溃的错误。我们非常欢迎您在[Issue](https://github.com/XDagger/xdagj/issues)中提出任何存在的问题或者改进的建议

