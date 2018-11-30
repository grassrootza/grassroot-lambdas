import os
import json
import requests

DUCKLING_URL = os.getenv('DUCKLING_PARSE_URL', 'http://localhost:8000/parse')

def parse(event, context):
    inbound_text = event['queryStringParameters']['date_string']
    print('Text to parse: %s' % inbound_text)

    r = requests.post(DUCKLING_URL, data = { 'locale': 'en_ZA', 'text': inbound_text })
    print('Raw response from Duckling: %s' % r.content)
    parse_response = json.loads(r.text)
    print("Executed response, result: %s" % parse_response)
    
    if check_parse_succeeded(parse_response):
        primary_value = parse_response[0]['value']['value']
        print("And extracted: %s" % primary_value)
        desired_length = len("2018-11-11T11:11")
        return_string = primary_value[0:desired_length]
    else:
        print("Could not parse text")
        return_string = "ERROR_PARSING"

    response = {
        "statusCode": 200,
        "headers": {
            "content-type": "text/plain"
        },
        "body": return_string
    }

    return response

def check_parse_succeeded(parse_response):
    if not parse_response:
        return False
    elif not isinstance(parse_response, list):
        return False
    elif len(parse_response) == 0:
        return False
    elif 'value' not in parse_response[0]:
        return False
    elif 'value' not in parse_response[0]['value']:
        return False
    else:
        return True 