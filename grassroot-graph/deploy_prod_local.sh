#!/bin/bash

gradle clean build -x test
gradle createDockerFile
docker build build/ -t graphimage:latest
docker run graphimage:latest
