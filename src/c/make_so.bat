cmake -DCMAKE_BUILD_TYPE=Debug -G "CodeBlocks - MinGW Makefiles" && cmake --build . && copy /Y libdfs.dll ..\main\resources\native && copy /Y libdfs.dll ..\test\resources\native
