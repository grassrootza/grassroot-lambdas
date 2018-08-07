var express = require('express');
const request = require('request-promise');
const AWS = require('aws-sdk');

const FB_APP_ID = process.env.FB_APP_ID;
const FB_APP_SECRET = process.env.FB_APP_SECRET;

// const REDIRECT_URI = 'http://localhost:3000/facebook/connect/done';
const REDIRECT_URI = process.env.FB_REDIRECT_URL;

const DDB_TABLE = process.env.DDB_TABLE;

const FB_API_VERSION = 'v2.12';

AWS.config.update({
    region: "eu-west-1",
});

const app = express();
const docClient = new AWS.DynamoDB.DocumentClient();

app.post('/facebook/connect/request/:userId', (req, res) => { 
  const callBackParam = encodeURIComponent(JSON.stringify({userId: req.params.userId}));
  const redirectUrl = `https://www.facebook.com/${FB_API_VERSION}/dialog/oauth?client_id=${FB_APP_ID}&response_type=code&redirect_uri=${REDIRECT_URI}&state=${callBackParam}&scope=manage_pages,publish_pages`;
  console.log('redirectUrl: ', redirectUrl);
  return res.redirect(301, redirectUrl);
});

app.get('/facebook/connect/done/:userId', (req, res) => {
  const userId  = req.params.userId;

  const options = {
    method: 'GET',
    uri: `https://graph.facebook.com/${FB_API_VERSION}/oauth/access_token`,
    qs: {
      client_id: FB_APP_ID,
      redirect_uri: REDIRECT_URI,
      client_secret: FB_APP_SECRET,
      code: req.query.code
    }
  };

  request(options).then(fbRes => {
    const parsedRes = JSON.parse(fbRes);
    const access_token = parsedRes.access_token;

    const getDataOptions = {
      nethod: 'GET',
      uri: `https://graph.facebook.com/${FB_API_VERSION}/me`,
      qs: {
        access_token: access_token
      }
    };

    request(getDataOptions).then(finalFbRes => {
      const finalRes = JSON.parse(finalFbRes);
      
      var params = {
        TableName: DDB_TABLE,
        Item: {
          "userId": userId,
          "token": access_token,
          "fbUserId": finalRes.id,
          "fbName": finalRes.name
        }
      }

      docClient.put(params, (err, data) => {
        if (err) {
          console.log("error to add item, JSON: ", JSON.stringify(err, null, 2));
          res.status(500).send({ error: 'Could not complete connection' });
        } else {
          console.log("added item: ", JSON.stringify(data, null, 2));
          res.status(201).json(finalRes);
        }
      });

    });
  });
});

app.post('/facebook/delete/:userId', (req, res) => {
  fetchToken(req.params.userId, userData => {
    // okay then delete the thing (well, delete our record)
    var params = {
      TableName: DDB_TABLE,
      Key: {
        "userId": req.params.userId
      }
    };

    console.log('Deleting item: ', params);

    docClient.delete(params, (err, data) => {
      if (err) {
        console.log('error deleting item: ', JSON.stringify(err, null, 2));
        res.status(500).send({ error: 'Could not remove record' });
      } else {
        console.log('completed deletion: ', JSON.stringify(data, null, 2));
        res.status(200).end();
      }
    })
  })
});

app.get('/facebook/pages/:userId', (req, res) => {
  console.time('fetchingPages');
  fetchToken(req.params.userId, userData => {
      if (userData && userData.token)
        fetchPages(userData, userPages => {
          res.json(userPages);
          console.timeEnd('fetchingPages');
        });
      else
        res.json([]);
  });
})

app.post('/facebook/post/:userId', (req, res) => {
  const page = req.query.pageId;
  const returnFn = fbRes => res.json({'isUserConnectionValid': true, 'isPostSuccessful': true});

  fetchToken(req.params.userId, userData => {
    if (page)
      fetchPages(userData, pageData => {
        var selectedPageData = pageData.find(p => p.page_id === page);
        post(selectedPageData.page_id, selectedPageData.page_token, req.query.message, returnFn);
      })
    else
      post(userData.fbUserId, userData.token, req.query.message, returnFn);
  });
});

function post(id, access_token, message, callback) {
  const options = {
      method: 'POST',
      url: `https://graph.facebook.com/${FB_API_VERSION}/${id}/feed`,
      qs: {
        access_token: access_token,
        message: message
      }
    }

    request(options).then(callback).catch(err => {
      console.log('error posting', err);
    })
}

app.post('/facebook/photo/:userId', (req, res) => {
  const page = req.query.pageId;
  const returnFn = fbRes => res.json({'isUserConnectionValid': true, 'isPostSuccessful': true});

  fetchToken(req.params.userId, userData => {
    if (!page)
      photo(userData.fbUserId, userData.token, req.query.imageUrl, req.query.message, returnFn);
    else
      fetchPages(userData, pageData => {
        var selected = pageData.find(p => p.page_id === page);
        photo(selected.page_id, selected.page_token, req.query.imageUrl, req.query.message, returnFn);
      })
  });
});

function photo(id, access_token, url, caption, callback) {
  const options = {
    method: 'POST',
    url: `https://graph.facebook.com/${FB_API_VERSION}/${id}/photos`,
    qs: {
      access_token: access_token,
      caption: caption,
      url: url
    }
  }

  request(options).then(callback).catch(err => {
    console.log('error posting photo: ', err);
  });
}

function fetchToken(userId, callback) {
  var params = {
    TableName: DDB_TABLE,
    Key: {
      "userId": userId
    }
  };

  docClient.get(params, (err, data) => {
    console.log("got item: ", data);
    if (data && data.Item) {
      callback(data.Item);
    } else {
      callback(null);
    }
  });
}

function fetchPages(userData, callback) {
  const options  = {
    method: 'GET',
    uri: `https://graph.facebook.com/${FB_API_VERSION}/${userData.fbUserId}/accounts`,
    qs: {
      access_token: userData.token
    }
  };

  console.time('graphApi');
  request(options).then(fbRes => {
    console.timeEnd('graphApi');
    const parsedRes = JSON.parse(fbRes);
    const pageArray = parsedRes.data;
    const mappedArray = pageArray.map(item => transformed = {page_name: item.name, page_id: item.id, page_token: item.access_token });
    mappedArray.push(user = {page_name: userData.fbName, page_id: userData.fbUserId, page_token: userData.token })
    callback(mappedArray);
  });
}



// app.listen(3000, () => console.log(`Listening on port 3000, ddb table: ${DDB_TABLE}`));

module.exports = app;