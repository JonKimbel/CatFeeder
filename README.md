# Cat Feeder

Feeds cat.

## Project Status

*   V1 - MVP, reliable auto-feeder. **100% complete.**
*   V2 - fancier & more robust auto-feeder with camera, cleaner code.
    **Not started.**
*   V3 - very clean & robust code. **Not planned.**

## Repo Setup

Run the following from this directory to set up the pre-commit checks.

```
git config core.hooksPath hooks
```

## Construction

### Electronics

*   Buy a [Particle Photon](https://smile.amazon.com/Particle-Reprogrammable-Development-Prototyping-Electronics/dp/B016YNU1A0/)
*   Buy a [standard-size servo capable of rotating at least 180°](https://smile.amazon.com/gp/product/B07BVR816V/)
*   Buy a power supply capable of powering both (5V @ ~3A)

Connect the servo's PWM pin to `D0` on the Photon, preferably with solder on a
protoboard. Connect the servo and Photon to the power supply.

### Mechanism

*   3D print all of the non-"mock" objects in [this Onshape project](https://cad.onshape.com/documents/860fec32e4bfca1868b60fc5/w/15e7cf7a15cfd49a1405bb9f/e/bb58b1b1518a81a205b9291e)
    *   Alternatively, [download the STLs directly](https://drive.google.com/open?id=10rxtRvgRGIzVJ8uLc_RcWd5JzaJx-dav)
*   Buy a ricotta cheese container, eat the cheese and wash the container
*   Buy socket head bolts, nuts, and threaded inserts ([1](https://smile.amazon.com/gp/product/B07F75DMHF/),
    [2](https://smile.amazon.com/gp/product/B0728FBS77/),
    [3](https://smile.amazon.com/gp/product/B07WH59N6T/)):
    *   1x 25mm M4 socket head bolt
    *   1x 20mm M4 socket head bolt
    *   2x 16mm M4 socket head bolt
    *   2x 8mm M4 socket head bolt
    *   2x 8mm M2 socket head bolt
    *   4x M4 nuts
    *   2x 6mm long M4 threaded inserts
    *   2x 4mm long M2 threaded inserts
    *   4x servo mounting screws

Cut a hole in the bottom of the ricotta container to line up with the hole in
the top of the food chute.

Hot glue the following together:

*   Ricotta container to food chute
*   Food chute to food hopper
*   Lower legs to feet

Insert threaded inserts in the blind holes (apart from the four holes used to
mount the servo). Bolt everything together where bolt holes are present.

## Software

### API (backend + frontend)

#### Before you begin

Download and unzip a release of
[protoc](https://github.com/protocolbuffers/protobuf/releases) and add the
contents of its `bin/` directory to your PATH.

Download the 0.3.9.4 release of nanopb from [here](https://jpa.kapsi.fi/nanopb/)
and add the contents of its `generator-bin/` directory to your PATH, then run
the following commands from this directory.

While you're on their site, consider donating to the nanopb developer :)

Why 0.3.9.4? Because it's currently compatible with Particle Device OS.
[nanopb is partially available in the Device OS](https://github.com/particle-iot/device-os/issues/1502),
so we have to fill in the gaps ourselves (see `photon/nanopb/`). If the versions
don't match we'll have a bad time. If it wasn't for this, you could use the
latest version of nanopb (updating `photon/nanopb/` to match).

#### Compiling

Note that you don't need to do this unless you're upgrading to a later version
of nanopb or have edited cat_feeder.proto.

Run the following command:

```
protoc -I=api --nanopb_out=photon api/cat_feeder.proto
```

### Server

#### Before you begin

1.  Install JDK 11
    (Linux: `apt install openjdk-11-jdk`,
    Windows: [download this and set JAVA_HOME](https://www.oracle.com/technetwork/java/javase/downloads/jdk11-downloads-5066655.html))
1.  Install Bazel from
    [here](https://docs.bazel.build/versions/master/install-windows.html#installing-menu).
1.  Create a file at `com/jonkimbel/catfeeder/backend/alert/TwilioInfo.java`
    according to [these docs](/com/jonkimbel/catfeeder/backend/alert/README.md).

#### Running locally

To compile and run the server, run the following from this directory:

```
bazel run //com/jonkimbel/catfeeder/backend:backend
```

OR open the IntelliJ project and run the `backend` configuration.

#### Packaging a .jar {#jar}

To generate a deployable jar, run the following:

```
bazel build //com/jonkimbel/catfeeder/backend:backend_deploy.jar
```

The jar will be under `bazel-bin`, the output of the command will tell you
exactly where.

#### Deploying to a server

[Package up a jar](#jar) and copy it to `/usr/lib/catfeeder` on the server.

If you want to require a password for server access, create a text file at
`/usr/lib/catfeeder/password.txt` containing the password (and nothing else).

Create a file at `/etc/systemd/system/catfeeder@.service` like so:

```
[Unit]
Description=catfeeder: runs the catfeeder server.

[Service]
Type=simple
ExecStart= /usr/bin/java -jar /usr/lib/catfeeder/backend_deploy.jar

[Install]
WantedBy=default.target
```

Run `sudo systemctl enable catfeeder@catfeeder` to make this service start at
system boot (important in case the server gets restarted), then run
`sudo systemctl start catfeeder@catfeeder` to start the service immediately.

If you ever need to debug a server deployed in this way, use
`journalctl -u catfeeder@catfeeder` to inspect the STDOUT & STDERR.

### Particle Photon (cat feeder firmware)

#### Before you begin

1.  Install the Particle CLI following the steps
    [here](https://docs.particle.io/tutorials/developer-tools/cli/).
1.  COMPLETELY OPTIONAL: Set up local build toolchain following
    [these steps](https://docs.particle.io/tutorials/developer-tools/cli/#compile-and-flash-code-locally)
1.  Create a file at `photon/backend-info.h` and `#define BACKEND_DOMAIN` to the
    domain for your server.

#### Compiling & flashing

To compile and flash the cat feeder firmware to your device, run the following
from the `photon/` directory. Change "CatFeeder" to whatever you named your
device in Particle Cloud.

```
particle compile photon --saveTo=out.bin && particle flash CatFeeder out.bin
```

NOTE: The "photon" in this command refers to the device model, the
[Particle Photon](https://store.particle.io/collections/wifi/products/photon).
It is NOT providing the name of the folder the code relies upon.
