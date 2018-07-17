#!/bin/sh

mkdir nsa

sudo docker build -t terafirma .

sudo docker tag terafirma grassrootdocker/terafirma:latest

sudo docker push grassrootdocker/terafirma:latest

