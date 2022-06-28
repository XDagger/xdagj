# XDAGJ Devnet Construction Tutorial


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
  Maven : v3.8.4
  ```

  Please make sure that the above environment is already available in your operating system, and the JDK version must be 17

- Hardware parameters

  **Since the RandomX algorithm has high memory requirements, in order to ensure the normal operation of the XDAGJ mining pool, please ensure that the available memory of the system is greater than 5.5G**

## Build
 
- Download source code

  ```shell
  $git clone https://github.com/XDagger/xdagj.git
  ```
### MacOS
  ```shell
  brew install cmake openssl libtool gmp autoconf 
  
  ```
### Linux(Ubuntu)
  ```shell
  apt-get install cmake gcc build-essential pkg-config libssl-dev libgmp-dev libtool libsecp256k1-dev librandomx-dev
  ```

- Compile RandomX link library

  ```shell
  $cd src/c
  $mkdir build && cd build
  $cmake ..
  $make
  ```

- Build the Jar package

  ```shell
  #Please go back to the xdagj root directory first
  $mvn clean package (-Dmaven.test.skip=true)
  ```

- Run

  ```shell
  $mkdir run 

  #Please copy xdag-devnet.config, xdag.sh & xdagj-0.4.9-shaded.jar into /run
  # xdag-devnet.config is in /src-main-resources; xdag.sh is in /script; xdagj-0.4.9-shaded.jar is in /target

  $cd run
  $sh xdag.sh -d
  
  #Wait for the system to start up, use telnet to access
  telnet ip:port
  ```




## Modify pool config

The configuration file is located in `src/main/resources/xdag-devnet.config`, the specific meaning is as follows, if you do not modify it, the default configuration is enabled. The whitelist of XDAGJ is an optional mode. If the configuration item is empty, all nodes are allowed to join, and only the corresponding ip access is allowed after restriction

```yaml
# Admin Config
admin.telnet.ip = 127.0.0.1    #The ip used to bind the telnet service
admin.telnet.port = 6001       #The port used to bind the telnet service
admin.telnet.password = xxxxx  #The password used to bind the telnet service

# Pool Config
pool.ip = 127.0.0.1            #The ip used to bind the Mining Pool service
pool.port = 7001               #The port used to bind the Mining Pool service
pool.tag = XdagJ-01            #The tag used to bind the Mining Pool service

# Pool-Reward Config
pool.poolRation = 5            #Proportion of mining pool (1-100)
pool.rewardRation = 5          #Reward ratio of block miners (1-100)
pool.fundRation = 5            #Foundation draw ratio (1-100)
pool.directRation = 5          #Participation reward ratio (1-100)

# Node config
node.ip = 127.0.0.1            #The ip used to bind the Full XdagJ Node service
node.port = 8001               #The port used to bind the Full XdagJ Node service
node.maxInboundConnectionsPerIp = 8 #The max Inbound Connection used to bind the Full XdagJ Node service
node.whiteIPs = 127.0.0.1:8001,127.0.0.1:8002 #The white IP list used to bind the Full XdagJ Node service(separated by ‘,’)

# Node libp2p Config
node.libp2p.port = 9001        #The port for libp2p used to bind the Full XdagJ Node service
node.libp2p.isbootnode = true  #The isboot for libp2p used to bind the Full XdagJ Node service
node.libp2p.privkey = 0x0802122074ca7d1380b2c407be6878669ebb5c7a2ee751bb18198f1a0f214bcb93b894b5 #The privkey for libp2p used to bind the Full XdagJ Node service
node.libp2p.bootnode = enr:-Iu4QPY6bYDC0PaafEwhgg_6yTcx0GAGbSARYqehJKEkyOmxX6SNZMyMMdkmDw9bAvYN9m2LrqIsPSd-bUqff0tsHYABgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQJ2EWgMpl6PtyFKMbbOb82Ob-al9NeE3GYB3-K7n4yWwoN0Y3CCJxGDdWRwgicR #The libp2p bootnode used to bind the Full XdagJ Node service

# Node RPC Config
rpc.enabled = true             #The switch for XdagJ rpc service
rpc.http.host = 127.0.0.1      #The ip used to bind the XdagJ rpc service
rpc.http.port = 10001          #The port used to bind the XdagJ rpc service
rpc.ws.port = 10002            #The websocket port used to bind the XdagJ rpc service

# Miner Config
miner.globalMinerLimit = 8192        #The maximum number of miners allowed in the mining pool
miner.globalMinerChannelLimit = 8192 #The maximum number of miners channel for same ip address
miner.maxConnectPerIp = 256          #The maximum number of miners allowed for the same ip address
miner.maxMinerPerAccount = 256       #The maximum number of miners allowed for the same wallet account
```

### Create a wallet

A `wallet.data` file that stores a new private key should be automatically created under folder `./wallet` during
the startup of a fresh install of Xdagj wallet. This file is encrypted by a password specified in a CLI/GUI prompt during
the first time of wallet startup.

```bash
$ ./xdag.sh -d --account init
Create New Wallet...
EnterNewPassword:
ReEnterNewPassword:
HdWallet Initializing...
HdWallet Mnemonic:all inject weapon orchard daring rotate frown dirt enable attitude crystal hen
HdWallet Mnemonic Repeat:all inject weapon orchard daring rotate frown dirt enable attitude crystal hen
HdWallet Initialized Successfully!

$ ./xdag.sh -d --account create
Please Enter Wallet Password:
New Address:6d055aeb022a0a400e49dffc72beceebb3b2b92e
PublicKey:0x4cd5d6d5fd2b32f884e497548263aff394e51c1e9fd801ffad4c7545f6b0e0d6fbc30393512a23811889228bd97bf851bfc935e3c9d754295ef9331e656085e8
```

### Automatic wallet unlock

The standard wallet.data file of Xdagj is always encrypted even if you entered an empty password during wallet creation.
Therefore, a wallet password is required to be provided for automatic unlock when you set up a full node.

The following ways are available for automatic wallet unlock which will be applied in sequence:

1. Set `--password` CLI option as your wallet password when starting `xdagj.sh` executable. This is considered as an
   insecured way as the way will expose your wallet password to all users through process monitor.
2. Set `XDAGJ_WALLET_PASSWORD` environment variable as your wallet password.

### Create a systemd service

A systemd service unit can be created at `/etc/systemd/system/xdagj.service` using this template file:
[xdagj.service](../misc/systemd/xdagj.service).

```bash
sudo cp xdagj.service /etc/systemd/system/xdagj.service
sudo systemctl daemon-reload
sudo systemctl enable xdagj.service
sudo systemctl start xdagj.service
```


## Usage

### Miner access

For details, please refer to [XDAGJ Access Test Net Tutorial](./XDAGJ_TestNet_Tutorial_en.md)

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


