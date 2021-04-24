# XDAGJ Test Network Access Tutorial

  - [Wallet Access Tutorial](#wallet-access-tutorial)
    - [MacOS/Linux Command Line Wallet](#macoslinux-command-line-wallet)
      - [MacOS](#macos)
      - [Ubuntu](#ubuntu)
    - [Usage](#usage)
    - [Windows Visual Wallet](#windows-visual-wallet)
  - [Wallet Backup and Restore](#wallet-backup-and-restore)
    - [Backup](#backup)
    - [Recover](#recover)
  - [Mining Tutorial](#mining-tutorial)
    - [Linux system turns on hugepage for RandomX algorithm](#linux-system-turns-on-hugepage-for-randomx-algorithm)
    - [MacOS / Linux](#macos--linux)
    - [Windows](#windows)
    - [General question](#general-question)
  - [Pool Address](#pool-address)
  - [Other](#other)


At this stage, XDAGJ only provides pool functions, and users can use the original C language version of the XDAG client wallet to participate in the test. The environment provided in this tutorial is not necessary, and users can perform the corresponding steps according to their own operating system



## Wallet Access Tutorial

### MacOS/Linux Command Line Wallet

Currently there is no visual wallet on MacOS and LInux platforms, users need to compile the corresponding client according to their own environment

**Note: Because the RanndomX algorithm requires a large amount of system memory, running a command line wallet needs to ensure that the available memory of the system is greater than 5G**

#### MacOS

System version：MacOS BigSur 11.2.3

- Install dependencies

  ```shell
  brew install cmake openssl libtool gmp autoconf 
  ```

- RandomX dependency (compile the project for the first time)

  ```shell
  git clone https://github.com/tevador/RandomX.git
  cd RandomX
  mkdir build && cd build
  cmake -DARCH=native ..
  make
  sudo make install
  ```

- Download source code

  ```shell
  git clone https://github.com/XDagger/xdag.git
  ```

- Compile libsecp256k1 (compile the project for the first time)

  ```shell
  cd xdag/secp256k1
  ./autogen.sh
  ./configure
  make
  ./tests
  sudo make install
  ```

- Build XDAG client

  ```shell
  mkdir build && cd build
  cmake .. -DBUILD_TAG=mac
  make
  ```

#### Ubuntu

System version : Ubuntu20.04 LTS

- Install dependencies

  ```shell
  apt-get install cmake gcc build-essential pkg-config libssl-dev libgmp-dev libtool libsecp256k1-dev librandomx-dev
  ```

- Turn on hugepage for RandomX algorithm

  - Temporarily

  ```shell
  sudo sysctl -w vm.nr_hugepages=2560
  ```

  - Permanently

  ```shell
  sudo bash -c "echo vm.nr_hugepages=2560 >> /etc/sysctl.conf"
  ```

- Download source code

  ```shell
  git clone https://github.com/XDagger/xdag.git
  ```

- Build XDAG client

  ```shell
  cd xdag
  mkdir build && cd build
  cmake .. 
  make
  ```

### Usage

- Connect to the pool

  ```shell
  ./xdag -t -randomx f -m <mining thread> <pool ip>:<port>
  ```

  -m is an optional item, indicating the mining thread, the default is 0, that is, no mining operation

- First run

  - The first time you run the system, you will be prompted to `set password`, which is used to transfer funds and unlock wallet information. Please keep in mind that once the password is lost, it cannot be retrieved
  - `enter random characters`This field is a random number seed, used to strengthen the randomness of the file, not a password
  - It will take some time to generate your wallet address, please wait patiently until the `xdag>` field appears

- Check the connected status 

  ```shell
  xdag> state
  [show current network status
  ```

- Check balances

  ```she
  xdag> balance
  [show all balances of your account]
  ```

- Show XDAG address

  ```sh
  xdag> account
  [display the xdag address owned by the account]
  ```

- Transfer 

  ```shell
  xdag> xfer
  [xfer  amount  address]
  ```

- drop out

  ```she
  xdag> terminate
  ```

- More command line instructions

  ```shell
  ./xdag -h
  #or
  xdag> help
  ```

  For more detailed information, please refer to [XDAG](https://github.com/XDagger/xdag/wiki/Getting-started)

### Windows Visual Wallet

Download the visual wallet from the official website, [download link](https://xdag.io/zh/)

After decompression, open the `wallet-config.json` file, modify `pool_address` to the testnet mining pool address, and modify `is_test_net` to `true`

## Wallet Backup and Restore

**It is strongly recommended that you store the testnet and mainnet wallets separately to avoid accidental loss of mainnet wallet data**

### Backup

- It is advised to back up the entire working directory, and store read-only
- The important files to keep are `wallet.dat`, `dnet_key.dat` and the `storage` folder

**Please remember the password set during the first run. If the password is lost, the wallet file cannot be decrypted correctly and the corresponding account cannot be retrieved**

### Recover

- To recover, place the above noted files in your newly extracted or compiled client directory



## Mining Tutorial

It is recommended to download XDAG's dedicated mining software [XdagRandomXMiner](https://github.com/XDagger/XdagRandomxMiner/releases/tag/0.4.1 )

**Note 1: To use mining software, a miner needs to take up 2.5G of running memory, which grows linearly with the number of miners. If multiple miners are used, make sure that the opened memory page is 1280*corresponding to the number of miners**

**Note 2: Please make sure that the wallet address has been confirmed on the XDAG network, otherwise the mining operation cannot be performed**

### Linux system turns on hugepage for RandomX algorithm

- Temporarily

  ```shell
  sudo sysctl -w vm.nr_hugepages=1280
  ```

- Permanently

  ```shell
  sudo bash -c "echo vm.nr_hugepages=1280 >> /etc/sysctl.conf"
  ```

### MacOS / Linux

- Start command

  ```shell
  DaggerMiner -cpu  -T -p <pool ip:pool port> -t <thread number> -a <account address>
  ```

### Windows

Please refer to [Win10 Configuration RandomX](Win10_Configuration_RandomX_Algorithm_Environment_zh.md) or [Enable the Lock Pages in Memory Option (Windows)](https://msdn.microsoft.com/en-gb/library/ms190730.aspx) to open the hugepage

- Start command

  ```shell
  DaggerMiner.exe -cpu -T -p <pool ip:pool port> -t <thread number> -a <account address> 
  ```

- Common problem

  - Lack of VC++ dependency library

    download link ：[VC++ runtime dependency library](https://download.visualstudio.microsoft.com/download/pr/89a3b9df-4a09-492e-8474-8f92c115c51d/B1A32C71A6B7D5978904FB223763263EA5A7EB23B2C44A0D60E90D234AD99178/VC_redist.x64.exe)

  - Missing .Net dependency library

    Download link ：[.Net runtime library](http://info.xdagmine.com/dotNetFx40_Full_x86_x64.exe)

### General question

Mining shows `Dataset allocation failed`, confirm that hugepage has been successfully configured, otherwise please ensure that the system memory is sufficient to support the memory required by a miner



## Pool Address

ShangHai:  1.15.78.91:9992



## Other

Now you can access the XDAGJ test network and perform the transfer function. At the same time, you can check the information you want to know in the browser

We welcome you to inform us about errors in the use process or any other information that can help us improve the project. You can give us feedback through [Issues](https://github.com/XDagger/xdagj/issues)

If you have other questions, or if you want us to provide more tutorials, you can also ask questions in [Issues](https://github.com/XDagger/xdagj/issues)
