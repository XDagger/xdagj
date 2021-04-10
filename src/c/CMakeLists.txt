# CMakeList.txt : Top-level CMake project file, do global configuration
# and include sub-projects here.
#
cmake_minimum_required (VERSION 3.5)

project ("xdaglib")

# Include sub-projects.
add_subdirectory ("dfs")
add_subdirectory ("randomx")
