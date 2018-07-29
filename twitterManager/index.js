var express = require('express');

const request = require('request-promise');
const AWS = require('aws-sdk');

const crypto = require('crypto');
const OAuth = require('oauth-1.0a');
const queryString = require('query-string');

const TWITTER_APP_ID = process.env.TWITTER_APP_ID; // = 'consumer key'
const TWITTER_APP_SECRET = process.env.TWITTER_APP_SECRET; // = 'app secret'

const DDB_TABLE = "twitter_tokens";

const REDIRECT_URI = 'https://www.grassroot.org.za/social/connect/twitter';
// const REDIRECT_URI = 'http://localhost:4200/social/connect/twitter';

AWS.config.update({
    region: "eu-west-1",
});

const app = express();

const docClient = new AWS.DynamoDB.DocumentClient();
const s3Client = new AWS.S3();

const oauth = OAuth({
    consumer: { key: TWITTER_APP_ID, secret: TWITTER_APP_SECRET },
    signature_method: 'HMAC-SHA1',
    hash_function(base_string, key) {
        return crypto.createHmac('sha1', key).update(base_string).digest('base64');
    }
});

// used only for posts etc., because Twitter Oauth1 is pain-creating
var twitter = require('twitter');

app.get('/twitter/connect/request/:userId', (req, res) => {
    const request_data = {
        url: 'https://api.twitter.com/oauth/request_token',
        method: 'POST',
        data: { oauth_callback: REDIRECT_URI }
    }

    const oAuthHeaders = oauth.toHeader(oauth.authorize(request_data));
    console.log("generated OAuth headers: ", oAuthHeaders);
    
    const options = {
        url: request_data.url,
        method: request_data.method,
        headers: oAuthHeaders
    }

    request(options).then(response => {
        res.send(response);
    }).catch(error => {
        console.log("error: ", error);
        res.status(500);
    });
});

app.get('/twitter/connect/done/:userId', (req, res) => {
    const userId = req.params.userId;
    const oauthToken = req.query.oauth_token;
    const oauthVerifier = req.query.oauth_verifier;

    const request_data = {
        url: 'https://api.twitter.com/oauth/access_token',
        method: 'POST',
        data: { oauth_token: oauthToken }
    }

    const oAuthHeaders = oauth.toHeader(oauth.authorize(request_data));

    const options = {
        url: request_data.url,
        method: request_data.method,
        qs: {
            oauth_verifier: oauthVerifier
        },
        headers: oAuthHeaders
    };

    request(options).then(response => {
        const parsed = queryString.parse(response);
        console.log(parsed);
        
        var params = {
            TableName: DDB_TABLE,
            Item: {
              "userId": userId,
              "token": parsed.oauth_token,
              "secret": parsed.oauth_token_secret,
              "twitterId": parsed.user_id,
              "twitterName": parsed.screen_name
            }
          };
    
          docClient.put(params, (err, data) => {
            if (err) {
              console.log("error to add item, JSON: ", JSON.stringify(err, null, 2));
              res.status(500).send({ error: 'Could not complete connection' });
            } else {
              console.log("added item: ", JSON.stringify(data, null, 2));
              res.status(201).json({displayName: parsed.screen_name, twitterUserId: parsed.twitterId});
            }
          });

    }).catch(error => res.send(error));
});

app.get('/twitter/status/:userId', (req, res) => {
    fetchToken(req.params.userId, user_details => {
        const request_data = {
            url: 'https://api.twitter.com/1.1/users/show.json',
            method: 'GET',
            data: { user_id: user_details.twitterId }
        }
    
        const oAuthHeaders = oauth.toHeader(oauth.authorize(request_data));
    
        const options = {
            url: request_data.url,
            method: request_data.method,
            qs: request_data.data,
            headers: oAuthHeaders
        };

        request(options).then(response => {
            const twUserData = JSON.parse(response);
            // console.log("response: ", JSON.parse(response));
            // console.log("response img url: ", JSON.parse(response).profile_image_url_https);
            res.status(200).json({
                displayName: user_details.twitterName, 
                twitterUserId: user_details.twitterId,
                profileImageUrl: twUserData.profile_image_url_https
            });
        }).catch(error => {
            console.log(error);
            res.status(500);
        })
    });
});

app.post('/twitter/post/:userId', (req, res) => {
    fetchToken(req.params.userId, user_details => {
        if (req.query.mediaKey)
            uploadImageThenTweet(user_details, req, res);
        else
            postTweet(user_details, req.query.tweet, res);
    });
});

function uploadImageThenTweet(user_details, req, res) {
    const s3Params = {
        Bucket: req.query.mediaBucket,
        Key: req.query.mediaKey
    };
    
    // get the image from s3, then post to Twitter
    s3Client.getObject(s3Params, (err, data) => {
        if (err) {
            res.status(500).json(err);
            return;
        }

        const twClient = new twitter({
            consumer_key: TWITTER_APP_ID,
            consumer_secret: TWITTER_APP_SECRET,
            access_token_key: user_details.token,
            access_token_secret: user_details.secret
        });

        twClient.post('media/upload', { media: data.Body })
            .then(response => {
                console.log('media response: ', response);
                postTweet(user_details, req.query.tweet, res, response.media_id_string)
            })
            .catch(error => res.status(500).json(error));
    })

}

function postTweet(user_details, tweet, res, mediaId) {
    const postReturnFn = () => res.status(201).json({'isUserConnectionValid': true, 'isPostSuccessful': true});
    const errrorReturnFn = error => {
        // res.status(500).json({'isUserConnectionValid': !!user_details, 'isPostSuccessful': false})
        res.status(500).json(error);
    };

    const twClient = new twitter({
        consumer_key: TWITTER_APP_ID,
        consumer_secret: TWITTER_APP_SECRET,
        access_token_key: user_details.token,
        access_token_secret: user_details.secret
    });

    const request_params = !!mediaId ? { status: tweet, media_ids: mediaId } : { status: tweet };

    console.log('request params: ', request_params);

    twClient.post('statuses/update', request_params)
        .then(postReturnFn)
        .catch(errrorReturnFn);

}

function assembleOptions(request_data, user_details) {
    return {
        url: request_data.url,
        method: request_data.method,
        qs: request_data.data,
        headers: oauthHeaders(request_data, user_details)
    };
}

function oauthHeaders(request_data, user_details) {
    const oauth_token = {
        key: user_details.token,
        secret: user_details.secret
    };

    console.log('oauth token: ', oauth_token);

    return oauth.toHeader(oauth.authorize(request_data, oauth_token));
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
  
app.listen(3000, () => console.log(`Listening on port 3000`));
// module.exports = app;