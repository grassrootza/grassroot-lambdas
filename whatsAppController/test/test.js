process.env.NODE_ENV = 'test'

const config = require('config');

const assert = require('assert');

const chai = require('chai');
const logger = require('debug')('grassroot:whatsapp:test');

const chaiHttp = require('chai-http');
const should = chai.should();
const spies = require('chai-spies');

const sinon = require('sinon');
const rp = require('request-promise');

const server = require('../main');
const users = require('../users');
const conversation = require('../conversation/conversation.js');

chai.use(chaiHttp);
chai.use(spies);

const userSpy = chai.spy(users.fetchUserId);

// some standard set up
const testPhone = config.get('__comment.testPhone');
const testUserId = 'test_user_1';
const authHeader = { 'bearer': config.get('auth.platform') }

const errorResponse = conversation.assembleErrorMsg(testUserId, 'restart');

const mockUserIdOptions = {
  method: 'POST',
  uri: config.get('users.url') + config.get('users.path.id'),
  auth: authHeader,
  qs: { 'msisdn': testPhone }
};

describe('Basic check that test scaffolding in order', () => {
  describe('#indexOf()', function() {
    it('should return -1 when the value is not present', () => {
      assert.equal([1,2,3].indexOf(4), -1);
    });
  });
});

describe('Simple check that status returns okay', () => {
  it('Should respond with status okay', (done) => {
    chai.request(server)
      .get('/status')
      .end((err, res) => {
        res.should.have.status(200);
        done();
      });
  })
})

describe('Tests of handling status messages properly', () => {
  it('Should just return an empty body if a status update', (done) => {
    chai.request(server)
      .post('/inbound')
      .send({
        "statuses":[{
          "id": "ABGGFlA5FpafAgo6tHcNmNjXmuSf",
          "recipient_id": "16315555555",
          "status": "sent",
          "timestamp": "1518694700"
        }]
      })
      .end((err, res) => {
        res.should.have.status(200);
        res.body.should.be.a('object');
        userSpy.should.have.been.called.exactly(0);
        done();
      })
  });
});

const restartTest = (done) => { // this also helps clean out this user in the temproary chat logs
  const testMessage = assembleTestMessage('Restart');
  chai.request(server)
    .post('/inbound')
    .send(testMessage)
    .end((err, res) => {
      res.should.have.status(200);
      res.should.not.be.empty;
      res.should.be.json;
      res.body.should.deep.equal(conversation.restartConversation(testUserId, null));
      done();
    })
};

describe('Test Izwi Lami messages', () => {
  
  beforeEach(() => {
    this.post = sinon.stub(rp, 'post');
    // set up returning a mock user options thing    
    this.post.withArgs(mockUserIdOptions).returns(testUserId);
  });

  afterEach(() => {
    this.post = rp.post.restore();
  });

  it('Should respond to Hello correctly', (done) => {
    const testMessage = assembleTestMessage("Hello");
    logger('Sending greeting to server');
    chai.request(server)
      .post('/inbound')
      .send(testMessage)
      .end((err, res) => testGreetingOkay(done, res))
  }).timeout(0); // just because of it hitting Rasa on cloud (may replace with stub in future), so, disable timeout

  it('Should respond to Izwi Lami correctly', (done) => {
    const testMessage = assembleTestMessage("Izwi Lami");

    logger('Initiating server request');
    chai.request(server)
      .post('/inbound')
      .send(testMessage)
      .end((err, res) => {
        logger('Checking results, first round');
        res.should.have.status(200);
        res.should.not.be.empty;
        res.should.be.json;
        logger('Inspecting reply properties');
        res.body.should.have.property('replyMessages');
        res.body.should.have.property('menuPayload');
        res.body.replyMessages.length.should.equal(4);
        res.body.menuPayload.should.have.members(['service_type::24hr_hcf', 'service_type::shelter', 'service_type::thuthuzela']);
        done();
      })
  }).timeout(0); // just because of it hitting Rasa on cloud (may replace with stub in future), so, disable timeout

  it('Should handle menu selection in Izwi Lami correctly', (done) => {
    const testMessage = assembleTestMessage('2');

    chai.request(server)
      .post('/inbound')
      .send(testMessage)
      .end((err, res) => {
        logger('Menu selection response received');
        res.should.have.status(200);
        res.should.not.be.empty;
        res.should.be.json;
        res.body.should.have.property('replyMessages');
        res.body.should.have.property('menuPayload');
        res.body.replyMessages.length.should.equal(1);
        res.body.menuPayload.should.be.empty;
        done();
    });
    
  }).timeout(0);

  it('Should handle province selection in Izwi Lami correctly', (done) => {
    const testMessage = assembleTestMessage('Gauteng');

    logger('Selecting province');
    chai.request(server)
      .post('/inbound')
      .send(testMessage)
      .end((err, res) => {
        res.should.have.status(200);
        res.should.not.be.empty;
        res.should.be.json;
        res.body.should.have.property('replyMessages');
        res.body.should.have.property('menuPayload');
        res.body.replyMessages.length.should.be.gt(2);
        res.body.replyMessages.should.include('These messages have contact information only, not links. Standard rates apply if you dial the numbers below.');
        res.body.replyMessages.should.include('Remember: ask for the morning after pill, ARVs, vaccinations and counseling at the clinic/hospital. http://bit.ly/2MzUIbU Viewing the map uses data.');
        logger('Received response: ', res.body.replyMessages);
        done();
      })
  }).timeout(0);

  it('Restarting to clean up', restartTest).timeout(0);
  
})

const testPhrase = 'Abahlali';
describe('Test platform search (mostly)', () => {

  beforeEach(() => {
    this.post = sinon.stub(rp, 'post');
    this.post.withArgs(mockUserIdOptions).returns(testUserId);
    
    const phraseSearch = (phrase, broadSearch) => { return {
      method: 'POST',
      uri: config.get('platform.url') + config.get('platform.paths.phrase.search'),
      qs: {
          'phrase': phrase,
          'userId': testUserId,
          'broadSearch': broadSearch
      },
      auth: authHeader,
      json: true
    } };

    // logger('Setting up with arguments: ', phraseSearch(testPhrase, true));

    const searchResponseFound = {
      'entityFound': true,
      'entityType': 'GROUP',
      'entityUid': 'test_entity',
      'responseMessages': ['Done! You have joined Abahlali']
    };

    this.post.withArgs(phraseSearch(testPhrase, false)).returns(searchResponseFound);
    this.post.withArgs(phraseSearch(testPhrase, true)).returns(searchResponseFound);

  });

  afterEach(() => {
    this.post = rp.post.restore();
  });

  it('Should respond to join word correctly', (done) => {
    const testMessage = assembleTestMessage(testPhrase);

    logger('Initiating server request, with first join word');
    chai.request(server)
      .post('/inbound')
      .send(testMessage)
      .end((err, res) => {
        logger('Checking results, first round: ', res.body);
        res.should.have.status(200);
        res.should.not.be.empty;
        res.should.be.json;
        logger('Inspecting reply properties');
        res.body.should.have.property('replyMessages');
        res.body.should.not.be.eql(errorResponse);
        done();
    })
  }).timeout(0); // just because of it hitting Rasa on cloud (may replace with stub in future), so, disable timeout

  it('Restarting to clean up', restartTest).timeout(0);

});

describe('Test action initiation', () => {
  beforeEach(() => {
    this.post = sinon.stub(rp, 'post');
    this.post.withArgs(mockUserIdOptions).returns(testUserId);
  });

  afterEach(() => {
    this.post = rp.post.restore();
  });

  it('Should respond to a greeting correctly', (done) => {
    const testMessage = assembleTestMessage("Hi");
    logger('Sending greeting to server, initiating action flow');
    chai.request(server)
      .post('/inbound')
      .send(testMessage)
      .end((err, res) => testGreetingOkay(done, res))
  }).timeout(0);

  it("Should provide opening action menu correctly", (done) => {
    const testMessage = assembleTestMessage("3");
    logger('Sending menu selection to server')
    chai.request(server)
      .post('/inbound')
      .send(testMessage)
      .end((err, res) => {
        res.should.have.status(200);
        res.should.not.be.empty;
        res.should.be.json;
        res.body.replyMessages.should.deep.equal(['Welcome to Grassroot Actions. Here you can set meeting, call votes, post livewires, gather group member information (such as addresses and phone numbers), call for action, find volunteers, and validate an action. What would you like to do?']);      
        done();
      })
  }).timeout(0);

  it('Restarting to clean up', restartTest).timeout(0);

})

const testGreetingOkay = ((done, res) => {
  logger('Received server response on greeting: ', res.body);
  res.should.have.status(200);
  res.should.not.be.empty;
  res.should.be.json;
  res.body.should.deep.equal(conversation.openingMsg(testUserId));
  done();
});

const assembleTestMessage = (message) => {
  return {
    "messages": [{
      "from": testPhone,
      "id": "ABGGFlA5FpafAgo6tHcNmNjXmuSf",
      "timestamp": "1518694235",
      "text": {
        "body": message
      },
      "type": "text"
    }]
  }
};
