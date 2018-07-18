#!/usr/bin/env python
# encoding:utf-8

print('Ignition.')
import os
import sys
# import psutil
import uuid, time, datetime, pprint
from datetime_engine import *
from flask import Flask, request, url_for, render_template, Response
# from distance import *

app = Flask(__name__)

@app.route('/')
def index():
    return render_template("textbox.html")

@app.route('/datetime')
def date():
    d_string = request.args.get('date_string')
    datetime_response = datetime_engine(d_string)
    print('main recieved from dt: %s' % datetime_response)
    return Response(datetime_response, mimetype='application/json')

# for tests
@app.route('/shutdown')
def shutdown():
    func = request.environ.get('werkzeug.server.shutdown')
    if func is None:
        raise RuntimeError('Not running with the Werkzeug Server :(')
    func()
    return 'Server shutting down'


"""
@app.route('/distance')
def w_distance():
    text = request.args.get('text').lower().strip()
    return Response(json.dumps(distance(text)), mimetype='application/json')
"""

if __name__ == '__main__':
    app.run(host='0.0.0.0')
