# XDAGJ Private Chain Construction Tutorial


  - [System environment and hardware requirements](#system-environment-and-hardware-requirements)
  - [Build](#build)
  - [Modify pool parameters](#modify-pool-parameters)
  - [Usage](#usage)
    - [Miner access](#miner-access)
    - [Pool startup parameters](#pool-startup-parameters)
    - [Pool command line](#pool-command-line)
  - [Other](#other)
## System environment and hardware requirements

- System environment

  ```yaml
  JDK   : v17
  Maven : v3.8.3
  ```

  Please make sure that the above environment is already available in your operating system, and the JDK version must be 15

- Hardware parameters

  **Since the RandomX algorithm has high memory requirements, in order to ensure the normal operation of the XDAGJ mining pool, please ensure that the available memory of the system is greater than 5.5G**

## Build

- Download source code

  ```shell
  git clone https://github.com/XDagger/xdagj.git
  ```

- Compile RandomX link library

  ```shell
  cd src/c
  mkdir build && cd build
  cmake ..
  make
  ```

- Build the Jar package

  ```shell
  #Please go back to the xdagj root directory first
  mvn clean package
  ```

- Run

  ```shell
  cd target
  nohup java -jar xdagj-0.4.5-shaded.jar > xdagj.log 2>&1 &
  #Wait for the system to start up, use telnet to access
  telnet ip:port
  ```

  The initial system password is 123456



## Modify pool parameters

The configuration file is located in `src/main/resources/xdag-testnet.config`, the specific meaning is as follows, if you do not modify it, the default configuration is enabled. The whitelist of XDAGJ is an optional mode. If the configuration item is empty, all nodes are allowed to join, and only the corresponding ip access is allowed after restriction

```yaml
#Connection settings
telnetIP && telnetPort    #The ip and port used to bind the telnet service
nodeIP && nodePort        #The ip and port exposed to other pool
poolIP && poolPort        #The ip and port for miners to access mining

#Reward settings
poolRation                #Proportion of mining pool (1-100)
rewardRation              #Reward ratio of block miners (1-100)
fundRation                #Foundation draw ratio (1-100)
directRation              #Participation reward ratio (1-100)

#Miner restrictions
globalMinerLimit          #Maximum number of miners allowed in the mining pool
maxConnectPerIP           #The same ip address allows the maximum number of access miners
maxMinerPerAccount        #The maximum number of miners allowed for the same wallet account

#Whitelist configuration
whiteIPs                  #The mining pool nodes that are allowed to connect, in the form of ip:port, separated by ‘,’
```



## Usage

### Miner access

For details, please refer to [XDAGJ Access Test Net Tutorial](./XDAGJ_TestNet_Access_Tutorial_eng.md)

### Pool startup parameters

```yaml
-t                      [Access as a testnet]
-f yourpath             [Modify the storage path of the block]
-p ip:port              [The connection exposed to the peer-to-peer mining pool, that is, the list in the whitelist]
-P (CFG)                [Set pool parameters; CFG is miners:maxip:maxconn:fee:reward:direct:fund
   miners               - Maximum number of miners allowed to access
   maxip                - The maximum that each ip can access
   maxconn              - The maximum number of miners allowed to access the same address
   fee                  - Reward for each generation of a main block
   reward               - Reward the miner with the most difficult master block
   direct               - Give reward shares to miners participating in mining
   fund                 - Foundation draw ratio
```

### Pool command line 

- Show XDAG address

  ```she
  xdag>account [N]
  [N is optional, display N account information, default 20]
  ```

- Check the network status of the pool

  ```shell
  xdag>state
  [Display the connection information of the pool, whether it is connected to other pools]
  ```

- Show chain status

  ```shell
  xdag>stats
  [View statistics on the current chain]
  ```

- Query main block information

  ```shell
  xdag>mainblocks [N]
  [N is optional, display the latest N main block information, the default is 20]
  ```

- Query its own block production

  ```shell
  xdag>minedblocks
  [N is optional, display the latest N locally generated block information, the default is 20]
  ```

- Check balances

  ```shell
  xdag>balance
  [Show your own balance]
  ```

- Transfer

  ```shell
  xdag>xfer amount addressto
  [Transfer amount of XDAG to account addressto]
  ```

- Check block details

  ```shell
  xdag>block  blockhash
  [Query the detailed information of the block whose hash is blockhash]
  ```

- Show the linked mining pool information

  ```shell
  xdag>net -l
  ```

- Connect to a new pool

  ```shell
  xdag>net -c IP:Port
  [Connect to other pools]
  ```

- Show the linked miner information

  ```shell
  xdag>miners
  ```

- Drop out

  ```shell
  xdag>terminate
  ```



## Other

At this point, you can use XDAGJ to build a private chain environment unique to you

You can test the existing functions and look for any errors that may cause the system to fail or crash. We welcome you to put forward any existing problems or suggestions for improvement in [Issue](https://github.com/XDagger/xdagj/issues)


