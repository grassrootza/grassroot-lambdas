#!/bin/bash

echo "Building image and tagging"
docker build -t grassroot-datetime:latest .
docker tag grassroot-datetime:latest 257542705753.dkr.ecr.eu-west-1.amazonaws.com/grassroot-datetime:latest

echo "Logging in to ECR and pushing"
eval $(aws ecr get-login --no-include-email --region eu-west-1)
docker push 257542705753.dkr.ecr.eu-west-1.amazonaws.com/grassroot-datetime:latest
