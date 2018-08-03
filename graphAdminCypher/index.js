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

app.get('/document/create', (req, res) => {
    console.log('Creating document, type: ', req.query.doc_type);;

    const machine_name = req.query.machine_name; // note: must be UNIQUE
    const human_name = req.query.human_name;
    const doc_type = req.query.doc_type; // extract or full

    const issues = paramToArray(req, 'issues'); // make sure in JSON on other side, e.g., housing, water, etc.
    const procedures = paramToArray(req, 'procedures'); // e.g., rights, actions, contacts
    const problems = paramToArray(req, 'problems'); // e.g., land proclamation

    const stage_relevance = req.query.stage_relevance; // BEGINNER, INTERMEDIATE, ADVANCED
    const text_or_link = req.query.text_or_link; // extract text or full doc s3 link

    session.run(
        'CREATE (d: Document {' +
            'machineName: $machine_name, humanName: $human_name, docType: $doc_type, ' +
            'issues: $issues, procedures: $procedures, problems: $problems, ' +
            'stageRelevance: $stage_relevance, textOrLink: $text_or_link' +
        '}) return d',
        { machine_name: machine_name, human_name: human_name, doc_type: doc_type,
          issues: issues, procedures: procedures, problems: problems,
          stage_relevance: stage_relevance, text_or_link: text_or_link }
    ).then(result => {
        console.log('result: ', result);
        res.json(result);
    }).catch(error => {
        console.log('error: ', error);
        res.json(error);
    })
});

app.get('/document/name/available', (req, res) => {
    console.log("Checking availability of name: ", req.query.machine_name);
    return session.run(
        'MATCH (d: Document) WHERE d.machineName=$machine_name RETURN COUNT(d)=0',
        { machine_name: req.query.machine_name }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
});

app.get('/document/query', (req, res) => {
    console.log("Querying documents with keyword: ", req.query.query_word);
    return session.run(
        "MATCH (d:Document) " +
        "WHERE d.humanName=$query_word OR d.machineName CONTAINS $query_word OR d.stageRelevance CONTAINS $query_word OR " +
        "$query_word IN d.issues OR $query_word IN d.procedures OR $query_word IN d.problems " +
        "RETURN d",
        { query_word: req.query.query_word }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
});

app.get('/document/list', (req, res) => {
    console.log("Getting list of all docs");
    return session.run(
        "MATCH (d:Document) RETURN d"
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
});

app.listen(3000, () => console.log(`Listening on port 3000`));
