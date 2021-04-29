### XDAGJ networking specification

---

#### Why use libp2p?

Libp2p is a universal toolkit, adopted by mainstream blockchain technologies such as IPFS, Ethereum2.0, Polkadot. Developers can use plug-and-play networks in their distributed applications.

Libp2p handles peer discovery and handshake protocols.

#### Libp2p version

XDAGJ adopts the JVM-libp2p 0.5.1-RELEASE version, which aims to provide the smallest network stack. JVM-libp2p allow XDAGJ to interoperate with mature versions of libp2p clients written in other language versions.

#### Future plan

Replace the existing broadcast model with the Libp2p-GossipSub protocol model. The purpose of the GossipSub protocol is to balance the contradiction between broadcast redundancy and propagation speed. It constitutes a push model. Through constant observation, a node maintains a score file for its connected peers. This approach allows them to select nodes that behave normally to be included in the grid. Evidence proves that it is not affected by the attack of solar eclipse, refer to [Proof](https://arxiv.org/abs/2007.02754). GossipSub is currently integrated as the main messaging layer protocol in Filecoin and Ethereum 2.0 (ETH2.0) blockchains.

#### The goal

Build a scalable, stable and flexible network stack for XDAGJ to support multiple services with the high TPS.

#### Modular

LibP2P adopts a modular design. Developers can combine multiple modules to achieve corresponding requirements.

As a very mature network framework, it can help XDAGJ build a sufficiently stable and highly flexible network stack. 

### Network specification

---

Libp2p is a multi-protocol transmission stack, which aims to establish multiple protocol service transmission ports at the same time. On this basis, we define the XDAGJ network layer protocol stack.

#### Transport

The XDAGJ transport layer uses the TCP protocol. A node has functions of a server and a client at the same time. Libp2p TCP supports listening to IPV4 and IPV6 addresses.

#### Encryption

The XDAGJ node uses the Libp2p-Noise protocol for handshake and uses secp256k1 for encryption. The Noise protocol is a framework for establishing an encryption protocol. The Noise protocol supports two-way and optional authentication, identity hiding, forward secrecy, zero round-trip encryption, and other advanced features. The Noise protocol handshake is light and easy to understand, and is used in large encryption-centric projects such as WireGuard, I2P, and Lightning.

#### Identity

In a world with billions of connected devices, identity enable a node knowing who to establish communication with the key to ensure safe and reliable communication. XDAGJ uses [Public Key Cryptography](https://en.wikipedia.org/wiki/Public-key_cryptography) as the basis of peer identity. First, it gives each peer a globally unique name in the form of [peer ID](https://docs.libp2p.io/reference/glossary/#peerid). Second, `PeerId` allows anyone to retrieve the public key of the identified peer, thereby achieving secure communication between peers.

### XDAGJ Discovery Network Introduction

---

The [discv5](https://github.com/ethereum/devp2p/blob/master/discv5/discv5-rationale.md)  protocol version is used for peer node discovery. It is an independent protocol and uses UDP on a dedicated port Run, only for peer device discovery. discv5 supports self-authentication and flexible peer-to-peer record data structure.

- The compressed secp256k1 publickey, 33 bytes (`secp256k1` field).
- An IPv4 address (`ip` field) and/or IPv6 address (`ip6` field).
- A TCP port (`tcp` field) representing the local libp2p listening port.
- A UDP port (`udp` field) representing the local discv5 listening port.

#### Node, Distance and Routing table

The node is composed of IP, UDPPORT, compression key, optional TCPPORT and 32-byte PEERID.

The distance between the nodes is not a physical distance, but is derived from the exclusive OR of the PEERID of the two nodes.

```shell
distance(n₁, n₂) = n₁ XOR n₂
```

The routing table of the node is composed of 256*16 discoverypeers, and the range of the 32-byte PPERID XOR is 0-256. The routing table can accommodate 16 nodes with the same distance. Once the number is more than 16, the newly added node will replace the node that has not been updated for the longest time.

#### Update local routing table

The node updates the local routing table through a handshake, and each sent data packet is signed with a private key to provide receiver verification. The newly added node in XDAGJ will ask for the latest routing information according to the seed node in the configuration, and update the local routing table. XDAGJ will periodically ask for the latest routing information of the nodes in the routing table, so as to maintain the local routing table.

[References](https://github.com/ethereum/devp2p/blob/master/discv5/discv5-theory.md)

#### Encrypted 

Discovery uses secp256k1 for encryption operations, and performs secp256k1 signatures on each packet to verify the reliability of the data.

#### How to debug with unencrypted data in the network

In XDAGJ, the processor relies on the Netty framework. You can use the unencrypted processor in the framework and add the logging function to start data tracking.

For temporary test scenarios, you can use **tcpdump** or **Wireshark** to view network data.

