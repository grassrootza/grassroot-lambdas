const express = require('express');
const bodyParser = require('body-parser');

const MessagingResponse = require('twilio').twiml.MessagingResponse;

const request = require('request-promise');

const AWS = require('aws-sdk');

AWS.config.update({
    region: "eu-west-1",
});

const app = express();
app.use(bodyParser.urlencoded({extended: false}));

const docClient = new AWS.DynamoDB.DocumentClient();

app.post('/inbound', (req, res) => {
    const content = getMessageContent(req);
    console.log('decoded content: ', content); 

    const response = getMessageReply(content);
    res.writeHead(200, {'Content-Type': response.content_type});
    res.end(response.body);

    logIncoming(content);
});

// module.exports = app;
app.listen(3000, () => console.log(`Listening on port 3000`));

// we will be swapping these out in future, as possibly / probably not using Twilio, so stashing them

const getMessageContent = (req) => {
    console.log('message body: ', req.body);
    const incoming_text = req.body['Body'];
    const incoming_phone = req.body['From'] ? req.body['From'].substring('whatsapp:+'.length) : '<<Error>>';
    return {
        'message': incoming_text,
        'from': incoming_phone
    };
}

const getMessageReply = (content) => {
    const twiml = new MessagingResponse();

    // note: !! this appears to be FILO, hence do in reverse order
    twiml.message('Also, here is what we think you said: ' + content.message);

    twiml.message('Oh, the things Grassroot will do with WhatsApp. ' + 
        'What they are, yet we know not; but they shall be the terrors of the earth.');

    return {
        content_type: 'text/xml',
        body: twiml.toString() 
    }
}

const logIncoming = (content) => {
    var params = {
        TableName: 'chatConversationLogs',
        Item: {
          'userId': content.from,
          'timestamp': Date.now(),
          'message': content.message
        }
    };

    docClient.put(params, (err, data) => {
        if (err)
            console.log('Aargh, error: ', JSON.stringify(err, null, 2));
        else
            console.log('Worked! Result: ', JSON.stringify(data, null, 2));
    });
}