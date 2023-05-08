# XDAGJ RPC Service Tutorial

This section describes XDAGJ RPC service.

- [System environment](#system-environment)
- [Usage](#usage)

## System environment

```yaml
     JDK   : v17
  script   : xdag.sh
     jar   : xdagj-0.x.y-shaded.jar
```

  Please make sure that the above environment is already available in your operating system, and the JDK version must be 17


## Usage

If you want to open the rpc service，you need to set "isRPCEnabled = true" in your configuration file.

The configuration file is located in `src/main/resources/xdag-XXX.conf`, if you do not modify it, the default configuration is disabled. 

- Show XDAG synchronization status
    
    request
    ```js
    curl http://localhost:4444/ -s -X POST -H "Content-Type: application/json" --data "{\"jsonrpc\":\"2.0\",\"method\":\"xdag_syncing\",\"params\":[],\"id\":1}"
    ```
    response
    ```json
    {"jsonrpc":"2.0","id":1,"result":{"currentBlock":"0x30331","highestBlock":"0x30331"}}
    ```
  
  



