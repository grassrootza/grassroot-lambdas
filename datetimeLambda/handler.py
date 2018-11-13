import os
import json
import requests

DUCKLING_URL = os.getenv('DUCKLING_PARSE_URL', 'http://localhost:8000/parse')

def parse(event, context):
    # body = {
    #     "message": "Go Serverless v1.0! Your function executed successfully!",
    #     "input": event
    # }

    print('Event from the proxy: %s' % event)
    # inbound_event = json.loads(event)
    inbound_text = event['queryStringParameters']['date_string']
    print('Text to parse: %s' % inbound_text)

    r = requests.post(DUCKLING_URL, data = { 'locale': 'en_ZA', 'text': inbound_text })
    parse_response = json.loads(r.text)
    print("Executed response, result: %s" % parse_response)
    
    primary_value = parse_response[0]['value']['value']
    print("And extracted: %s" % primary_value)
    desired_length = len("2018-11-11T11:11:11")
    return_string = primary_value[0:desired_length]

    response = {
        "statusCode": 200,
        "body": return_string
    }

    return response
