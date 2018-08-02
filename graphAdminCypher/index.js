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
    return req.query[paramName] ? JSON.parse(req.query[paramName]) : [];
}

app.get('/document/create/extract', (req, res) => {

    console.log('Creating extract document ...');

    const machine_name = req.query.machine_name; // note: must be UNIQUE
    const human_name = req.query.human_name;

    const doc_type = req.query.doc_type; // extract or full
    const issues = paramToArray(req, 'issues'); // make sure in JSON on other side, e.g., housing, water, etc.
    const procedures = paramToArray(req, 'procedures'); // e.g., rights, actions, contacts
    const problems = paramToArray(req, 'problems'); // e.g., land proclamation

    const stage_relevance = req.query.stage_relevance; // BEGINNER, INTERMEDIATE, ADVANCED

    const main_text = req.query.main_text;

    session.run(
        'CREATE (d: Document {' + 
            'machineName: $machine_name, humanName: $human_name, docType: $doc_type, ' +
            'issues: $issues, procedures: $procedures, problems: $problems, ' +
            'stageRelevance: $stage_relevance, mainText: $main_text' +
        '}) return d',
        { machine_name: machine_name, human_name: human_name, doc_type: doc_type,
          issues: issues, procedures: procedures, problems: problems,
          stage_relevance: stage_relevance, main_text: main_text }
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
