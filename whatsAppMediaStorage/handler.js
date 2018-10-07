'use strict';

const config = require('config');
const uuid = require('uuid/v4');
const fs = require('fs');

const request = require('request-promise');

const AWS = require('aws-sdk');
AWS.config.update({
    region: 'eu-west-1',
});

const docClient = new AWS.DynamoDB.DocumentClient();
const s3Client = new AWS.S3();

const storageBucket = config.get('s3.bucket');
const storageFolder = config.get('s3.folder');
const dynamoDbTable = config.get('dynamo.table');

module.exports.store = async (event, context) => {

  console.log('event received: ', event);
  // const payload = JSON.parse(event);
  const payload = event;
  console.log('Received this payload to handle: ', payload);

  console.log('Fetching media file from WhatsApp');
  const mediaFile = await retrieveMediaFromWhatsApp(payload['media_id']);
  console.log('Retrieved media file, proceeding to stash in S3.');
  
  if (!!mediaFile) {
    // fs.writeFile('./image.jpg', mediaFile, 'binary', () => console.log('File written'));
    const ourImageId = uuid();
    console.log('Stashing in S3, with uuid: ', ourImageId);
    const s3Upload = await storeFileInS3(ourImageId, payload['media_type'], mediaFile)
    console.log('Completed s3 upload, result: ', s3Upload);
  }

  return {
    statusCode: 200,
    body: JSON.stringify({
      message: 'Go Serverless v1.0! Your function executed successfully!',
      input: event,
    }),
  };

};

const retrieveMediaFromWhatsApp = (media_id_whatsapp) => {
  const options = {
    method: 'GET',
    encoding: 'binary',
    uri: `${config.get('whatsapp.url')}/${media_id_whatsapp}`,
    auth: {
      'bearer': config.get('whatsapp.token')
    }
  }

  return request(options)
    .then(data => {
      console.log('Fetched data, returning');
      return data;
    })
    .catch(err => {
      console.log('Error fetching data: ', err);
      return false;
    });
}

const storeFileInS3 = (uuid, mimeType, data) => {
  const body = new Buffer(data, 'binary');
  
  const uploadParams = {
    Bucket: storageBucket,
    Key: `${storageFolder}/${uuid}`,
    ContentType: mimeType,
    Body: body
  };

  return s3Client.upload(uploadParams).promise()
    .then(result => {
      console.log('Upload result: ', result);
      return true;
    })
    .catch(err => { 
      console.log('Upload failed! Error: ', err);
      return false;
    });
}