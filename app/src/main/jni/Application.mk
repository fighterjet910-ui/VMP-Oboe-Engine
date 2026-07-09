# Force target build for modern 64-bit ARM phone processors
APP_ABI := arm64-v8a

# Target modern Android platform runtime execution modes
APP_PLATFORM := android-26

# Force compiler toolchain to optimize performance structures using C++17 definitions
APP_STL := c++_static
APP_CPPFLAGS := -std=c++17 -frtti -fexceptions
