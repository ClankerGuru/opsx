#!/usr/bin/env sh
./gradlew :app:installShadowDist -q && ./app/build/install/app-shadow/bin/opsx "$@"
