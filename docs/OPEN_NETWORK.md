# Open network: running a node without a whitelist

This document describes how xdagj 0.9 lets anybody run a node, what had to change for
that to be safe, and how a network moves from the whitelisted 0.8.x world to the open one.
It is the reference for the code that cites it (`BlockchainImpl`, `ChannelManager`,
`AbstractConfig`, `XdagP2pHandler`).

Design principles follow the "XDAG Node 2.0" road map: *Peer ≠ Consensus*, *Snapshot ≠ Truth*,
no permanent whitelist, no privileged bootstrap nodes, no trusted authority, no
non-determinism.

## 1. Why the whitelist could not simply be removed

In 0.8.x a node only accepted connections from `node.whiteIPs`. That list was not a
convenience: it was the only thing standing between the network and a number of
consensus and protocol flaws that any peer could exploit. Rewriting the node (the Rust
port in `/root/xdag`, `docs/BUGS.md` there) surfaced them; the worst ones are:

| id | flaw (0.8.4) | effect from an untrusted peer |
|----|--------------|-------------------------------|
| C1 | every block, not only end-of-epoch PoW candidates, gets its own difficulty from `sha256d(hash)` | a wallet transaction with a lucky hash becomes the main block of its epoch or unwinds the chain |
| C2 | a transaction that lists the same block twice as input is checked against the untouched balance for each listing | the block ends with a negative balance: money from nothing |
| C3 | the execution status a peer attaches to a synced block is believed | a peer decides which transactions of its victims count |
| C4 | amount sums overflow and throw out of the middle of `setMain` | one block aborts confirmation half-way, leaving a half-applied main block |
| C6 | "input address must exist" is checked at import time against local state | validity depends on what the node has seen: two honest nodes disagree forever |
| N2 | `BLOCKS_REQUEST` / `SUMS_REQUEST` have no bounds; a 2^63 span loops forever in `loadSum` | a peer makes the node stream its whole history, or hangs a network thread |
| N3 | network statistics claimed by peers decide when a node considers itself synchronised | a peer claiming a huge height keeps the node in "syncing" forever: no blocks processed, none produced |
| N5 | no self / duplicate connection handling, handshake replay | connection churn, impersonation of a node one has been dialled by |
| — | unsigned discovery packets, unbounded allocations from wire counts, frame resync on garbage | in xdagj-p2p: node-id spoofing, table poisoning, memory exhaustion |

Opening the network without fixing C1/C2 would have handed the chain to the first
attacker. So the task was done in this order: **P2P → storage/robustness → sync →
execution rules**, as the road map prescribes, with the consensus changes isolated behind
one activation switch.

## 2. The open-network hardening fork

All consensus-affecting fixes are gated behind **one fork**, activated per network by an
epoch:

| network | activation |
|---------|------------|
| devnet  | epoch 0 (in force from genesis) unless `consensus.opennet.forkEpoch` says otherwise |
| testnet | not scheduled by default; set `consensus.opennet.forkEpoch` |
| mainnet | **not scheduled**: `MainnetConfig.OPEN_NET_FORK_EPOCH = Long.MAX_VALUE`; the maintainers set the epoch in code before a release (the config file is ignored on mainnet so that no operator can fork alone) |

The rules a block is checked with depend on the block's epoch, the rules a main block is
executed with on the main block's epoch, so that the history before the fork is
reproduced exactly (the 0.8.x code paths are kept as they are, bug for bug, in
`applyBlockLegacy` / `unApplyBlock`). A negative config value means "not scheduled".

Once a main block of the fork era has been confirmed the node **latches** the fork
(`BlockStore` key `OPEN_NET_FORK_LATCH`); the latch is persisted and never cleared, and
from then on *every* block is checked with the hardened rules, whatever its timestamp.
Without the latch a block that claims an old timestamp would be checked with the old
rules and could still exploit them.

### 2.1 Rules in force after the fork

* **Difficulty.** Only end-of-epoch candidates without inputs (the blocks miners produce)
  carry their own difficulty. Transactions and link blocks contribute nothing, so they can
  neither become main blocks nor unwind the chain (C1). When RandomX is in force a
  candidate whose seed is unknown has difficulty 0 instead of a `sha256d` fallback.
* **Execution** (`applyBlockHardened`, `unApplyBlockHardened`): inputs that spend the same
  block are added up before the balance check (C2); all sums are overflow-safe and a
  transaction whose amounts do not fit is rejected like any other invalid transaction, the
  nonce is consumed and nothing throws (C4); a block without inputs moves no value whatever
  its amount fields say; blocks inherited from a snapshot are never executed or unwound
  (they carry no data); the execution status sent by peers is ignored (C3); a transaction
  skipped because its nonce does not fit stays skipped, and the main block that skipped it
  is recorded (`TX_SKIPPED_BY`) so that only the unwind of *that* block undoes it.
* **Import** (`tryToConnect`): the whole block is validated before anything is changed
  (0.8.x mutated state and then threw); a block must carry an output signature; an account
  transaction needs at least one output; "input address must exist" is not checked (C6:
  a transaction from an unknown address is simply rejected at execution time, which is
  deterministic); a full orphan pool is not invalidity; blocks older than the snapshot
  time are refused; no reorganisation below the snapshot top.
* **Confirmation** (`checkNewMain`): a main-chain block is confirmed when the chain above
  it is long enough, without the 0.8.x `BI_REF` precondition, which depended on the order
  in which blocks arrived (a node that went through a reorganisation could wait forever
  for a block that nobody re-linked).
* **Own pool admission** (`admitToPool`, local policy, not consensus): the node only queues
  transactions for its own blocks if they are covered right now (balance, nonce window of
  64, pool not full). A transaction that cannot be paid for costs its sender nothing
  (C5 is unchanged), so this stops the free filling of the pool.

### 2.2 Determinism

`DeterminismTest` builds random histories (competing candidates, account and block
transfers, nonce gaps and reuse, duplicate inputs, link blocks, poison and overflow
blocks) and delivers them to fresh nodes in creation order and in three random
parents-first orders; the complete state (blocks, flags, heights, fees, account balances
and nonces) must be identical. 600 seeds (thousands of reorganisations) pass under the
hardened rules. The same generator shows that the 0.8.x rules are *not* order
independent (C6), which is why they are frozen rather than fixed.

### 2.3 Things deliberately not changed

* C5 (a failed transaction consumes the nonce without a fee): a consensus change with fee
  semantics that needs a maintainer decision; mitigated by pool admission.
* C8 (non-atomic `setMain`): the node now marks a main-chain update in progress
  (`MAIN_UPDATE_MARK`) and refuses to start on a database whose last update was
  interrupted (`-Dxdagj.ignoreInterruptedMainUpdate=true` overrides); real atomicity needs
  a storage rewrite.
* S1 (Kryo storage format) and the swapped `BLOCK` / `TIME` directory names (O6): the
  on-disk layout of existing nodes; changing it needs a migration.

## 3. The P2P layer

The 0.8.x transport (`io.xdag.net`: frames, handshake, whitelist) is replaced by
[xdagj-p2p](https://github.com/XDagger/xdagj-p2p) 0.1.8, hardened as part of this work
(in 0.1.7; 0.1.8 adds two start-up fixes and is the same on the wire):

* **Identity.** A node is known by the address of its *node key* (`node.keyFile`,
  created on first start), never by its wallet key. Handshake: `INIT(nonceA) → INIT(nonceB)
  + HELLO(signs nonceA) → WORLD(signs H(nonceB | id of B))` - each side signs a value the
  other side chose, so a recorded handshake is worthless, and a node cannot be made to
  talk to itself.
* **Discovery** (UDP Kademlia). Every datagram is signed with the node key over
  `sha256("xdag-discovery-v2" | networkId | code | body)` and the sender's identity is
  recovered from the signature. The address a node claims for itself is ignored; a node is
  reached where its datagrams come from, and an endpoint is only believed after it has
  answered a ping with a pong that echoes the hash of that ping. Only such verified nodes
  enter the table, are dialled, or get their `FIND_NODE` answered (no amplification to
  forged addresses). Neighbour lists are bounded (16), checked for form and address policy,
  and verified by a ping. Per-address rate limits; bounded numbers of nodes per address,
  per /24 (/48) and in total; timestamps within 20 s.
* **Admission.** Bans are enforced on inbound connections; limits on total, inbound,
  pending-handshake, per-IP and per-subnet connections; configured peers are exempt from
  the per-address limits but not from the totals; per-connection inbound byte rate; a
  peer that does not read is dropped; strict frame codec (no resync on garbage); bounded
  message assembly (one packet in flight per connection); all wire counts bounded before
  allocation.
* **Closed / open.** The P2P layer runs in *closed* mode - only `node.seeds` and
  `node.trustedPeers` are accepted, dialled, or answered in discovery - until the chain
  latches the fork; then it switches to *open* (`ChannelManager.applyOpenness`, checked
  every 10 s). This is the same behaviour the whitelist had, without the list being a
  permanent feature: after the fork the lists are entry points and preferences, nothing
  more.
* **Protocol** (`XdagP2pHandler`): XDAG messages live at codes 0x20-0x2A; `BLOCKS_REQUEST`
  spans are bounded to `REQUEST_BLOCKS_MAX_TIME`, `SUMS_REQUEST` spans to
  `16^n × REQUEST_BLOCKS_MAX_TIME` up to 2^48 (and `loadSum` refuses negative spans);
  requests and pushed blocks are rate limited per peer; peers that send malformed
  messages, impossible requests or blocks that can never be valid accumulate a
  misbehaviour score and are banned for 10 minutes at 100; network statistics that cannot
  be true (more main blocks than epochs) are ignored. Nobody writes into a node's past
  unasked: history (`SYNC_BLOCK`) is taken only in answer to a request of this node - for
  the span or for the block itself - and so is news (`NEW_BLOCK`) dated more than about
  nine hours ago. A block that is needed after all, because something refers to it, is
  asked for by its hash and then accepted.
* **Synchronisation** (`XdagSync`, `SyncManager`). Three things a node could go by can be
  had for free, so none of them decides anything: what peers *claim* (a claimed height
  keeps a node "syncing" forever, N3); the node's own *tip* (a block without work but with
  a current timestamp becomes the latest main block of any node that is behind); and the
  *sums* of the history comparison (they are sums - blocks made for the purpose bring a
  node's sums for a span it has not got to exactly those of an honest peer, and then
  nothing is fetched there). What cannot be had for free: a block only becomes part of a
  node's blocks when everything it refers to is there. So:
  * `XdagSync` works in *rounds* with one peer at a time, in *cycles* in which every peer
    that is connected when the cycle begins gets exactly one round (a peer that comes back
    under a new name waits for the next cycle). Rounds never stop - not when the tip is
    recent and not after the node called itself synchronised.
  * A round compares the whole sums tree with the peer, wherever the tip is, down to the
    snapshot the node was booted from, and fetches the request spans in which the peer has
    more, oldest first. That is the fast path and all it takes among honest nodes. A span
    fetched from a peer is settled for that peer while the peer's sums for it stay the
    same; spans in which this node already holds at least as much as the peer (blocks only
    this node has) are looked into a few per round, and a synchronised node fetches at
    most 16 spans per round - so blocks nobody needs cannot make nodes fetch their history
    from each other again and again.
  * A node that is not synchronised then *verifies*: it fetches the two request spans that
    are being written to, whatever the sums say. If the peer's newest blocks attach to its
    own blocks, it has the history behind them. If they do not although the comparison
    found nothing to fetch, the sums are wrong somewhere, and the node *recovers*: it looks
    at spans of the peer, without importing them, until it has found where its own blocks
    end, and fetches everything from there to the present regardless of the sums.
  * In an open network a node is synchronised when (a) a cycle of rounds - with at least
    half of the peers it has now - was held since it last caught up, (b) of the peers that
    showed blocks from the present in that cycle, most had none that the node could not
    attach (a peer that did not answer counts against), and (c) its best chain has not
    advanced through blocks older than one request span for 30 s, which only a chain with
    more work can cause. Peers that answered and showed nothing recent (they are behind
    themselves) count neither way; if nobody did - a network on which no blocks are
    produced yet - rounds with most peers are enough.
  * If older blocks with more work turn up later, the node goes back to "synchronising"
    and produces no blocks until it has caught up. Having waited `waitEpoch` epochs makes
    a node start by itself only if it has no peers at all.
  * In a closed network the 0.8.x rule (what the configured peers report) is unchanged.

The wire protocol is **not compatible with 0.8.x** (frame version 2, `networkVersion` 1):
upgrading a network is a flag day.

## 4. Configuration

```hocon
node.seeds = ["1.2.3.4:8001", "[2001:db8::1]:8001"]   # entry points, no privilege
node.trustedPeers = ["5.6.7.8:8001"]                    # always connected, never banned
node.discovery.enabled = true
node.allowPrivateAddresses = false     # true on devnets that live on one machine / LAN
node.minConnections = 8
node.maxConnections = 50
node.maxInboundConnections = 40
node.maxInboundConnectionsPerIp = 8
# node.bindIp = 0.0.0.0
# node.keyFile = <root>/node.key
# consensus.opennet.forkEpoch = -1     # devnet / testnet only; negative = not scheduled
```

`node.whiteIPs` is still read: its entries become seeds and trusted peers and a warning is
logged. A mining pool's own whitelist (`pool.whiteIPs`, the websocket for miners) is a
different thing and unchanged.

## 5. Activating the fork on a live network (mainnet / testnet)

1. Release a build with `OPEN_NET_FORK_EPOCH` set to an epoch a few weeks ahead (an
   epoch is 64 s; `XdagTime.getEpoch(timestamp)`).
2. All nodes upgrade before that epoch. From the release on they speak the new protocol
   (flag day: 0.8.x nodes drop off), still in closed mode with their former whitelist as
   seeds / trusted peers.
3. At the fork epoch the first main block of the new era is confirmed, every node latches
   and opens up; discovery starts, anybody may connect.
4. A node that starts after the fork from a snapshot taken after it treats every block as
   a fork-era block (`RandomX.forkTimeFromSnapshot`, latch computed from the snapshot
   height).

Nothing needs to be done at the moment of the fork, and no node can decide it alone: the
epoch is in the code of the release, not in a config file.

## 6. Residual risks and open items

* The consensus itself is still XDAG's: a majority of hash rate can reorganise the chain.
  The fork removes the ways of doing it *without* hash rate.
* The mining pool protocol (websocket) is unchanged and remains an operator-whitelisted
  service.
* DNS-based node lists (xdagj-p2p `treeUrls`) are not wired into the node; seeds are the
  entry points.
* Deferred consensus items: C5, C8, storage format (section 2.3).
* Operational: the node key file must be kept private (it is the node's identity, not
  money) and must not be copied between nodes (duplicates are refused as self-connections).
