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

const paramToArray = (req, paramName) => {
    return req.query[paramName] ? JSON.parse(req.query.paramName) : [];
}

app.get('/document/create/extract', (req, res) => {

    console.log('Creating extract document ...');

    const docType = req.query.documentType; // extract or full
    const issues = paramToArray(req, 'issues'); // make sure in JSON on other side, e.g., housing, water, etc.
    const procedures = paramToArray(req, 'procedures'); // e.g., rights, actions, contacts
    const problems = paramToArray(req, 'problems'); // e.g., land proclamation

    const stage_relevance = req.query.stageRelevance; // BEGINNER, INTERMEDIATE, ADVANCED
    
    const human_name = req.query.description;
    const machine_name = req.query.name; // note: must be UNIQUE 

    const mainText = req.query.mainText;

    session.run(
        'CREATE (d: Document {' + 
            'name: $machine_name, description: $human_name, doc_type: $docType' + 
        '}) return d',
        { machine_name: machine_name, human_name: human_name, doc_type: docType }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })

});

app.get('/document/name/available', (req, res) => {
    return session.run(
        'MATCH ()',
        { machine_name: req.query.machine_name }
    ).then(result => {

    }).catch(error => {
        res.json(error);
    })
});

app.listen(3000, () => console.log(`Listening on port 3000`));
