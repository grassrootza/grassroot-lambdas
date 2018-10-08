const AWS = require('aws-sdk')
AWS.config.update({
    region: 'eu-west-1',
});

const SQS = new AWS.SQS()
const queue = 'https://sqs.eu-west-1.amazonaws.com/257542705753/whatsapp-bulk-outbound'

exports.handler = async (event) => {
    // Flood SQS Queue
    console.log('Initiating flood');
    for (let i=0; i<50; i++) {
        console.log('Sending messages ... ', i);
        await SQS.sendMessageBatch({ Entries: flooder(), QueueUrl: queue }).promise()
    }
    console.log('Messages dispatched');
    return 'done'
}

const flooder = () => {
  let entries = []

  for (let i=0; i<10; i++) {
      entries.push({
        Id: 'id'+parseInt(Math.random()*1000000),
        MessageBody: JSON.stringify({
            recipient: '27813074085',
            messages: ['Hello from Lambda']
        }) 
      })
  };
  return entries
}

exports.handler(null);
