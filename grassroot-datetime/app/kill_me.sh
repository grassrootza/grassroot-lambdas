#!/bin/bash  

ps -ef | grep flask | grep -v grep | awk '{print $2}' | xargs kill # xargs sudo kill (for local deployment)
ps -ef | grep "python trainer.py" | awk '{print $2}' | xargs kill  # xargs sudo kill (for local deployment)
ps -ef | grep "python checker.py" | awk '{print $2}' | xargs kill  # xargs sudo kill (for local deployment)
fuser -k 5000/tcp