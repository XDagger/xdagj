# XDAGJ RPC Service Tutorial

This section describes XDAGJ RPC service.

If you want to open the rpc service，you need to set "isRPCEnabled = true" in your xdag-XXX.config.

## Usage

```js
curl http://localhost:4444/ -s -X POST -H "Content-Type: application/json" --data "{\"jsonrpc\":\"2.0\",\"method\":\"xdag_syncing\",\"params\":[],\"id\":1}"
```

```json
{"jsonrpc":"2.0","id":1,"result":{"currentBlock":"0x30331","highestBlock":"0x30331"}}
```



