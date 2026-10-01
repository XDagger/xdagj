# Security audit: xdagj 0.9.0 open network / xdagj-p2p 0.1.7

Date: 2026-09-30. Scope: everything a node exposes to an untrusted network once the
whitelist is gone - the P2P layer (xdagj-p2p 0.1.7), the XDAG protocol handler, block
import and execution (`BlockchainImpl`), synchronisation (`XdagSync`, `SyncManager`), and
the configuration that controls them. Out of scope: the mining pool websocket, RPC and
telnet (operator-facing, unchanged), the wallet.

Method: the code was read with the questions an attacker asks - *what does the node
believe without proof, what does it allocate or compute on request, what can be replayed,
what depends on the order of arrival* - after the design had been written down
(`OPEN_NETWORK.md`), and every finding was fixed or explicitly accepted. The consensus
changes were then subjected to a determinism fuzz (`DeterminismTest`, 600 seeds) and the
protocol to adversarial unit tests and a two-node loopback test.

Severity: **H** an untrusted peer can break consensus, take funds, or stop the node;
**M** an untrusted peer can degrade the node or the network; **L** hygiene / defence in depth.

## 1. Findings in xdagj-p2p (all fixed in 0.1.7)

| # | sev | finding | fix |
|---|-----|---------|-----|
| P1 | H | Discovery datagrams were not authenticated: any packet could carry any node id, host and port. A peer could fill the table with fake nodes, impersonate nodes, and make the node send neighbour lists to forged addresses (amplification). | `KadPacket`: every datagram signed with the node key over `sha256(domain ‖ networkId ‖ code ‖ body)`, sender identity recovered from the signature and required to match the id the packet claims. Addresses inside packets are ignored: a node is reached where its datagrams come from. |
| P2 | H | No endpoint proof: a node entered the table on any pong. | Ping/pong bonding: a pong must echo the hash of a ping we sent to that endpoint; only bonded nodes enter the table, are dialled, or get `FIND_NODE` answered. Bonds expire after 12 h. |
| P3 | H | `KadNeighborsMessage`, `Peer.decode`, `DnsNode.decompress` allocated arrays from counts read off the wire (remote OOM). `SimpleDecoder.readBytes` accepted negative lengths. | `SimpleDecoder.readCount(max, minElementSize)` bounds every count before allocation; negative lengths refused; neighbours ≤ 16 per message, capabilities ≤ 32, DNS nodes ≤ 1024. |
| P4 | H | Frame codec "resynced" on a bad magic number, scanning the stream byte by byte and logging; `XdagMessageHandler` kept a map of half-received packets per connection with no bound on number or lifetime. | Strict codec: a bad header closes the connection. One packet in flight per connection; interleaving, empty chunks and overlong packets are protocol violations. |
| P5 | H | Handshake replay: only the dialling side chose a nonce and signed it itself; a recorded `WORLD` passed for five minutes at any node the victim had dialled. Self-connections were not detected. | Mutual challenge (`INIT` both ways), each side signs the other side's nonce; the dialler's signature also binds the accepting node's identity. Self-connections are recognised and the address remembered. Tests: `testRecordedWorldCannotBeReplayed`, `testWorldIsBoundToTheIdentityOfTheAcceptingSide`. |
| P6 | M | Bans were not enforced on inbound connections; no limits on inbound, pending-handshake, per-IP or per-subnet connections; ban duration overflowed after ~48 offences and expired immediately. | `ChannelManager.admit()` before any handler is installed: bans, closed-network check, totals, pending handshakes (32), per IP (2), per /24 or /48 (8); handshake timeout 10 s; graduated ban computed by shifting with a cap. |
| P7 | M | No backpressure: a peer that did not read made the node buffer without bound; a peer could write at any rate. | Outbound queue bound (32 MB, then the peer is dropped), Netty water marks, `Channel.isWritable()` and `onWritabilityChanged` for the application; `InboundTrafficGuard` (4 MB/s, 32 MB burst) at the head of the pipeline. |
| P8 | M | Discovery packets were verified before any rate limit; verifying a signature costs far more than sending a datagram. | Per-source token bucket in `P2pPacketDecoder` before decoding (20/s, burst 60), a second one in `KadService`; bounded numbers of tracked nodes per IP (8), per subnet (64), unverified (256), total (2000); table limits per IP per bucket (2) and per subnet (10). |
| P9 | M | `getConnectableNodes()` handed unverified nodes to the dialler; the node id used in discovery (hex) and in the handshake (Base58) never matched, so duplicate connections were not recognised; on one machine any connection from the loopback address counted as "this node", so a node could never connect to a second local neighbour. | Only verified nodes are connectable; ids converted for comparison; peers identified by the listen address they announce. |
| P10 | M | `Channel.send(Bytes)` wrote unframed bytes to the socket (the other side could not decode them); framework message codes could be sent by the application. | Framed as an application message; codes below 0x16 refused. |
| P11 | L | `ReputationManager` used Java serialisation and grew without bound; node ids are free to make up. | Plain text file, 20 000 entries, least recently updated evicted. |
| P12 | L | `P2pConfig` called third-party "what is my IP" services over plain HTTP, without timeouts, in its constructor. | No network access on construction; `detectExternalIp()` on request, HTTPS, 5 s timeouts, answers validated as IP literals. |
| P13 | L | Host strings from other nodes were turned into socket addresses, which resolves names in the DNS. | `Node.isWellFormed()`: IP literals only, ports in range; names never resolved. |
| P14 | L | Remote-triggered conditions logged at INFO/WARN (log flooding). | Downgraded to DEBUG/TRACE. |
| P15 | L | `PeerClient.connect(host, port)` blocked until the connection closed; `stop()` without `start()` threw; tests leaked a server on a fixed port and needed the internet. | Waits for the connect only; null-safe; tests hermetic. |

Two datagram sizes deserve a note: a full neighbour list does not fit in one 1280-byte
datagram, so answers are sent in datagrams of at most 8 nodes and accepted for 5 s after
the question, up to a bucket's worth.

## 2. Findings in xdagj (protocol and node)

| # | sev | finding | fix |
|---|-----|---------|-----|
| X1 | H | `BLOCKS_REQUEST` had no bound on the time span: a peer could make the node read and stream its whole history. `SUMS_REQUEST` with a span of 2^63 looped forever in `BlockStoreImpl.loadSum` (a negative number never shifts to zero), hanging a network thread. (N2) | Spans bounded to what the protocol produces; `loadSum` refuses non-positive spans; both rate limited per peer; at most 65 536 blocks per answer. Test: `sumsRequestWithImpossibleSpanIsRefusedWithoutHanging`. |
| X2 | H | Statistics reported by peers decided when the node considered itself synchronised; a peer claiming a huge height kept it "syncing" forever (no gossip processed, no blocks produced). (N3) | Claims that cannot be true (more main blocks than epochs) are ignored and count as misbehaviour; under the hardened rules a node is also done when its own latest main block is recent (`SyncManager.RECENT_TIP_EPOCHS`). |
| X3 | H | Blocks on the wire were decoded with no length checks; a body of the wrong size reached `XdagBlock` and failed with an `Error`. Message fields (hash, difficulty, sums) were unchecked. | Explicit checks in every message decoder; a malformed message is a `MessageException` and bans the peer. |
| X4 | M | A missing parent made the node ask **every** peer for it; a stream of orphan blocks turned the node into a request flood against its own peers (which, with request limits, would ban it). | One peer is asked (the one that sent the block, else a random one); outbound requests per peer are throttled below what peers accept. |
| X5 | M | A peer that never answered a sync request cost 64 s per request with no consequence. | Silence counts as misbehaviour (25 points; 100 = 10 min ban). |
| X6 | M | Relay TTL taken from the peer without bound. | Capped at the node's own TTL. |
| X7 | M | Invalid blocks, malformed messages and impossible requests had no consequence. | Misbehaviour score per peer; blocks that can never be valid (`ImportResult.isMisbehavior()`, i.e. structural or hardened-rule violations, not state- or clock-dependent rejections) count 20, malformed messages 100. |
| X8 | H | Consensus: C1, C2, C3, C4, C6 and the crash paths found in this work (block without output signature: NPE after state mutation, lets anyone purge miners' orphan pools; amount-bearing coinbase field in a non-transaction block: `IndexOutOfBounds` inside `setMain`; zero-output account transaction: division by zero; `unApplyBlock` NPE on snapshot blocks; `rollTx` clearing `BI_REF` of the block that becomes the next main block). | Hardened fork rules (`OPEN_NETWORK.md` §2); validation before mutation in all eras. Tests: `OpenNetForkTest` (20), `DeterminismTest` (600 seeds hardened). |
| X9 | M | The node identity was the wallet key: the handshake exposed which node held which funds, and a compromised node key was a compromised wallet. | Dedicated node key (`node.keyFile`, 0600), created on first start. |
| X10 | L | Pool award bookkeeping: node payments keyed by `Address` identity could spend the same block twice in one transaction (duplicate IN - a negative block balance under the 0.8.x rules; `INTEROP.md` of the Rust port lists two such balances on mainnet); pending awards lost on restart; rewards of blocks found without a pool stayed in the block forever. | De-duplicated by hash; persisted in `pool-awards.txt`; solo-mined rewards go to the node's account. |
| X11 | L | `xfer` / `doXfer` gathered an amount from several accounts under one nonce (never valid); amounts rounded to 0.01 XDAG; history paging used a global static; snapshot blocks reported nothing but a balance over RPC. | Single-account spend with its own nonce; exact decimal amounts (≤ 9 decimals); per-thread page count; full record reported. |

## 3. Accepted / residual risks

* **Block spam is not priced.** XDAG link blocks and transactions carry no cost beyond a
  signature; an attacker can produce valid blocks at will. What bounds the damage: per-peer
  rate limits (1 000 gossip blocks/s, 4 MB/s), bans for invalid blocks, the orphan pool cap
  and the local pool admission policy (the node re-references only what is covered). Storage
  still grows with valid spam; that is a property of the block format, not of this change.
* **Sybil / eclipse.** Discovery is Kademlia with per-address and per-subnet limits on
  the table and on connections, seeds are always dialled again when connections are lost,
  and `node.trustedPeers` gives operators a guaranteed set. A well-resourced attacker with
  many addresses can still surround a node; the consensus consequences are the same as in
  any PoW network (a stale view, not a forged history).
* **Hash-rate majority** can reorganise the chain. The fork removes the ways of doing it
  without hash rate.
* **Sync-done heuristic.** A node declares itself synchronised when its own tip is recent
  (4 epochs). A peer can feed it a recent block on a private fork; the node then starts
  producing on that fork until heavier blocks arrive - wasted work, no consensus effect,
  and the sums-based comparison keeps running.
* **Unencrypted transport.** The handshake authenticates both ends; the connection afterwards
  is neither encrypted nor MACed. Every message is verified on its own merits, so a
  man-in-the-middle can drop or delay but not forge.
* **Discovery ignores bans** (they live in the TCP layer); banned addresses are still rate
  limited.
* **Deferred consensus items** C5 (fee on failure), C8 (atomic `setMain`, detected but not
  prevented), S1 (storage format), O6 (directory names) - see `OPEN_NETWORK.md` §2.3.
* **Mainnet activation** is a maintainer decision (`MainnetConfig.OPEN_NET_FORK_EPOCH`).
  Until it is made, mainnet nodes run the new protocol in closed mode.

## 4. Evidence

| suite | result |
|-------|--------|
| xdagj-p2p `mvn test` (offline, loopback only) | 910 tests, 0 failures, 2 skipped (need the internet: external-IP services) |
| xdagj `mvn test` (offline, loopback only) | 242 + 2 (`TwoNodeNetworkTest`) tests, 0 failures |
| `DeterminismTest.hardenedRulesGiveTheSameStateInAnyOrder`, seeds 1-600 | pass; two real bugs found on the way (skipped-transaction bookkeeping, fee clobbering by `removeOrphan`) plus the `BI_REF` confirmation order dependence |
| `OpenNetForkTest` | 20 tests: legacy vs hardened behaviour for C1, C2, C4, C6, the crash paths, the latch, execution rules following the main block's epoch, sync hints dropped |
| `XdagP2pHandlerTest` | malformed message → ban; unknown code → ban; out-of-bounds blocks / sums requests refused (2^63 span no longer hangs); impossible statistics ignored; valid block imported; invalid block scored |
| `TwoNodeNetworkTest` | two nodes on 127.0.0.1: connect through a seed, gossip, request/answer, rubbish → ban; a closed node refuses a stranger and accepts a configured peer |
| xdagj-p2p adversarial tests | signed/tampered/foreign-network/unsigned datagrams, oversized packets, neighbour count on the wire, interleaved and overlong frames, handshake replay and identity binding, loopback peers, rate limits, table limits |
