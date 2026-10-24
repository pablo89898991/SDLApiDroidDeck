# sdl3droiddeck: SDL3 and the SDL3 bridge's controller loop, built into the app (libSDL3.so + libmain.so, loaded by
# the bridge in its own ":sdl3" process). Written by patch-droiddeck.sh (step 30); the sources are SdlBridge's.
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SDL_ANDROID_JAR OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory(SDL)
add_library(main SHARED src/main.c)
target_link_libraries(main PRIVATE SDL3::SDL3 log)
