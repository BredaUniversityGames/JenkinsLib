# Tiny project for the local test Jenkins (test/jenkins/local, job cmake-local).
cmake_minimum_required(VERSION 3.21)
project(SampleGame LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

add_executable(sample_game main.cpp)

enable_testing()
add_executable(sample_tests tests.cpp)
add_test(NAME sample_tests COMMAND sample_tests)
