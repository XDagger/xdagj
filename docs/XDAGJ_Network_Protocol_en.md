# Network Protocol

Clients send messages to peers in order to interact with the blockchain.  These can be
divided into general peer messages, and consensus methods used by validators for forging blocks.

Messages are written using the XDAGJ [XDAGJ_Message_Encoding](./XDAGJ_Message_Encoding_en.md) format.

Messages send the code byte first, then the parameters in order


## Standard Message Objects

### Peer

Message:
- ip (string)
- port (int)
- networkVersion (short)
- clientId (string)
- peerId (string)
- latestBlockNumber (long) 
- capabilities size (int)
- capabilities[] (string[])

## P2P Messages

- [0x00, 0x0f] Reserved for p2p basics

### Disconnect

Code: 0x00

Inform a peer of disconnection.  Reasons can include too many peers, invalid handshake, or other.

Message:
- reason (byte)

### Hello

Code: 0x01

Initiate a connection with a peer.  No messages will be accepted until after this handshake is done.

Message:
- peer (Peer)
- timestamp (long)
- signature (byte[])

### World

Code: 0x02

Respond to a Hello message indicating successful connection.

Message:
- peer (Peer)
- timestamp (long)
- signature (byte[])

### Ping

Code: 0x03

Request a reply from the peer to ensure connection is still valid.

Message:
- timestamp (long)

### Pong

Code: 0x04

Respond to a Ping message.

Message:
- timestamp (long)

## Consensus Messages

- [0x10, 0x1f] Reserved for node

### BLOCKS_REQUEST(0x10)
### BLOCKS_REPLY(0x11)
### SUMS_REQUEST(0x12)
### SUMS_REPLY(0x13)
### BLOCKEXT_REQUEST(0x14)
### BLOCKEXT_REPLY(0x15)
### BLOCK_REQUEST(0x16)
### RECEIVE_BLOCK(0x17)
### NEW_BLOCK(0x18)
### SYNC_BLOCK(0x19)
### SYNCBLOCK_REQUEST(0x1A)