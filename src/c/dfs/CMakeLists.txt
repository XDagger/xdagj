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
target_include_directories(dfs PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../jni/)

#copy library to target directory
set(DFS_TARGET_DIR ${CMAKE_CURRENT_LIST_DIR}/../../main/resources/native/${CMAKE_SYSTEM_NAME}-${CMAKE_SYSTEM_PROCESSOR})
add_custom_command(
    TARGET dfs
    POST_BUILD
    COMMAND mkdir -p ${DFS_TARGET_DIR}
    COMMAND cp $<TARGET_FILE:dfs> ${DFS_TARGET_DIR}
)

