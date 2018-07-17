#!/bin/sh

sudo apt-get update

if hash pip; then 
    echo "pip is installed"
else
    sudo apt install python3-pip
fi

if hash git; then 
    echo "git is installed"
else
    sudo apt install git
fi

# conda install libgcc

pip install flask

pip install googletrans

pip install pymongo

pip install schedule

pip install boto3

pip install rasa_nlu==0.10.6

pip install duckling

pip install git+https://github.com/mit-nlp/MITIE.git

pip install psutil

pip install dateparser

[ -f MITIE-models-v0.2.tar.bz2 ] && echo "Requirement already satisfied
 MITIE-models-v0.2.tar.bz2" || wget -P ./ https://github.com/mit-nlp/MITIE/releases/download/v0.4/MITIE-models-v0.2.tar.bz2
 
tar xvjf MITIE-models-v0.2.tar.bz2 --directory ./current_model/model/

sudo rm -r MITIE-models-v0.2.tar.bz2

if hash aws; then
    echo "aws is installed.
    You're ready to go. Run 'sudo bash activate_me.sh' to fire up the API."
else
	sudo apt install awscli;
	echo "Your AWS credentials are not configured.
	Please run 'aws configure' and enter the associated details."
fi