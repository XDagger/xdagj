set(CMAKE_SYSTEM_NAME Darwin)
set(CMAKE_SYSTEM_PROCESSOR x86_64)
SET(CMAKE_C_COMPILER   clang)
SET(CMAKE_CXX_COMPILER clang++)
SET(MACOSX_VERSION_MIN 10.8)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mmacosx-version-min=${MACOSX_VERSION_MIN} -arch ${CMAKE_SYSTEM_PROCESSOR} -march=core2")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mmacosx-version-min=${MACOSX_VERSION_MIN} -arch ${CMAKE_SYSTEM_PROCESSOR} -march=core2 -std=c++98 -stdlib=libc++")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -mmacosx-version-min=${MACOSX_VERSION_MIN} -arch ${CMAKE_SYSTEM_PROCESSOR} -march=core2 -Wl,-dead_strip -Wl,-undefined,error -stdlib=libc++ -lc++")