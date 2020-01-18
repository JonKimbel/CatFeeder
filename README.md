# Cat Feeder

Feeds cat.

## Setup

Run the following from this directory to set up the pre-commit checks.

```
git config core.hooksPath hooks
```

## Compiling

### API (backend + frontend)

Download and unzip a release of
[protoc](https://github.com/protocolbuffers/protobuf/releases) and add the
contents of its `bin/` directory to your PATH.

Download the latest stable release of nanopb from
[here](https://jpa.kapsi.fi/nanopb/) and add the contents of generator-bin to
your PATH, then run the following commands from this directory.

```
protoc -I=api --nanopb_out=photon api/cat-feeder.proto
```

While you're on their site, consider donating to the nanopb developer :)

### Server

#### Before you begin

1.  Install JDK 11 from [here](https://www.oracle.com/technetwork/java/javase/downloads/jdk11-downloads-5066655.html) and set JAVA_HOME
1.  Install Bazel from
[here](https://docs.bazel.build/versions/master/install-windows.html#installing-menu).

#### Compiling & running

To compile and run the server, run the following from this directory:

```
bazel run //com/jonkimbel/catfeeder/backend:backend
```

OR open the IntelliJ project and run the `backend` configuration.

### Particle Photon (cat feeder firmware)

#### Before you begin

1.  Install the Particle CLI following the steps
    [here](https://docs.particle.io/tutorials/developer-tools/cli/).
1.  COMPLETELY OPTIONAL: Set up local build toolchain following
    [these steps](https://docs.particle.io/tutorials/developer-tools/cli/#compile-and-flash-code-locally)
1.  Create a file at `photon/backend-info.h` and `#define BACKEND_DOMAIN` to the
    domain pointing at the server you're hosting.

#### Compiling & flashing

To compile the cat feeder firmware, run the following from the `photon/`
directory:

```
particle compile photon --saveTo=out.bin
```

NOTE: The "photon" in this command refers to the device model, the
[Particle Photon](https://store.particle.io/collections/wifi/products/photon).
It is NOT providing the name of the folder the code relies upon.

TODO: figure out how to flash to photon then document here.
