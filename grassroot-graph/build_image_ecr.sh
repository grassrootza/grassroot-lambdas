#!/bin/bash

echo "Compiling source"
gradle clean build -x test
gradle createDockerFile
echo "Building image and tagging"
docker build build/ -t grassroot-graph:latest
docker tag grassroot-graph:latest 257542705753.dkr.ecr.eu-west-1.amazonaws.com/grassroot-graph:latest

echo "Logging in to ECR and pushing"
eval $(aws ecr get-login --no-include-email --region eu-west-1)
docker push 257542705753.dkr.ecr.eu-west-1.amazonaws.com/grassroot-graph:latest
