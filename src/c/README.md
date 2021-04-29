# Xdag Native Library

## Build on x86_64 Linux

Build on linux supports cross-compiling to the following platforms:

1. Linux-aarch64
2. Linux-x86_64
3. Windows-x86_64

Prerequisites:
- gcc-7+
- glibc-2.25
- cmake
- automake
- autoconf
- gcc-x86_64-linux-gnu
- gcc-aarch64-linux-gnu 
- g++-x86_64-linux-gnu
- g++-aarch64-linux-gnu
- binutils-x86_64-linux-gnu
- binutils-aarch64-linux-gnu

Steps to update your gcc and glibc
```
sudo add-apt-repository ppa:ubuntu-toolchain-r/test
sudo apt-get update 
sudo apt-get install gcc-7

sudo update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-7 100
sudo update-alternatives --config gcc

```

Steps to build on Debian/Ubuntu based distributions with a x86_64 machine:
```
sudo apt install cmake automake autoconf gcc gcc-aarch64-linux-gnu gcc-mingw-w64 g++-aarch64-linux-gnu g++-mingw-w64 binutils binutils-aarch64-linux-gnu binutils-mingw-w64
```

## Linux-x86_64
```
find . -name "*.o"  | xargs rm -f
find . -name "*.lo"  | xargs rm -f
mkdir build && cd build
cmake -vvv -DCMAKE_TOOLCHAIN_FILE=../cmake/toolchain-Linux-x86_64.cmake ../
make -j$(nproc)
```

## Linux-aarch_64
```
find . -name "*.o"  | xargs rm -f
find . -name "*.lo"  | xargs rm -f
mkdir build && cd build 
cmake -vvv -DCMAKE_TOOLCHAIN_FILE=../cmake/toolchain-Linux-aarch64.cmake ../
make -j$(nproc)
```

## Windows-x86_64 x86_64-pc-cygwin 
```
download cygwin x86_64 from https://cygwin.com/ and install cmake,autoconf,automake,make,gcc-g++ while install cygwin
make sure your gcc compiler is x86_64-pc-cygwin then

find . -name "*.o"  | xargs rm -f
find . -name "*.lo"  | xargs rm -f
mkdir build && cd build
cmake -vvv -DCMAKE_TOOLCHAIN_FILE=../cmake/toolchain-Windows-x86_64.cmake ../
make -j$(nproc)
```

## Build on macOS
```
Prerequisites:
1. clang
2. cmake
3. autoconf
4. automake

find . -name "*.o"  | xargs rm -f
find . -name "*.lo"  | xargs rm -f
mkdir build && cd build
cmake -vvv -DCMAKE_TOOLCHAIN_FILE=../cmake/toolchain-Darwin-x86_64.cmake ../
make -j$(nproc)
```
