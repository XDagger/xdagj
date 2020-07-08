# How to build a test environment

## Prepare

* Learn from [XDagger/Getting started](https://github.com/XDagger/xdag/wiki/Getting-started) to build a C pool
* Learn from [README](../README.md) to build a Java pool



## Use a C and Java pool

**1、Set up test network environment for C** 

* Instead of netdb-testnet.txt and netdb-white-testnet.txt with your IPs.(Including Java version ip for providing service);

* Launch your hosts with `-t -disable-refresh` options;

* Use this command to start the pool (Note case sensitivity):

  ```shell
  xdag.exe -m 0 -t -p [ip1:port1] -P [ip2:port2:CFG] -disable-refresh`-rpc-enable -rpc-port [rpc-port]
  ```

  [ip1:port1] : IP for connection between pools;

  [ip2: port2] :Miner connection IP.

**2、Set up test network environment for Java**

​	Modify the NodeIP attribute in the Config class to the IP set in netdb-testnet.txt and netdb-white-testnet.txt.

​	①Start  in IDEA

​	②Start with Jar package in console

```shell
java -jar xdagj.jar -p [ip1:port1] -P [ip2:port2:CFG]
```

Then turn it on with  `run` command. C pool will automatically connect to the Java pool,or you can use command `net connect ip:port` to connect another pool.



## Both are Java pool

Java version does not hava a whitelist function,so there is no need to configure IP.

Perform  `Set up test network environment for Java`



