var express = require('express');

var AWS = require('aws-sdk');
var s3 = new AWS.S3();

var taskBucket = 'grassroot-task-images';
var lwireBucket = 'grassroot-livewire-media';
var generalBucket = 'grassroot-media-files-general';

const app = express();

app.get('/:mediaType/:imageKey', (req, res) => {
		var type = req.params.mediaType;
		var bucket = type == 'TASK_IMAGE' ? taskBucket : 
				type == 'LIVEWIRE_MEDIA' ? lwireBucket : generalBucket;
		const params = {
			Bucket: bucket,
			Key: req.params.imageKey
		};

		console.log("fetching image with params: ", params);
		// might also get a signed URL but then would just be doing a redirect, both are a tradeoff, so might as well
		s3.getObject(params, (err, data) => {
			if (err) {
				console.log("Error fetching image! : ", err);
				res.send("Error! Could not retrieve image: " + err);
			}	else {
				// console.log("Fetching image, writing response, data : ", data);
				res.writeHead(200, {'Content-Type': data.ContentType });
				res.end(data.Body);
			}
		})
	});

// app.listen(3000, () => console.log('Example app listening on port 3000!'));

module.exports = app;