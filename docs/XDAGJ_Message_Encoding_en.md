# Message Encoding
Xdagj network messages format.

## Types

### boolean
1 bit

### long
8 bytes

### int
4 bytes 

### byte[]
First write the number of bytes using [VLQ](https://en.wikipedia.org/wiki/Variable-length_quantity), then write each byte.

### string
First write the number of bytes using [VLQ](https://en.wikipedia.org/wiki/Variable-length_quantity), then write each byte of the string.

## VLQ
Variable length quantity.  This encoding allows for writing both large and small numbers in an efficient manner.