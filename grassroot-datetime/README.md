# Grassroot NLU 

A nlu component that takes in text input and returns parsed entity values.

## Getting Started

Open a terminal in the directory with start_application.py. Enter the following lines as root:

 ~$ bash install_dependencies.sh

 At this point make sure to have your AWS services properly configured.

 ~$ export AUTH_TOKEN='enter your feersum authorisation token'

 ~$ sudo bash activate_me.sh

This will start a flask server on 0.0.0.0:5000/ (where '0.0.0.0' is the hosts machine's ip)
 
Note: For your own convenience, sudo all commands.

To kill the program:

 ~$ sudo bash kill_me.sh

### Prerequisites

All dependencies should already be handled by the install_dependencies.sh file, however, for the sake of transparency (and anticipation of the unkown), here's a look under the hood:

* DynamoDB or MongoDB (preferably both)
* Python 3.6.1 and its standard library as well as the following additional libraries:
  * pymongo(when using MongoDB)
  * flask
  * rasa_nlu
  * mitie
  * duckling
  * schedule
  * boto3


## Databases

This API uses DynamoDB by default, but this can easily be swapped out for Mongodb by simply opening the config.py file and uncommenting:

 #database = MongoDB

while commenting out
 
 #database = DynamoDB

Note: You'll need to have a MongoDB running in the background to use MongoDB features.


## Deployment

After running $ bash install_dependencies.sh and $ bash activate_me.sh you should be good to go. Just make sure that your database is up and running (esp. when using MongoDB or a local instance of DynamoDB)

This API includes an auto-training routine aimed at making the results better over time. This feature may be turned on or off at anytime without affecting the main program. By default, it is turned on upon initialisation. To deactivate auto-training (which commences by default at 00:30 every night),
simply open the activate_me.sh file and comment out:

 python trainer.py > training_output.txt 2>&1 &

Then run:

 $ sudo bash restart.sh

Should you find the training data collected at runtime suboptimal you can also train a new model remotely. The most effective way to do this would be to copy the already existing training_data.json in the current_models directory, add as many more instances to it as required, then train a new model (make sure rasa_nlu is installed and configured on the training machine), and finally upload the new model to s3 (You'll have to delete the contents of 'model/current_model/' and replace them with your newly generated model).

 To do all of this in stealth mode, you could alternatively clone this repo, run 'bash install_dependencies.sh', copy your edited training_data.json to the main directory (the one with config.py), open a python3 console and type in 'from config import *' (this will download the current model in use from s3 so it might take a few minutes), and after this command completes type 'auto_trainer()'. This will train a new model based on the newly passed training data and automatically upload the model to s3 (if our new model has better performance than the old one).

# DateTime parser

This API includes a date-time parser for formalising date values. For example an input of 'tomorrow at 5 in the evening' will return 'YYYY-MM-DDT17:00'.
To call this API and pass values to it:

  /datetime?date_string=tomorrow at 5 in the evening

The base url is http://hostmachineIP where 'hostmachineIP' is as advertised, your host machines IP. To utilise this service you will need to have a Feersum nlu authorisation token. See [here](http://feersum.io/).

# Word Distance

Also included is a word distance function accessed with the following postfix:

  /distance?text=water

# Docker

Should wish to put this API in a docker container (though at time of print this has already been done), you will need the Grassroot docker credentials. Having acquired them, you may then open a terminal in this directory and type in 'sudo docker login',
enter the required information, and then run 'sudo docker build -t whateveryouwanttocallyourimage .' (Note the period at the end of the command, it is part of the command syntax). Should you wish to add more functionality to the API, make sure that system dependencies are properly added to the Dockerfile and any new API dependencies are properly added to the depends.sh file. 

## Built With

* [flask](http://flask.pocoo.org/)
* [rasa_nlu](http://rasa.ai/)
* [MongoDB](https://www.mongodb.com/)
* [DynamoDB](https://aws.amazon.com/dynamodb/)
* [Amazon s3](https://aws.amazon.com/s3‎/)
* [feersum_nlu](https://feersum.io)

## Authors

* **Luke Jordan** - *Lead developer*

* **FRTNX** - *Apprentice* 


## License

This project is licensed under the BSD-3 Clause License