# Android DvbDriver

This driver app provides a simple TCP based API that allows
controlling USB DVB-T tuners on Android. It gives the host app access to
the raw DVB MPEG-2 TS stream.

The app is a very simplified user space port of a small subset of
the V4L2 Linux kernel drivers. It is mainly written using Android USB Host API
with a small helper library that allows fast kernel controlled USB bulk transfer.

# Usage

The app exposes an interface that demonstrates direct usage of the library
to connect to the hardware and dump the transport stream into a file.

If you want to use the app via the exposed TCP interface, you have to do
the following steps from your own app:

1. Launch an android intent
```java
startActivityForResult(new Intent(Intent.ACTION_VIEW)
            .setData(new Uri.Builder().scheme("dtvdriver").build()), SOME_CODE);
```
1. If the activity returns successfully, you will receive two TCP port numbers to bind to.
One of them is the control port where you can send commands to and receive responses.
The other port will provide the raw TS stream.
1. You can then start sending commands over the control port and processing the TCP stream.

For actual details on the protocol, take a look at the `dvbservice` module. There
are no official docs provided, if you would like to volunteer to write one get in touch with me.

There is also a handy "debug" mode that allows the driver to play the pre-recorded TS dumps
as if they are coming from a real device. This mode is extremely useful during development.

Note that this app does not provide channel/programme scanning/playback capabilities or any transport stream processing.
If you would like to write an app for DVB-T playback, you have to implement all of these yourself.

# Supported hardware

Currently:
* RTL2832u with tuner chip R820t

Shortly I will be implementing support for other RTL2832u devices. The driver is not limited
to these devices only. If a device has a Linux kernel driver, then it probably could be ported.
If you have ported a driver for a device, get in touch with me so we can add it to the main driver.

# Apps that use the driver

There are currently no apps using the driver. This is about to change soon, keep tuned :)