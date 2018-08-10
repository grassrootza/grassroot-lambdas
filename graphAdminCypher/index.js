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

app.use(function(req, res, next) {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
    next();
});

const paramToArray = (req, paramName) => {
    return req.query[paramName] ? JSON.parse(req.query[paramName]) : [];
}

app.get('/document/create/:doc_type', (req, res) => {
    console.log('Creating document, type: ', req.params.doc_type);;

    let params = buildParams(req);

    let final_params_section;
    if (params.doc_type == 'EXTRACT') {
        params.main_text = req.query.main_text;
        final_params_section = 'main_text: $main_text';
    } else {
        params.doc_bucket = req.query.doc_bucket;
        params.doc_key = req.query.doc_key;
        final_params_section = 's3bucket: $doc_bucket, s3key: $doc_key'
    }

    console.log('build params: ', params);

    session.run(
        commonPropertyQuery(final_params_section), params
    ).then(result => {
        console.log('result: ', result);
        res.json(result);
    }).catch(error => {
        console.log('error: ', error);
        res.json(error);
    })
});

const commonPropertyQuery = (final_params_section) => 'CREATE (d: Document {' +
    'machineName: $machine_name, humanName: $human_name, docType: $doc_type, ' +
    'issues: $issues, procedures: $procedures, problems: $problems, ' +
    'stageRelevance: $stage_relevance, ' + final_params_section + 
    '}) return d';

const buildParams = (req) => {
    return {
        machine_name: req.query.machine_name, // note: must be UNIQUE
        human_name: req.query.human_name,
        doc_type: req.params.doc_type, // extract or full

        issues: paramToArray(req, 'issues'), // make sure in JSON on other side, e.g., housing, water, etc.
        procedures: paramToArray(req, 'procedures'), // e.g., rights, actions, contacts
        problems: paramToArray(req, 'problems'), // e.g., land proclamation

        stage_relevance: req.query.stage_relevance, // BEGINNER, INTERMEDIATE, ADVANCED

        s3bucket: req.query.doc_bucket,
        s3key: req.query.doc_key
    };
}

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
        "WHERE d.humanName CONTAINS $query_word OR d.machineName CONTAINS $query_word OR d.stageRelevance CONTAINS $query_word OR " +
        "$query_word IN d.issues OR $query_word IN d.procedures OR $query_word IN d.problems " +
        "RETURN d",
        { query_word: req.query.query_text }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
});

// pagerank queries

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

app.get('/pagerank/write', (req, res) => {
    console.log("Writing raw pagerank to graph");
    return session.run(
        "CALL pagerank.write()"
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
})

app.get('/pagerank/normalize', (req, res) => {
    const entity_type = req.query.entity_type;
    const sub_type = req.query.sub_type;

    console.log("Writing normalized pagerank to graph");
    return session.run(
        "CALL pagerank.normalize()"
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
})

app.get('/pagerank/stats', (req, res) => {
    const entity_type = req.query.entity_type;
    const sub_type = req.query.sub_type;
    const normalized = req.query.normalized;
    const upper_bound = req.query.upper_bound;
    const lower_bound = req.query.lower_bound;

    console.log("Getting pagerank stats");
    return session.run(
        "CALL pagerank.stats($entity_type, $sub_type, toBoolean($normalized), toInteger($upper_bound), toInteger($lower_bound))",
        { entity_type: entity_type, sub_type: sub_type, normalized: normalized, upper_bound: upper_bound, lower_bound: lower_bound }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
})

app.get('/pagerank/scores', (req, res) => {
    const entity_type = req.query.entity_type;
    const sub_type = req.query.sub_type;
    const normalized = req.query.normalized;
    const upper_bound = req.query.upper_bound;
    const lower_bound = req.query.lower_bound;

    console.log("Getting pagerank scores");
    return session.run(
        "CALL pagerank.scores($entity_type, $sub_type, toInteger($upper_bound), toInteger($lower_bound), toBoolean($normalized))",
        { entity_type: entity_type, sub_type: sub_type, upper_bound: upper_bound, lower_bound: lower_bound, normalized: normalized }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
})

app.get('/pagerank/meanEntities', (req, res) => {
    const depth = req.query.depth;
    const entity_type = req.query.entity_type;
    const sub_type = req.query.sub_type;
    const normalized = req.query.normalized;
    const upper_bound = req.query.upper_bound;
    const lower_bound = req.query.lower_bound;

    console.log("Getting mean entities reached");
    return session.run(
        "CALL pagerank.meanEntitiesAtDepth(toInteger($depth), $entity_type, $sub_type, toInteger($upper_bound), toInteger($lower_bound), toBoolean($normalized))",
        { depth: depth, entity_type: entity_type, sub_type: sub_type, upper_bound: upper_bound, lower_bound: lower_bound, normalized: normalized }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
})

app.get('/pagerank/meanRelationships', (req, res) => {
    const depth = req.query.depth;
    const entity_type = req.query.entity_type;
    const sub_type = req.query.sub_type;
    const normalized = req.query.normalized;
    const upper_bound = req.query.upper_bound;
    const lower_bound = req.query.lower_bound;

    console.log("Getting mean relationships reached");
    return session.run(
        "CALL pagerank.meanRelationshipsAtDepth(toInteger($depth), $entity_type, $sub_type, toInteger($upper_bound), toInteger($lower_bound), toBoolean($normalized))",
        { depth: depth, entity_type: entity_type, sub_type: sub_type, upper_bound: upper_bound, lower_bound: lower_bound, normalized: normalized }
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
})

app.listen(3000, () => console.log(`Listening on port 3000`));