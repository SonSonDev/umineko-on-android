FROM ubuntu:14.04

WORKDIR /build

COPY .travis_before_install.sh .
COPY env.txt .

RUN apt-get update && apt-get -y upgrade && apt-get install -y build-essential automake curl make xz-utils zip unzip openssh-client && mkdir -p src/extlib/src/steam-sdk/
RUN bash -c ". env.txt && . .travis_before_install.sh && export -p > env.txt"
