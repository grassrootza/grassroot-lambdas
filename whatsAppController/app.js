const express = require('express');
const bodyParser = require('body-parser');

const MessagingResponse = require('twilio').twiml.MessagingResponse;

const request = require('request-promise');

const AWS = require('aws-sdk');

AWS.config.update({
    region: "eu-west-1",
});

const app = express();

app.use(bodyParser.json());

app.post('/inbound', (req, res) => {
    console.log('Got a message! Query params: ', req.query);
    console.log('And body? ', req.body);
    const twiml = new MessagingResponse();

    twiml.message('The Robots are coming! Head for the hills!');
  
    res.writeHead(200, {'Content-Type': 'text/xml'});
    res.end(twiml.toString());
});

module.exports = app;