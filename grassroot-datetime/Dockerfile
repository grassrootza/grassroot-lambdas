#Download base image ubuntu 16.04
FROM tiangolo/uwsgi-nginx-flask:python3.6-alpine3.7

WORKDIR /app

RUN apk add --no-cache alpine-sdk
RUN apk add --no-cache openjdk7-jre

ADD ./app /app

RUN sh depends.sh

ENV LANG en_US.UTF-8  
ENV LANGUAGE en_US:en  
ENV LC_ALL en_US.UTF-8