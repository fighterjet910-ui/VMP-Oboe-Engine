# CMake ka minimum version
cmake_minimum_required(VERSION 3.10.2)

# Project ka naam
project("elite_audio_engine")

# 1. Source files ko define karein
add_library(elite_audio_engine 
            SHARED 
            EliteAudioEngine.cpp)

# 2. Android ki zaroori libraries dhoondhein
find_library(log-lib log)
find_library(android-lib android)

# 🔥 3. OPENSL ES DRIVER KO DHOONDHEIN (Naya step)
find_library(OpenSLES-lib OpenSLES)

# 4. VMP Compile Options (Trillion times faster execution ke liye)
target_compile_options(elite_audio_engine PRIVATE 
    -O3 
    -march=armv8-a 
    -mfpu=neon 
    -mfloat-abi=softfp)

# 5. Libraries link karein (OpenSLES yahan link ho gaya hai)
target_link_libraries(elite_audio_engine 
                      ${log-lib} 
                      ${android-lib}
                      ${OpenSLES-lib})
                      