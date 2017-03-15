![YouTube video](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

# Android DVB-T Driver

This driver provides a simple TCP based API that allows
controlling USB DVB-T/DVB-T2 tuners on Android. It gives other apps access to
the raw DVB MPEG-2 TS stream.

The driver is a simplified user space port of a subset of
the V4L2 Linux kernel drivers. Under the hood it uses Android USB Host API
with a small helper library that allows fast kernel controlled USB bulk transfers.

# Open Beta

The driver is available on Google Play for developers as an open beta. You must first sign up before
accessing the download page.

1. [Sign up for DVB-T Driver Open Beta on Google Play](https://play.google.com/apps/testing/info.martinmarinov.dvbdriver)
2. [Download DVB-T Driver on Google Play](https://play.google.com/store/apps/details?id=info.martinmarinov.dvbdriver)

Or download latest pre-compiled APK: [app-release.apk](app/app-release.apk).

# Usage

The driver has an Activity that demonstrates direct connection to hardware
and allows dumping the raw DVB-T/T2 transport stream to a file.

A socket interface is built on top of that functionality to allow the driver
to be used by third party apps over TCP. If you want to use the driver via the
exposed TCP interface, you have to do the following steps from your own app:

1. Launch an android intent
```java
startActivityForResult(new Intent(Intent.ACTION_VIEW)
            .setData(new Uri.Builder().scheme("dtvdriver").build()), SOME_CODE);
```
1. If the activity returns successfully, you will receive two TCP port numbers to bind to.
One of them is the control port where you can send commands and receive responses.
The other port will provide the raw TS stream.
1. You can then start sending commands over the control port and process the TS stream.

For actual details on the protocol, take a look at the `dvbservice` module. There
are no official docs provided, if you would like to volunteer to write one get in touch with me.

There is also a handy "debug" mode that allows the driver to play the pre-recorded TS dumps
as if they are coming from a real device. This mode is extremely useful during development.

Note that this driver does not provide channel/programme scanning/playback capabilities or any transport stream processing.
If you would like to write an app for DVB playback, you have to implement all of these yourself.

# Supported hardware

Currently:
* RTL2832 with tuner chip R820t
* RTL2832 with tuner chip E4000
* RTL2832 with tuner chip R828D (DVB-T2 support for MN88473 devices)
* RTL2832 with tuner chip FC0012

Shortly I will be implementing support for other RTL2832 devices. The driver is not limited
to these devices only. If a device has a Linux kernel driver, then it probably could be ported.
If you have ported a driver for a device, get in touch with me so we can add it to the main driver.

# Apps that use the driver

There are currently no apps using the driver. This is about to change soon, keep tuned :)