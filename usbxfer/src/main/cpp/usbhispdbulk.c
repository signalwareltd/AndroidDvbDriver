#include <jni.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <errno.h>
#include <string.h>
#include <android/log.h>

#define TAG "JNI_HiSpeedBulk"

JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_UsbHiSpeedBulk_jni_1setInterface(JNIEnv *env, jclass type, jint fd,
                                                        jint interfaceId, jint alternateSettings) {
    struct usbdevfs_setinterface setint;
    setint.altsetting = (unsigned int) alternateSettings;
    setint.interface = (unsigned int) interfaceId;

    if (ioctl(fd, USBDEVFS_SETINTERFACE, &setint)) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "ioctl error %d text %s", errno, strerror(errno));
        return -errno;
    } else {
        return 0;
    }
}