#!/bin/bash
cmake -D CMAKE_BUILD_TYPE=Release . && cmake --build . && cp -f librandomx.* ../../main/resources/native && cp -f librandomx.* ../../test/resources/native
