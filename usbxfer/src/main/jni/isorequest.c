#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <linux/usbdevice_fs.h>
#include <android/log.h>
#include <memory.h>

#define TAG "JNI_IsoRequest"

typedef struct isoreq {
    unsigned char endpointAddr;
    int number_of_packets;
    int id;
    size_t packetSize;
} isoreq_t;

JNIEXPORT jlong JNICALL
                Java_info_martinmarinov_usbxfer_IsoRequest_jni_1allocate_1urb(JNIEnv *env, jclass type, jint endpointAddr, jint id, jint maxPackets, jint packetSize) {
    struct usbdevfs_urb * urb = (struct usbdevfs_urb *) malloc(sizeof(struct usbdevfs_urb) + (size_t) (maxPackets * sizeof(struct usbdevfs_iso_packet_desc)));

    urb->usercontext = (isoreq_t *) malloc(sizeof(isoreq_t));
    isoreq_t * isor = (isoreq_t *) urb->usercontext;
    isor->id = (int) id;
    isor->endpointAddr = (unsigned char) endpointAddr;
    isor->number_of_packets = maxPackets;
    isor->packetSize = (size_t) packetSize;

    urb->buffer_length = packetSize * maxPackets;
    urb->buffer = malloc((size_t) urb->buffer_length);

    return (jlong) urb;
}


JNIEXPORT void JNICALL
Java_info_martinmarinov_usbxfer_IsoRequest_jni_1reset_1urb(JNIEnv *env, jclass type, jlong ptr) {
    int i;
    struct usbdevfs_urb * urb = (struct usbdevfs_urb *) ptr;
    isoreq_t * isor = (isoreq_t *) urb->usercontext;

    urb->endpoint = isor->endpointAddr;
    urb->type = USBDEVFS_URB_TYPE_BULK;
    urb->flags = 0;
    urb->actual_length = 0;
    urb->start_frame = 0;

    urb->error_count = 0;
    urb->signr = 0;
    urb->status = -1;

    urb->number_of_packets = isor->number_of_packets;

    for (i = 0; i < urb->number_of_packets; i++) {
        urb->iso_frame_desc[i].actual_length = (unsigned int) 0;
        urb->iso_frame_desc[i].length = (unsigned int) isor->packetSize;
        urb->iso_frame_desc[i].status = (unsigned int) -1;
    }
}


JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_IsoRequest_jni_1submit(JNIEnv *env, jclass type, jlong ptr, jint fd) {
    struct usbdevfs_urb * urb = (struct usbdevfs_urb *) ptr;
    if (ioctl(fd, USBDEVFS_SUBMITURB, urb)) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "ioctl error %d text %s during submit", errno, strerror(errno));
        return -errno;
    } else {
        return 0;
    }
}


JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_IsoRequest_jni_1cancel(JNIEnv *env, jclass type, jlong ptr, jint fd) {
    struct usbdevfs_urb * urb = (struct usbdevfs_urb *) ptr;
    if (ioctl(fd, USBDEVFS_DISCARDURB, urb)) {
        if (errno == EINVAL) { // This happens if the request has already completed.
            return 0;
        }
        __android_log_print(ANDROID_LOG_ERROR, TAG, "ioctl error %d text %s during cancel", errno, strerror(errno));
        return -errno;
    } else {
        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_IsoRequest_jni_1read(JNIEnv *env, jclass type, jlong ptr,
                                                    jbyteArray data_) {
    struct usbdevfs_urb * urb = (struct usbdevfs_urb *) ptr;

    // Copy whole packet, will not check individual packet status for now
    (*env)->SetByteArrayRegion(env, data_, 0, urb->buffer_length, urb->buffer);
    return urb->buffer_length;
}

JNIEXPORT void JNICALL
Java_info_martinmarinov_usbxfer_IsoRequest_jni_1free_1urb(JNIEnv *env, jclass type, jlong ptr) {
    struct usbdevfs_urb * urb = (struct usbdevfs_urb *) ptr;

    free(urb->buffer);
    free(urb->usercontext);
    free(urb);
}

JNIEXPORT jint JNICALL
Java_info_martinmarinov_usbxfer_IsoRequest_jni_1get_1ready_1packet_1id(JNIEnv *env, jclass type, jint fd,
                                                                      jboolean wait) {
    struct usbdevfs_urb * urb = NULL;
    isoreq_t * isor;

    if (ioctl(fd, wait ? USBDEVFS_REAPURB : USBDEVFS_REAPURBNDELAY, &urb) < 0) {
        if (errno == EAGAIN) {
            // means try again
            return -1;
        } else {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "ioctl error %d text %s during get ready packet id", errno,
                                strerror(errno));
            return -errno;
        }
    }
    if (urb == NULL) return -1;

    isor = (isoreq_t *) urb->usercontext;
    return isor->id;
}