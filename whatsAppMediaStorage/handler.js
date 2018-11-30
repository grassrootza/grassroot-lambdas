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

  if (!mediaFile) {
    return { statusCode: 404, body: JSON.stringify({message: 'Failed! Could not find on WhatsApp', input: event})}
  }

  const hasEntityId = !!payload['entity_uid'];
  
  // fs.writeFile('./image.jpg', mediaFile, 'binary', () => console.log('File written'));
  const ourImageId = uuid();
  console.log('Stashing in S3, with uuid: ', ourImageId);
  const s3Upload = await storeFileInS3(ourImageId, payload['media_type'], mediaFile)
  console.log('Completed s3 upload, result: ', s3Upload);

  let id_to_return;
  if (hasEntityId) {
    const dynamoRecord = await insertRecordInDynamoDb(ourImageId, payload);
    console.log('Result of Dynamo DB insertion: ', dynamoRecord);
    id_to_return = ourImageId;
  } else {
    const putResult = await storeRecordOnPlatform(ourImageId, payload);
    console.log('Result of platform storage: ', putResult);
    id_to_return = putResult;
  }

  return {
    statusCode: 200,
    body: JSON.stringify({
      media_record_id: id_to_return,
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

const insertRecordInDynamoDb = (media_file_id, payload) => {
  const currentMillis = Date.now();

  // maybe, just maybe, we could have two of the same assoc entity at some milli, but seems extremely unlikely
  const item = {
    assoc_entity_id: payload['entity_uid'],
    media_file_id: media_file_id,
    stored_timestamp: currentMillis,
    bucket: storageBucket,
    folder: storageFolder,
    media_type: payload['media_type'],
    assoc_entity_type: payload['entity_type'],
    submitting_user_id: payload['user_id']
  };

  const params = {
    TableName: dynamoDbTable,
    Item: item
  };

  return docClient.put(params).promise();
}

const storeRecordOnPlatform = async (media_file_id, payload) => {
  console.log('Fetching token for user first');

  const tokenOptions = {
    uri: `${config.get('platform.url')}${config.get('platform.path.token')}`,
    method: 'POST',
    auth: { 'bearer': config.get('platform.token') },
    qs: {
      userId: payload['user_id']
    }
  }
  // console.log('Options: ', tokenOptions);
  const token = await request(tokenOptions);
  // console.log('Retrieved token from platform? : ', token);

  const options = {
    uri: `${config.get('platform.url')}${config.get('platform.path.store')}`,
    method: 'POST',
    auth: { 'bearer': token },
    qs: {
      bucket: storageBucket,
      imageKey: `${storageFolder}/${media_file_id}`,
      mimeType: payload['media_type']
    }
  }

  console.log('Storing media record, options: ', options);

  return request(options);
}