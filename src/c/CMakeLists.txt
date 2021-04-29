cmake_minimum_required(VERSION 3.4.1)

PROJECT(dfs)

#dfslib flags
set(DFSLIB_COMMON_FLAGS -g -O2 -Wall)

add_definitions(${DFSLIB_COMMON_FLAGS})

#dfslib files
set(DFSLIB_SOURCE
    crc.h
    crc.c
    dfslib_crypt.c
    dfslib_random.c
    dfslib_string.c
    dfsrsa.c
    dnet_crypt.c
    dfs_jni.cc)

add_library(dfs SHARED ${DFSLIB_SOURCE})


