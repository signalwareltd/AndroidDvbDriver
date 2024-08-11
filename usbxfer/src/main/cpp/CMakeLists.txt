cmake_minimum_required(VERSION 3.3.1)

PROJECT( "UsbXfer" C )

FILE ( GLOB_RECURSE SOURCES_LIB "*.c" )

SET ( CMAKE_C_FLAGS         "-Wall" )
SET ( CMAKE_C_FLAGS_DEBUG   "-g -DDEBUG")
SET ( CMAKE_C_FLAGS_RELEASE "-O1 -DNDEBUG" ) # Reduce optimization level due to suspected deadlocks on some Samsung devices

FIND_LIBRARY( LOG_LIB log )
FIND_LIBRARY( OPEN_SLES_LIB OpenSLES )

ADD_LIBRARY( ${PROJECT_NAME} SHARED ${SOURCES_LIB} )
TARGET_LINK_LIBRARIES( ${PROJECT_NAME} ${LOG_LIB} ${OPEN_SLES_LIB} )