cmake_minimum_required(VERSION 3.8)
project(ponscripter)

list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake;${CMAKE_CURRENT_SOURCE_DIR}/cmake/sdl2)

option(USE_STEAM "Enable Steam Support" OFF)
option(USE_CPU_GFX "Custom graphics using intrinsics" ON)
option(ENABLE_GAME_CONTROLLERS "Enable support for game controllers" ON)

# Workaround for bug on macOS where if you have Mono installed, CMake will prefer the (old) versions of JPEG and PNG that ship with it, even though you'll be linking with the dylibs in /usr/local/
set(CMAKE_FIND_FRAMEWORK LAST)

if(USE_STEAM)
	add_subdirectory(src/extlib/src/steam-sdk)
endif()
find_package(BZip2 1.0.4 REQUIRED)
find_package(Freetype 2.3.5 REQUIRED)
find_package(JPEG 62 REQUIRED)
find_package(PNG 1.2.24 REQUIRED)
find_package(OGG 1.1.3 REQUIRED)
find_package(Vorbis 1.3.4 REQUIRED)
find_package(VorbisFile 1.3.4 REQUIRED)
find_package(SDL2 2.0.3 REQUIRED)
find_package(SDL2_image 2.0.0 REQUIRED)
find_package(SDL2_mixer 2.0.0 REQUIRED)
find_package(SMPEG2 2.0.0 REQUIRED)
find_package(ZLIB 1.2.3 REQUIRED)

add_subdirectory(src)
