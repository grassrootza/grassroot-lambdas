var express = require('express');
const neo4j = require('neo4j-driver').v1;

// const user = process.env.NEO4J_USER;
// const password = process.env.NEO4J_PASS;
// const uri = process.env.NEO4J_URI;

const user = 'grassroot';
const password = 'longpassword';
const uri = 'bolt://localhost';

const driver = neo4j.driver(uri, neo4j.auth.basic(user, password));
const session = driver.session();

const app = express();

const ensureActorExists = (type, platformUid) => 
  session.run(
    'MERGE (a: Actor { actorType: $type, platformUid: $platformUid }) RETURN a',
    {type: type, platformUid: platformUid}
  );

const ensureEventExists = (type, platformId) => 
  session.run(
    'MERGE (e: Event { eventType: $type, platformUid: $platformUid }) RETURN e',
    { type: type, platformUid: platformId }
  );

const addGenRelationship = (headPlatformId, tailPlatformId) => 
  session.run(
    'MATCH (a: Actor), (b: Actor) WHERE a.platformUid = $headId AND b.platformUid = $tailId ' +
    'CREATE (a)-[r:GENERATOR]->(b) RETURN r',
    { headId: headPlatformId, tailId: tailPlatformId }
  );

const addActorInMovement = (actorPlatformId, movementPlatformId) => 
  session.run(
    'MATCH (a: Actor), (m: Actor) WHERE a.platformUid = $actorId AND m.platformUid = $movementId ' +
    'CREATE (a)-[r:PARTICIPATES]->(m) RETURN r',
    { actorId: actorPlatformId, movementId: movementPlatformId }
  );

const addEventInMovement = (eventPlatformId, movementPlatformId) =>
  session.run(
    'MATCH (e: Event), (m: Actor) WHERE e.platformUid = $eventId AND m.platformUid = $movementId ' +
    'CREATE (e)-[r:PARTICIPATES]->(m) RETURN r',
    { eventId: eventPlatformId, movementId: movementPlatformId }
  );

const removeFromMovement = (entityPlatformId, movementPlatformId) =>
  session.run(
    'MATCH (a { platformUid: $headId })-[r:PARTICIPATES]->(m { platformUid: $movementId }) DELETE r',
    { headId: entityPlatformId, movementId: movementPlatformId }
  );

app.get('/create/:creatorPlatformId/:movementPlatformId', (req, res) => {
    ensureActorExists('INDIVIDUAL', req.params.creatorPlatformId)
      .then(() => ensureActorExists('MOVEMENT', req.params.movementPlatformId)
        .then(() => addGenRelationship(req.params.creatorPlatformId, req.params.movementPlatformId)
          .then(result => {
            const singleRecord = result.records[0];
            const node = singleRecord.get(0);
            resultAndCleanUp(res, node);          
        })))
});

app.get('/get/:platformId', (req, res) => {
  session.run(
    'MATCH (a: Actor { actorType: \'MOVEMENT\', platformUid: $platformUid })-[r]-(b) RETURN a, b, r',
    { platformUid: req.params.platformId }
  ).then(result => resultAndCleanUp(res, result.records));
});

app.get('/add/actor/:actorType/:actorId/:movementId', (req, res) => {
  ensureActorExists(req.params.actorType, req.params.actorId)
    .then(() => addActorInMovement(req.params.actorId, req.params.movementId)
      .then(result => {
        resultAndCleanUp(res, result);
      }));
});

app.get('/add/event/:eventType/:eventId/:movementId', (req, res) => {
  ensureEventExists(req.params.eventType, req.params.eventId)
    .then(() => addEventInMovement(req.params.eventId, req.params.movementId)
      .then(result => {
        resultAndCleanUp(res, result);
      }));
})

app.get('/remove/:entityId/:movementId', (req, res) => {
  removeFromMovement(req.params.entityId, req.params.movementId).then(result => resultAndCleanUp(res, result));
});

const resultAndCleanUp = (res, resultToPass) => {
  session.close();
        
  res.json(resultToPass);

  // on application exit:
  driver.close();
}

app.listen(3000, () => console.log(`Listening on port 3000`));
