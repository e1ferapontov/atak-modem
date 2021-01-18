# atak-modem

#include <android/log.h>

#define  LOG_TAG    "testjni"
#define  ALOG(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

ALOG("tx_init: shreg %d\n", shreg);