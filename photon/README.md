This directory contains firmware for the Cat Feeder, intended for deployment
on a Particle Photon.

Before this code will compile, you will need to add a file named
`backend-info.h` in this directory containing the following:

```cpp
#ifndef BACKEND_INFO_H
#define BACKEND_INFO_H

#define BACKEND_DOMAIN "<your backend's domain name, e.g. google.com>"

#endif // BACKEND_INFO_H
```

The .pb files are compiled protobufs. To change them, change the API definition
in the `/api` directory then compile them by following the instructions in the
[main README](/README.md).
