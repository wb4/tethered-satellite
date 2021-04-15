#!/bin/bash

SRC_DIR=src
BUILD_DIR=build
IMAGE_DIR=images

mkdir -p $BUILD_DIR && \
rm -f $BUILD_DIR/*.class $BUILD_DIR/*.jar && \
javac -d $BUILD_DIR $SRC_DIR/*.java && \
jar cfe $BUILD_DIR/TetherSim.jar TetherSim $IMAGE_DIR -C $BUILD_DIR .
