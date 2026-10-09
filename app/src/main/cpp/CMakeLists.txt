cmake_minimum_required(VERSION 3.22)
project(umineko_android C CXX)

set(ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../..)
set(TP ${ROOT}/third_party)
set(ENG ${ROOT}/engine/src)
set(EXT ${ENG}/extlib/src)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---------- SDL2 + extensions (bibliotheques partagees) ----------
add_subdirectory(${TP}/SDL2 SDL2-build)

set(SDL2IMAGE_VENDORED ON CACHE BOOL "" FORCE)
set(SDL2IMAGE_BACKEND_STB ON CACHE BOOL "" FORCE)
foreach(opt AVIF JXL TIF WEBP SAMPLES INSTALL DEPS_SHARED)
    set(SDL2IMAGE_${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(SDL2IMAGE_PNG ON CACHE BOOL "" FORCE)
set(SDL2IMAGE_JPG ON CACHE BOOL "" FORCE)
add_subdirectory(${TP}/SDL2_image SDL2_image-build)

set(SDL2MIXER_VENDORED ON CACHE BOOL "" FORCE)
foreach(opt FLAC MOD MIDI OPUS WAVPACK GME SAMPLES INSTALL DEPS_SHARED MP3_MPG123 VORBIS_VORBISFILE)
    set(SDL2MIXER_${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(SDL2MIXER_VORBIS STB CACHE STRING "" FORCE)
set(SDL2MIXER_MP3 ON CACHE BOOL "" FORCE)
set(SDL2MIXER_WAVE ON CACHE BOOL "" FORCE)
add_subdirectory(${TP}/SDL2_mixer SDL2_mixer-build)

# ---------- libogg / libvorbis (decodage pour le moteur) ----------
set(INSTALL_DOCS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS_SAVE ${BUILD_SHARED_LIBS})
set(BUILD_SHARED_LIBS OFF)
add_subdirectory(${TP}/ogg ogg-build)

set(VORBIS_SRC ${TP}/vorbis/lib)
add_library(vorbis_static STATIC
    ${VORBIS_SRC}/analysis.c ${VORBIS_SRC}/bitrate.c ${VORBIS_SRC}/block.c
    ${VORBIS_SRC}/codebook.c ${VORBIS_SRC}/envelope.c ${VORBIS_SRC}/floor0.c
    ${VORBIS_SRC}/floor1.c ${VORBIS_SRC}/info.c ${VORBIS_SRC}/lookup.c
    ${VORBIS_SRC}/lpc.c ${VORBIS_SRC}/lsp.c ${VORBIS_SRC}/mapping0.c
    ${VORBIS_SRC}/mdct.c ${VORBIS_SRC}/psy.c ${VORBIS_SRC}/registry.c
    ${VORBIS_SRC}/res0.c ${VORBIS_SRC}/sharedbook.c ${VORBIS_SRC}/smallft.c
    ${VORBIS_SRC}/synthesis.c ${VORBIS_SRC}/vorbisenc.c ${VORBIS_SRC}/vorbisfile.c
    ${VORBIS_SRC}/window.c)
target_include_directories(vorbis_static PUBLIC ${TP}/vorbis/include PRIVATE ${VORBIS_SRC})
target_link_libraries(vorbis_static PUBLIC ogg)

# ---------- FreeType ----------
foreach(opt ZLIB BZIP2 PNG HarfBuzz BROTLI)
    set(FT_DISABLE_${opt} ON CACHE BOOL "" FORCE)
endforeach()
add_subdirectory(${TP}/freetype freetype-build)

# ---------- bzip2 ----------
set(BZ ${EXT}/bzip2-1.0.4)
add_library(bz2_static STATIC
    ${BZ}/blocksort.c ${BZ}/bzlib.c ${BZ}/compress.c ${BZ}/crctable.c
    ${BZ}/decompress.c ${BZ}/huffman.c ${BZ}/randtable.c)
target_include_directories(bz2_static PUBLIC ${BZ})

# ---------- smpeg2 (videos .mpg) ----------
set(SM ${EXT}/smpeg-2.0.0)
add_library(smpeg2_static STATIC
    ${SM}/smpeg.cpp ${SM}/MPEG.cpp ${SM}/MPEGlist.cpp ${SM}/MPEGring.cpp
    ${SM}/MPEGstream.cpp ${SM}/MPEGsystem.cpp
    ${SM}/audio/MPEGaudio.cpp ${SM}/audio/bitwindow.cpp ${SM}/audio/filter.cpp
    ${SM}/audio/filter_2.cpp ${SM}/audio/hufftable.cpp ${SM}/audio/mpeglayer1.cpp
    ${SM}/audio/mpeglayer2.cpp ${SM}/audio/mpeglayer3.cpp ${SM}/audio/mpegtable.cpp
    ${SM}/audio/mpegtoraw.cpp
    ${SM}/video/MPEGvideo.cpp ${SM}/video/decoders.cpp ${SM}/video/floatdct.cpp
    ${SM}/video/gdith.cpp ${SM}/video/jrevdct.cpp ${SM}/video/motionvec.cpp
    ${SM}/video/parseblock.cpp ${SM}/video/readfile.cpp ${SM}/video/util.cpp
    ${SM}/video/video.cpp)
target_include_directories(smpeg2_static PUBLIC ${SM})
target_compile_definitions(smpeg2_static PRIVATE
    STDC_HEADERS=1 HAVE_SYS_TYPES_H=1 HAVE_SYS_STAT_H=1 HAVE_STDLIB_H=1 HAVE_STRING_H=1
    HAVE_MEMORY_H=1 HAVE_STRINGS_H=1 HAVE_INTTYPES_H=1 HAVE_STDINT_H=1 HAVE_UNISTD_H=1
    HAVE_DLFCN_H=1 _THREAD_SAFE THREADED_AUDIO NOCONTROLS)
target_link_libraries(smpeg2_static PUBLIC SDL2::SDL2)
target_compile_options(smpeg2_static PRIVATE -w -Wno-c++11-narrowing)

# ---------- Moteur Ponscripter ----------
add_library(main SHARED
    ${ENG}/AnimationInfo.cpp ${ENG}/bstrlib.c ${ENG}/bstrwrap.cpp
    ${ENG}/cp932_encoding.cpp ${ENG}/DirectReader.cpp ${ENG}/DirPaths.cpp
    ${ENG}/DirtyRect.cpp ${ENG}/encoding.cpp ${ENG}/expression.cpp ${ENG}/font.cpp
    ${ENG}/Fontinfo.cpp ${ENG}/graphics_accelerated.cpp ${ENG}/NsaReader.cpp
    ${ENG}/Ponscripter.cpp ${ENG}/PonscripterLabel.cpp
    ${ENG}/PonscripterLabel_animation.cpp ${ENG}/PonscripterLabel_command.cpp
    ${ENG}/PonscripterLabel_effect.cpp ${ENG}/PonscripterLabel_effect_breakup.cpp
    ${ENG}/PonscripterLabel_effect_cascade.cpp ${ENG}/PonscripterLabel_effect_trig.cpp
    ${ENG}/PonscripterLabel_event.cpp ${ENG}/PonscripterLabel_ext.cpp
    ${ENG}/PonscripterLabel_file.cpp ${ENG}/PonscripterLabel_file2.cpp
    ${ENG}/PonscripterLabel_image.cpp ${ENG}/PonscripterLabel_rmenu.cpp
    ${ENG}/PonscripterLabel_sound.cpp ${ENG}/PonscripterLabel_text.cpp
    ${ENG}/PonscripterMessage.cpp ${ENG}/prng.cpp ${ENG}/pstring.cpp
    ${ENG}/resize_image.cpp ${ENG}/SarReader.cpp ${ENG}/ScriptHandler.cpp
    ${ENG}/ScriptParser.cpp ${ENG}/ScriptParser_command.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/resources_stub.cpp ${CMAKE_CURRENT_SOURCE_DIR}/android_log.cpp ${CMAKE_CURRENT_SOURCE_DIR}/android_bridge.cpp)

target_include_directories(main PRIVATE ${ENG})
target_compile_definitions(main PRIVATE LINUX USE_OGG_VORBIS UTF8_FILESYSTEM ANDROID_PORT)
target_compile_options(main PRIVATE -w -Wno-c++11-narrowing $<$<COMPILE_LANGUAGE:CXX>:-std=gnu++14 -fpermissive>)
target_link_libraries(main PRIVATE
    SDL2::SDL2 SDL2_image::SDL2_image SDL2_mixer::SDL2_mixer
    vorbis_static ogg freetype bz2_static smpeg2_static
    log android z)




