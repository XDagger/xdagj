# XDAGJ说明

[TOC]

## XDAGJ矿池接入

### 钱包

#### MacOS / Linux

##### 从C源码构建客户端

现阶段XDAGJ只提供矿池功能，用户可以采用编译C语言版本的XDAG客户端来进行接入

###### MacOS平台

- 安装依赖项目

  ```shell
  brew install cmake openssl libtool gmp autoconf 
  ```

- RandomX依赖

  ``` shell
  git clone https://github.com/tevador/RandomX.git
  cd RandomX
  mkdir build && cd build
  cmake -DARCH=native ..
  make
  sudo make install
  ```

- 下载源码

  ```shell
  git clone https://github.com/XDagger/xdag.git
  ```

- 编译libsecp256k1(首次编译该项目)

  ```shell
  cd xdag/secp256k1
  ./autogen.sh
  ./configure
  make
  ./tests
  sudo make install
  ```

- 构建XDAG客户端

  ```shell
  mkdir build && cd build
  cmake .. -DBUILD_TAG=mac
  make
  ```

###### Ubuntu平台

- 安装依赖项

  ```shell
  apt-get install cmake gcc build-essential pkg-config libssl-dev libgmp-dev libtool libsecp256k1-dev librandomx-dev
  ```

  **gcc版本不低于4.9**

- 为RandomX算法打开huge pages功能

  临时开启

  ```shell
  sudo sysctl -w vm.nr_hugepages=2560
  ```

  永久开启

  ```shell
  sudo bash -c "echo vm.nr_hugepages=2560 >> /etc/sysctl.conf"
  ```

- 下载源码

  ```shell
  git clone https://github.com/XDagger/xdag.git
  ```

- 构建XDAG客户端

  ```shell
  cd xdag
  mkdir build && cd build
  cmake .. 
  make
  ```

###### CentOS

- 安装依赖项

  ```shell
  yum install cmake gcc pkg-config openssl gmp-devel libtool ncurses-devel gcc-c++
  ```

  **gcc版本不低于4.9**

- 为RandomX算法打开huge pages功能

  临时开启

  ```shell
  
  ```

  永久开启

  ```shell
  
  ```

- 下载源码

  ```shell
  git clone https://github.com/XDagger/xdag.git
  ```

- 构建XDAG客户端

  ```shell
  
  ```

###### 

##### 用法

- 连接至矿池

  ```shell
  ./xdag -t -randomx f -m <挖矿线程> <矿池地址>:<矿池端口>
  ```

  挖矿线程为0即不进行挖矿操作

- 第一次运行

  - 第一次运行系统会提示您`set password`，该密码用于转账以及解锁钱包信息，请务必牢记，密码一旦遗失将无法找回
  - `enter random characters`该字段为随机数种子，用于加强文件的随机性，不是密码
  - 生成您的钱包地址需要花费一些时间，请耐心等待直到`xdag>`字段出现

- 获取连接到矿池的状态

  ```shell
  xdag> state
  [展示目前网络状态]
  ```

- 查询余额

  ```shell
  xdag> balance
  [显示您账户的所有余额]
  ```

- 显示您的XDAG地址

  ```shell
  xdag> account
  [显示该账户下所拥有的xdag地址]
  ```

- 转账操作

  ```shell
  xdag> xfer
  [xfer  金额  地址]
  ```

- 退出

  ```shell
  xdag> terminate
  ```

- 更多命令行指令

  ```shell
  ./xdag -h
  #或
  xdag> help
  ```

#### Widnows平台

官网下载可视化钱包使用，[下载地址](https://xdag.io/zh/)

解压后打开`wallet-config.json`文件，修改`pool_address`为测试网矿池地址，并将`is_test_net`修改为`true`

### 挖矿

#### MacOS / Linux

可以从[这里](https://github.com/XDagger/XdagRandomxMiner/releases)下载xdag的专用挖矿软件[DaggerRandomxMine](https://github.com/XDagger/XdagRandomxMiner)

##### Linux为RandomX算法打开hugepages功能（MacOS忽略）

- 临时开启，设备重启后失效

```shell
sudo sysctl -w vm.nr_hugepages=1280
```

- 永久开启，重启不失效

```shell
sudo bash -c "echo vm.nr_hugepages=1280 >> /etc/sysctl.conf"
```

##### 用法

```shell
DaggerMiner -cpu -p <矿池地址:端口> -t <挖矿线程数> -a <钱包地址>
```

#### Windows

请参考[Enable the Lock Pages in Memory Option (Windows)](https://msdn.microsoft.com/en-gb/library/ms190730.aspx)打开hugepage

##### 用法

```shell
DaggerMiner.exe -cpu -p <矿池地址:端口> -t <挖矿线程数> -a <钱包地址> 
```



==一个矿工需要占用1280个内存页来使用RandomX算法，意味着每一个矿工将占用2.5G的内存空间，请确保计算机拥有至少4Gb的内存才能用于挖矿==

### 备份与还原

- 备份操作

  - 建议您备份整个工作目录，并且以只读的方式存储
  - `wallet.dat`和`dnet_key.dat`为钱包文件，请确保 

  **请务必牢记第一次运行设置的密码，若密码遗失将将无法正确解密钱包文件，无法还原**

- 还原操作

  - 请将上述备份文件存入编译后客户端所在同目录下，启动程序后系统会自动识别并且通过密码还原



### 矿池地址

南京: 146.56.240.230:8882

新加坡: 51.79.222.25:8882



## 搭建私有网络

- 下载源码

```shell
git clone https://github.com/XDagger/xdagj.git
```

- 编译运行所需要的库

```shell
cd src/c
sh make_lib.sh
```

- 配置文件

配置文件位于`src/main/resources/xdag.config`，具体的含义如下，不修改则启用默认配置。其中XDAGJ的白名单为可选模式，配置项为空则允许所有节点加入，限定后只允许对应的ip接入。

```shell
#链接设置
telnetIP && telnetPort  #用于绑定telnet服务的ip和端口
nodeIP && nodePort 			#暴露给对等矿池的ip和端口
poolIP && poolPort			#矿工接入挖矿的ip和端口

#奖励设置
poolRation							#挖矿矿池抽成比例(1-100)
rewardRation						#出块矿工奖励比例(1-100)
fundRation							#基金会抽成比例(1-100)
directRation						#参与奖励比例(1-100)

#矿工限制
globalMinerLimit				#矿池最大允许接入矿工数量
maxConnectPerIP					#相同ip地址允许最多的接入矿工数
maxMinerPerAccount			#相同钱包账户允许最多的接入矿工数

#白名单配置
whiteIPs								#允许链接的对等矿池节点，形式为ip:port，用‘，’隔开
```

- 运行矿池 

```shell
mvn package 
cd target
nohup java -jar --enable-preview xdagj-0.4.0-shaded.jar > xdagj.log 2>&1 &
telnet ip port
```



## 捐赠地址

XDAG：+89Zijf2XsXqbdVK7rdfR4F8+RkHkAPh

