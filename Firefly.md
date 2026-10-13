# Firefly (XDAGj-v0.8.5)

## Firefly v0.8.5

- Fix block data corruption during network synchronization issues.



### Notable changes in this release:

- Use original 512-byte XdagBlock data directly in network message serialization instead of reconstructing via `Block.toBytes()`, ensuring data integrity for received blocks during forward synchronization.

### Bug fixes:

- **Fixed an issue with inconsistent block data when broadcasting received blocks.** When a block received from the network was subsequently re-serialized into a `NewBlockMessage` or `SyncBlockMessage` for forwarding to other peers, the field reorder inside `toBytes()` caused the forwarded block to differ from the one originally received, breaking consistency across nodes.

