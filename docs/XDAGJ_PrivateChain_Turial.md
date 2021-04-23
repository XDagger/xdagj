# XDAGJ私有链搭建教程

[TOC]

## 系统环境与硬件要求

- 系统环境

  ```yaml
  JDK   : v15
  Maven : v3.6.3
  ```

  请确保您的操作系统中已经具备上述环境，其中JDK版本必须确保为15

- 硬件参数

  由于RandomX算法对内存要求较高，为了确保XDAGJ矿池的正常运行，请确保系统可用内存大于5.5G



## 构建XDAGJ客户端

- 下载源码

  ```shell
  git clone https://github.com/XDagger/xdagj.git
  ```

- 编译RandomX链接库

  ```shell
  cd src/c
  mkdir build && cd build
  cmake ..
  make
  ```

- 构建Jar包

  ```she
  #请先回退至xdagj根目录
  mvn clean package
  ```

- 运行矿池

  ```shell
  cd target
  nohup java -jar --enable-preview xdagj-0.4.0-shaded.jar > xdagj.log 2>&1 &
  #等待系统启动完毕，采用telnet接入
  telnet ip:port
  ```

  系统初始密码为123456



## 修改矿池参数

配置文件位于`src/main/resources/xdag.config`，具体的含义如下，不修改则启用默认配置。其中XDAGJ的白名单为可选模式，配置项为空则允许所有节点加入，限定后只允许对应的ip接入

```yaml
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



## 矿池用法

### 矿工接入

详见[接入测试网教程]

### 矿池启动参数

```yaml
-t              [作为测试网接入]
-f yourpath     [修改区块的存储路径 ]
-p ip:port      [暴露给对等矿池的连接，即白名单内的名单]
-P (CFG)
[设置矿池对应的参数; CFG is miners:maxip:maxconn:fee:reward:direct:fund
miners - 最大所能允许接入的矿工数量
maxip - 每一个ip所能接入的最大
maxconn - 相同地址允许接入的最大矿工数量
fee - 每产生一个主块矿池获得的奖励
reward - 奖励最大难度主块的矿工
direct - 给予参与挖矿的矿工奖励份额
fund - 基金会抽成比例
```

### 矿池命令行参数（待补充每一个命令）

- 查看您的XDAG地址
- 查看矿池网络状态
- 查看链上状态
- 查询主块信息
- 查询自身出块情况
- 查询余额
- 转账操作
- 查看区块详细信息
- 查看已链接的矿池信息
- 连接新矿池
- 查看已链接的矿工信息
- 断开与矿工的连接
- 退出



## 其他

至此，您已经可以使用XDAGJ构建一个属于您独有的私有链环境

您可以对现有的功能进行测试，寻找任何有可能令系统出错或者崩溃的错误。我们非常欢迎您在Issue中提出任何存在的问题或者改进的建议。