### xdagj 快照使用方法

启动方法
1. 导入原先c矿池的wallet，参数是钱包路径
    ```shell script
    ./xdag.sh --convertoldwallet <your_wallet_path>/wallet-testnet.dat -t
    ```
2. 加载快照，参数是快照文件的路径
    ```shell script
   ./xdag.sh --loadsnapshot <your_snapshot_file_path> -t
    ```
3. 使用快照启动，参数是快照高度以及快照时间(快照时间采用16进制)(这两个参数可以在C端快照时获 得)，如附图所示
    ```shell script
    ./xdag.sh -t --enablesnapshot <snapshot_height> <snapshot_time>
    ```
   
   
![C version snapshot](img/C_version_snapshot.png)