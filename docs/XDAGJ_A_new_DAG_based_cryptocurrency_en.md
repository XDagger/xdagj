![](img/xdag/XDAG_first.png)

# XDAG: A New DAG-Based Cryptocurrency

## Introduction

XDAG is a new generation public chain based on the directed acyclic graph (DAG) infrastructure. It is the first mineable, pure community-driven DAG+POW project, with the main network launched in January 2018. The maximum supply of XDAG is approximately 1.446294144 billion. XDAG has no project sponsor, no ICO, no private placement, and no pre-mining. It is developed and maintained by blockchain enthusiasts from all over the world, making it truly decentralized, efficient, secure, and fair. Although XDAG is a DAG project, its transaction model is similar to Bitcoin's UTXO, and the development of the project shares some similarities with Bitcoin.

## The Legend of XDAG

XDAG was started in September 2017 by a math professor at a Russian university using the name Cheatoshi. Because he was dissatisfied with the cryptocurrency projects of that time, he decided to create his cryptocurrency project using DAG and PoW. Cheatoshi spent three months completing the project but failed to launch it in December 2017. After debugging for a few days, the XDAG network successfully launched in January 2018.

On January 5, 2018, Cheatoshi posted the founding post of XDAG in the Bitcointalk forum, declaring that he intended to create a fair cryptocurrency system based on DAG technology. At that time, everyone was communicating on Bitcointalk. In February 2018, Cheatoshi open-sourced the project, allowing the community to participate and contribute. Afterward, the community migrated the project to Github. Cheatoshi then disappeared, leaving the project completely transferred to the community to manage. This experience is very similar to that of Satoshi Nakamoto.

Salute to Cheatoshi.

XDAG is the first DAG to support mining (IOTA doesn't really use mining), while having no pre-mining and no ICO, and being completely community-driven with no sponsors or investors. XDAG is currently being continuously developed and maintained by a group of blockchain developers who persist in the ideal of decentralization.

## Current Issues with Decentralized Technology

Despite the industry's advancements, blockchain technology itself has various issues that are becoming increasingly critical as implementation scales up. Two primary problems are currently being encountered:

1. The TPS bottleneck of the blockchain limits the performance of blockchain technology by allowing only a limited number of transactions to be processed simultaneously.
2. The long confirmation time of the blockchain hampers the scalability of blockchain technology as users face slow transactions and high fees.
![State-of-The-art](img/xdag/XDAG_second.png)

The current approaches tried include:

1. Layer 1 (Sharding)

   PoS/DPoS and consensus/Sharding (sharding calculation)

2. Layer 2 (Side Chain)

   Side Chain/ State Channels/ Multi Chains

Despite the defects of blockchain, whether it is the centralized solution of EOS or the current hot sharding technology, there are still unsolvable problems in side chain technology, which indirectly shows that it will face great difficulties and challenges to expand based on blockchain itself. The technology of DAG appeared as a third option of resolution.

DAG is relatively special, the data structure is different from the blockchain, and it is born with high scalability. The most well-known project using DAG technology is IOTA, but IOTA still has the problem of centralization; XDAG has made changes with PoW being adopted, which has been proven to be the optimal consensus scheme, and leaving the work of verifying transactions to miners exclusively, and retaining the original advantages of DAG.

These problems pose significant challenges to the future adoption and utilization of blockchain technology. Some possible mitigation strategies that can effectively address these issues include adopting alternative consensus algorithms like DAG (Directed Acyclic Graph). DAG is a revolutionary technology that uses a non-linear structure to ensure maximum transaction processing speed, high scalability, and improved security measures.

One such technology that is built on DAG is XDAG. XDAG is an innovative technology that aims to address several issues that have hindered the widespread adoption of blockchain technology. This technology is designed to deliver high-speed, secure, and scalable solutions, making it an ideal choice for various applications that require high throughput and transaction speeds.

XDAG provides numerous advantages over the traditional blockchain system. The DAG structure enables XDAG to process many transactions simultaneously, significantly increasing the TPS rate. XDAG eliminates the need for miners, making it more energy-efficient and faster than other blockchain consensus algorithms. XDAG's confirmation times are much faster, allowing for real-time, high-throughput transactions.

In conclusion, blockchain technology poses several challenges to scalability and efficiency that need attention to enhance its adoption and utilization. Adopting new technologies such as XDAG or integrating the existing ones, in addition to applying other mitigation strategies, can revolutionize blockchain technology, offering the potential to become more efficient, secure, and scalable for various applications.

You can use XDAG, which is built on a DAG structure, to address scalability and efficiency problems in blockchain technology. This technology provides numerous advantages over the traditional blockchain system, including high scalability, faster confirmation times, and a higher TPS rate. Adopting new technologies like XDAG can revolutionize and enhance blockchain technology's adoption and utilization.
## XDAG Technology

DAG (Directed Acyclic Graph) has been around since it was first introduced as a mathematical concept in graph theory, and a branch of mathematics back in 1736. This digital storage structure has been in use in computer technology since the early days of the field. While DAG structures are theoretically more complex than single-chain structures, they offer better scalability.

Several public chains in the industry currently use DAG structures, but each implementation has its own unique approach and application scenarios, with different understandings of DAG technology playing a major factor.

The various DAG implementations include:

1. NANO, where each account has its chain, and transaction records link different accounts together to form a DAG structure.

   ![NANO DAG](img/xdag/XDAG_third.png)

2. IOTA's DAG, where the user determines the validity of a transaction based on its height and weight.

   ![IOTA DAG](img/xdag/XDAG_fourth.png)

3. Hashgraph's DAG, which uses a modified gossip algorithm between nodes for transaction propagation, forming a sequence in time, and creating a DAG structure.

   ![Hashgraph DAG](img/xdag/XDAG_fifth.png)

4. Byteball's DAG, which uses witness nodes to increase the branch's weight and determine the current master chain in the DAG.

   ![Byteball DAG](img/xdag/XDAG_sixth.png)

Despite the different approaches, these DAG implementations do not address the "impossible triangle" problem, which is characterized by issues related to decentralization, high TPS, and low confirmation time. The XDAG technology, built on an advanced DAG structural model, is designed to solve the "impossible triangle" problem.

XDAG was built from the ground up to address problems related to scalability, high TPS, and low confirmation time, and does so by using the DAG-based algorithm with many unique features. These include an exclusive consensus model, transaction filtering methods at both receiving and sending ends, dynamic memory pool allocation, and much more.

As such, XDAG technology offers the potential for blockchain technology to become more efficient, secure, and scalable for various applications.


XDAG aims to redesign the underlying data structure of blockchain technology and adopt a DAG composition. This innovative approach combines PoW consensus algorithm and DAG technology to provide concurrent processing of transactions between different nodes, improving transactions per second (TPS) and reducing confirmation time. The network is also designed to ensure security and fairness.

![XDAG_seventh.png](img/xdag/XDAG_seventh.png)

In the figure above, A represents wallet address block, Tx represents transaction block, M represents the main block generated by PoW, and W represents the witness block.

The unique feature of XDAG is that blocks, transactions, and addresses are equivalent.

![XDAG_eighth.png](img/xdag/XDAG_eighth.png)

The block data structure in XDAG is shown above. This data structure is used for persistent storage of data. The block structure is composed of 16 structures named xdag_field. Each xdag_field consists of a structure and a union.

The transport_header is used to represent the sequence number during transmission and to hold the address of the next block during post-reception processing.

Type is a 64-bit field that indicates the type of 16 fields in a block. It is divided into 16 parts, each four bits, indicating half a byte. Thus, four bits can indicate 16 types. The field type indicates the corresponding type for each four bits.

Time is used to indicate the time of block generation. The format used is 1/1024 seconds, with one second expressed as 2^10. Time is also used as the starting time point of the request time range when data is exchanged between nodes.

Hash is a 24-byte truncated hash, usually the truncated hash of another block.

Amount is a quantity value in Cheato, used to record the number of XDAGs. Cheato is the basic unit of XDAG, and 1 XDAG contains 2^32 Cheato.

End_time is used to indicate the end time point of data exchange between nodes as the request time range. Data is a 32-byte hash.

The blocks in XDAG are generated independently by each node and wallet, without interference from others. This ensures the independence of block processing in the basic design, laying the foundation for the high TPS mentioned subsequently.
Technology Advantages of XDAG

1. XDAG is the first public chain based on DAG to realize PoW. By combining the high concurrency of DAG with the security and decentralization of PoW, it solves the "impossible triangle" problem in blockchain technology.
2. Block, transaction, and address are equivalent in XDAG. This unique design ensures that the transfer process will not lose coins due to typing in the wrong address.
3. XDAG has low transaction fees and high TPS.
4. XDAG uses unique technology to solve many problems that may exist in the blockchain system, such as double-spending, uncontrollable transaction time, centralization, 34% attacks, and 51% attacks.
5. Some of the experiments currently being done in Bitcoin and Ether can also be done on XDAG since the founders have designed their own operating systems with this in mind.

The concepts mentioned in the figures below explain the above points:

![XDAG_ninth.png](img/xdag/XDAG_ninth.png)

In the concept of the main chain in DAG, transactions need sorting; otherwise, the problem of double-spending cannot be solved. Inside the main chain of XDAG, there are slices according to time, and each slice will be packaged for transactions. Green is the main block on the main chain, yellow is the witness block, blue is the transfer transaction, and black is the address block.

![XDAG_tenth.png](img/xdag/XDAG_tenth.png)

XDAG is similar to Bitcoin and is also the model of UTXO. The graphics may not be the same, but the essence is the same. The block in the figure is the connection block mentioned earlier, Tx0, Tx1, Tx2 are the real transactions, and Block A-D are the addresses.

![XDAG_eleventh.png](img/xdag/XDAG_eleventh.png)

The above figure shows that there are Merkle trees in the blockchain and a similar structure in XDAG.

![XDAG_twelfth.png](img/xdag/XDAG_twelfth.png)

The green main block in the above figure stores the hash of the transaction, similar to the Merkle tree. The calculation of PoW in XDAG is variable. Miners add the received transactions to their own hash calculation, each node does the calculation, and finally competes for the strongest calculation power to generate the main chain block.

![XDAG_thirteenth.png](img/xdag/XDAG_thirteenth.png)

In the above figure, calculating the local block/transaction hash is done layer by layer, and then filled in the new block (Main Block 2). After that, the sha256 calculation is done, which involves iterative calculation and obfuscation. For sending the result, only the calculated sha256 value needs to be sent instead of sending all the transactions, saving bandwidth resources. Thus, the miner only needs to continue to calculate the sha256, finally find the minimum hash to get the nonce and determine the new main block on the main chain, and in this way, the structure of the main chain is formed.

Note: To ensure fairness, the XDAG mining algorithm has been changed from sha256 to the RandomX algorithm.
How to Resolve Double Spend

This can be illustrated using diagrams as shown below:

![XDAG_fourteenth.png](img/xdag/XDAG_fourteenth.png)

If a transaction is generated between A1 and A2, a new connection block is generated to confirm the transaction, and the connection block is generated from the miners.

![XDAG_fifteenth.png](img/xdag/XDAG_fifteenth.png)

Suppose there are 10 XDAGs at address A1, and A1's wallet is maliciously copied twice, and two transfers are initiated at the same time. One transfer, Tx1, transfers 5 XDAGs from A1 to address A2, and the other, Tx2, transfers 7 XDAGs from A1 to address A2. The two transfers total 12 XDAGs, which exceeds the original 10 XDAGs for the A1 address, which is a typical double spend.

The logic in XDAG detection is that when the node receives Tx1 and Tx2 at the same time, the node generates a W block that references both Tx1 and Tx2. According to the stable ordering rules, Tx2 will be populated with fields with smaller order numbers when referenced by the W block. Therefore, Tx2 is processed first, and Tx1 is processed later, thus verifying that the Tx1 spend is a double spend. As a result, the internal block will mark the transaction block pointed to by the hash as rejected, and the transaction block Tx1 is recorded in the DAG forever and is not deleted.

Because users can choose which node to send their transactions to for verification, it is assumed here that if both transactions are sent to the same node, the first transaction referenced by the connection block is a valid transaction, and the second is an invalid transaction, solving the simple double spend problem in this way.

![XDAG_sixteenth.png](img/xdag/XDAG_sixteenth.png)

Suppose the premise is the same as before. There are 10 XDAGs at address A1, and A1's wallet is maliciously copied twice, and two transfers are initiated at the same time. One transfer, Tx1, transfers 5 XDAGs from A1 to address A2, and the other, Tx2, transfers 7 XDAGs from A1 to address A2. The two transfers total 12 XDAGs, which exceeds the original 10 XDAGs for the A1 address, which is a typical double spend.

But this time, the situation has changed. The person maliciously used technical means to connect the wallet to a different node, thus creating a double spend detection between different nodes.

At this point, PoW consensus comes into play, and a master block is generated every 64 seconds in XDAG. By comparing the difficulty of the master blocks, M1' and M1'', it is determined that M1'' is more challenging. Therefore, M1'' referenced block Tx2 in sorting priority over M1' referenced Tx1, so that Tx1 is detected as a double spend. As a result, the transaction block pointed to by the hash is marked as rejected in the internal block, while the transaction block Tx1 is recorded in the DAG forever and will not be deleted.

If there is a user who wants to cheat and sends these two transactions to different
5. XDAG supports high TPS.

Why can high TPS be achieved by XDAG? It is because the DAG is split into multiple localized blocks, which enables a high TPS similar to the effect of sharding computation.

The diagram (see figure below) displays a more comprehensive DAG structure of XDAG, where the wallet address blocks from previous diagrams are omitted for simplicity.

![XDAG_seventeenth.png](img/xdag/XDAG_seventeenth.png)

In the diagram, nodes denote different nodes, M denotes the main block mined by PoW, W denotes an additional block, which I named a witness block, and Tx is the transaction block.

Different nodes receive their transaction blocks separately, and the act of stitching the transaction blocks into the DAG is less coupled among them. The connection between them is established through the data interaction between nodes, which enables different nodes to absorb higher concurrency and achieve a high TPS.

At the same time, a master block is generated every 64 seconds through PoW, which greatly reduces the transaction confirmation time of decentralized public chains, and can be confirmed in 1 to 2 minutes on average.
## Advantages of XDAG

1. XDAG adopts the DAG + PoW method to break through and solve the limitations of traditional blockchain technology, which can greatly improve the scalability of the blockchain system. XDAG has the advantages of decentralization and high TPS while supporting PoW mining. XDAG network can still have high TPS under the most decentralized condition, such as PoW consensus, and the transaction volume can reach several thousand TPS.

Theoretically, the DAG approach allows for unlimited trading blocks between Main Blocks, but the actual situation still depends on network transmission speed and equipment performance on the network. The peak transaction volume has reached several thousand TPS, but due to network and hardware conditions, the transaction limit has not been measured.

2. Friendly to blockchain technology. A block in XDAG is also a transaction, and the address generated by the wallet will also generate a transaction in the network: Block = Transaction = Address. Some of the experiments currently done on Bitcoin and ETH can be done on XDAG because the founders have considered these in the design of the operating system.

3. Fast block generation, fast transfer, and no commission. Thanks to the characteristics of the infrastructure DAG technology, XDAG is currently set to generate a block every 64 seconds, the transfer can take about 3 minutes to the account, and the fee is zero. This is in the case of PoW decentralization, to achieve high TPS and fast transfer.

4. XDAG can achieve financial security, with no black hole address. All wallet addresses and transaction records in XDAG are blocks, so as long as there is a wallet, the wallet address must exist in the main network. If you try to transfer money to a non-existent address, it will fail, so there is no problem of transferring to a black hole address.

5. Originality of XDAG. The implementation of DAG+PoW in XDAG is groundbreaking and the earliest (Note: check the time through BitcoinTalk's Genesis post), and the code is original. XDAG provides C language and Java.

6. Truly community-driven. No project sponsor, no pre-mining, no ICO. Every XDAG is mined and mined out by miners fairly. The community team is made up of enthusiasts from different countries, together driving the evolution of XDAG.

7. ASIC resistance and CPU mining. XDAG adopts the RandomX mining algorithm to attract more CPU users to join mining, which provides more fairness.

With these advantages, XDAG can support many decentralized scenario applications, allowing more applications to be ported to XDAG, free from the pain of congestion and high fees of other public chains.
## XDAG Community

XDAG enthusiasts have set up a community autonomy team, which has gone from geek free development to orderly advancement. XDAG is making rapid progress, one step at a time, and the community is getting stronger and stronger.

Currently, the XDAG community autonomy team comprises more than 20 members from around the world, including those working on XDAG JAVA/C, PC wallet, Android wallet, IOS wallet, mining algorithm, mining software, network protocols, community website, testnet, community operations, and other different tasks.

The workload is still relatively large for an autonomous community, and more XDAG enthusiasts are needed to join the team. The community has established a community proposal mechanism (https://trello.com/b/nlSBXa2d/xps) and a developer incentive mechanism. You can check the community website at https://xdag.io/ where there is a "Reward Task" to attract more developers to the community. Anyone can suggest improvements on XPS and sponsor the development costs.

Here's an example:

![](img/xdag/XDAG_eighteenth.png)
## XDAG Links

Official Website: xdag.io

Bitcointalk: https://bitcointalk.org/index.php?topic=2552368.0

XDAG Whitepaper: https://github.com/XDagger/xdag/blob/master/WhitePaper.md

Github: https://github.com/XDagger

Blockchain Explorer: https://explorer.xdag.io/

Exchanges: coinex.com
## Welcome to XDAG Community

Discord: https://discord.gg/Nf72gd9

Telegram: https://t.me/dagger_cryptocurrency

Twitter: https://twitter.com/XDAG_Community

WeChat: xdag_dev