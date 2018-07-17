#!/bin/sh

# aws ecr get-login --region eu-west-1
# aws s3api get-object --bucket grassroot-nlu --key activation/feersum_setup.sh feersum_setup.sh
# source ./feersum_setup.sh
export FLASK_APP=start_application.py
flask run --host=0.0.0.0 > output.txt 2>&1 &
# python trainer.py > training_output.txt 2>&1 &
# python checker.py > system_status.txt 2>&1 &
tail -f output.txt