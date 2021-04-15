#!/bin/bash
cmake -D CMAKE_BUILD_TYPE=Release . && cmake --build . && cp -f libdfs.* ../../main/resources/native && cp -f libdfs.* ../../test/resources/native
