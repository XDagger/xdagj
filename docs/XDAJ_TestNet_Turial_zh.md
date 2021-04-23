# XDAGJ测试网接入教程

现阶段XDAGJ只提供矿池功能，用户可以利用原有C语言版本的XDAG客户端钱包参与到测试环节中。本教程提供的环境非必需，用户可以根据自身操作系统执行对应的步骤。

- [XDAGJ测试网接入教程](#xdagj测试网接入教程)
  - [钱包接入教程](#钱包接入教程)
    - [MacOS/Linux命令行钱包](#macoslinux命令行钱包)
      - [MacOS](#macos)
      - [Ubuntu](#ubuntu)
    - [XDAG命令行客户端用法](#xdag命令行客户端用法)
    - [Windows 可视化钱包](#windows-可视化钱包)
  - [钱包备份与还原](#钱包备份与还原)
    - [备份](#备份)
    - [还原](#还原)
  - [挖矿教程](#挖矿教程)
    - [Linux系统为RandomX算法打开hugepage功能](#linux系统为randomx算法打开hugepage功能)
    - [MacOS / Linux](#macos--linux)
    - [Windows](#windows)
    - [通用问题](#通用问题)
  - [矿池地址](#矿池地址)
  - [其他](#其他)



## 钱包接入教程

### MacOS/Linux命令行钱包

MacOS 和 LInux平台目前没有可视化钱包，用户需要根据自身环境编译对应的客户端

**须知：由于RanndomX算法对系统内存要求较大，运行命令行钱包需要确保系统可用内存大于5G**

#### MacOS

系统版本：MacOS BigSur 11.2.3

- 安装依赖项

  ```shell
  brew install cmake openssl libtool gmp autoconf 
  ```

- RandomX依赖(首次编译该项目)

  ```shell
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

#### Ubuntu

系统版本 : Ubuntu20.04 LTS

- 安装依赖项

  ```shell
  apt-get install cmake gcc build-essential pkg-config libssl-dev libgmp-dev libtool libsecp256k1-dev librandomx-dev
  ```

- 为RandomX算法打开hugepage功能

  - 临时开启

  ```shell
  sudo sysctl -w vm.nr_hugepages=2560
  ```

  - 永久开启

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

### XDAG命令行客户端用法

- 链接矿池

  ```shell
  ./xdag -t -randomx f -m <挖矿线程> <矿池地址>:<矿池端口>
  ```

  -m 为可选项目，表示挖矿线程，默认为0，即不进行挖矿操作

- 第一次运行

  - 第一次运行系统会提示您`set password`，该密码用于转账以及解锁钱包信息，请务必牢记，密码一旦遗失将无法找回
  - `enter random characters`该字段为随机数种子，用于加强文件的随机性，不是密码
  - 生成您的钱包地址需要花费一些时间，请耐心等待直到`xdag>`字段出现

- 查看链接到矿池的状态

  ```shell
  xdag> state
  [展示目前网络状态]
  ```

- 查询余额

  ```she
  xdag> balance
  [显示您账户的所有余额]
  ```

- 显示XDAG地址

  ```sh
  xdag> account
  [显示该账户下所拥有的xdag地址]
  ```

- 转账操作

  ```shell
  xdag> xfer
  [xfer  金额  地址]
  ```

- 退出

  ```she
  xdag> terminate
  ```

- 更过命令行指令

  ```shell
  ./xdag -h
  #或
  xdag> help
  ```

  更多详细的信息，可以参考[XDAG]()

### Windows 可视化钱包

官网下载可视化钱包使用，[下载地址](https://xdag.io/zh/)

解压后打开`wallet-config.json`文件，修改`pool_address`为测试网矿池地址，并将`is_test_net`修改为`true`

## 钱包备份与还原

**强烈建议您将测试网和主网的钱包分开存放，避免意外导致主网钱包数据的丢失**

### 备份

- 建议您备份整个工作目录，并且以只读的方式存储
- 工作目录下的`wallet.dat`和`dnet_key.dat`为钱包文件，对其进行单独的备份以防意外丢失

**请务必牢记第一次运行时设置的密码，若密码遗失将无法正确解密钱包文件，无法找回对应的账户**

### 还原

- 请将上述备份文件存入编译后客户端所在同目录下，启动程序后系统会自动识别并且通过密码还原



## 挖矿教程

建议下载XDAG的专用挖矿软件[XdagRandomXMiner](https://github.com/XDagger/XdagRandomxMiner/releases/tag/0.4.1 )

**须知1：使用挖矿软件，一个矿工需要占用2.5G的运行内存，该内存与矿工数量呈线性关系增长，若使用多个矿工，需要确保开启的内存页为1280*对应矿工数量**

**须知2：请确保钱包地址已经在XDAG网络上被确认，否则无法进行挖矿操作**

### Linux系统为RandomX算法打开hugepage功能

- 临时开启

  ```shell
  sudo sysctl -w vm.nr_hugepages=1280
  ```

- 永久开启

  ```shell
  sudo bash -c "echo vm.nr_hugepages=1280 >> /etc/sysctl.conf"
  ```

### MacOS / Linux

- 启动命令

  ```shell
  DaggerMiner -cpu  -T -p <矿池地址:端口> -t <挖矿线程数> -a <钱包地址>
  ```

### Windows

请参考[Win10配置RandomX](./Win10_RandomX.md)或者[Enable the Lock Pages in Memory Option (Windows)](https://msdn.microsoft.com/en-gb/library/ms190730.aspx)打开hugepage

- 启动命令

  ```shell
  DaggerMiner.exe -cpu -T -p <矿池地址:端口> -t <挖矿线程数> -a <钱包地址> 
  ```

- 常见问题

  - 缺少VC++依赖库

    下载地址：[VC++运行依赖库](https://download.visualstudio.microsoft.com/download/pr/89a3b9df-4a09-492e-8474-8f92c115c51d/B1A32C71A6B7D5978904FB223763263EA5A7EB23B2C44A0D60E90D234AD99178/VC_redist.x64.exe)

  - 缺少.Net依赖库

    下载地址：[.Net运行库](http://info.xdagmine.com/dotNetFx40_Full_x86_x64.exe)

### 通用问题

挖矿显示`Dataset allocation failed`，确定hugepage已成功配置，否则请确保系统内存足够支撑一个矿工所需的内存

## 矿池地址

上海:  1.15.78.91:9992



## 其他

现在您已经可以接入XDAGJ测试网络并进行转账功能了，同时可以在浏览器中查看一下你想要知道的信息

我们欢迎您将使用过程发生的错误或者其他一切可以帮助我们完善项目的信息，您可以通过[Issues](https://github.com/XDagger/xdagj/issues)向我们反馈

如果您有其他疑问，或者希望我们提供更多的教程，也可以在[Issues](https://github.com/XDagger/xdagj/issues)中进行提问