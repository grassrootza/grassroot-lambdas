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
    res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
    next();
});

const executeRequest = (query, params, res) => {
    console.log("query: ", query);
    console.log("params: ", params);
    return session.run(
        query, params
    ).then(result => {
        res.json(result);
    }).catch(error => {
        res.json(error);
    })
}

// profiling

app.get('/profile/counts', (req, res) => {
    console.log('Getting entity and relationship counts');
    executeRequest("RETURN profile.counts()", {}, res);
})

app.get('/profile/group/membership', (req, res) => {
    console.log('Getting group membership counts');
    let query = "RETURN profile.groupMemberships(toInteger($first_rank), toInteger($last_rank))";
    let params = { first_rank: req.query.first_rank, last_rank: req.query.last_rank };
    executeRequest(query, params, res);
})

app.get('/profile/user/participation', (req, res) => {
    console.log('Getting user participation counts');
    let query = "RETURN profile.userParticipations(toInteger($first_rank), toInteger($last_rank))";
    let params = { first_rank: req.query.first_rank, last_rank: req.query.last_rank };
    executeRequest(query, params, res);
})

// documents

app.get('/document/create/:doc_type', (req, res) => {
    console.log('Creating document, type: ', req.params.doc_type);

    let params = buildDocumentParams(req);
    params.doc_type.toUpperCase() == 'EXTRACT' ? params.main_text = req.query.main_text : params.doc_link = req.query.doc_link;
    let query = params.doc_type.toUpperCase() == 'EXTRACT' ? createDocumentQuery('main_text') : createDocumentQuery('doc_link');

    executeRequest(query, params, res);
});

app.get('/document/list', (req, res) => {
    console.log("Getting list of all documents");
    executeRequest("MATCH (d:Document) RETURN d", {}, res);
});

app.get('/document/name/available', (req, res) => {
    console.log("Checking availability of document name: ", req.query.machine_name);
    let query = "MATCH (d:Document) WHERE toUpper(d.machineName)=toUpper($machine_name) RETURN COUNT(d)=0";
    let params = { machine_name: req.query.machine_name };
    executeRequest(query, params, res);
});

app.get('/document/query', (req, res) => {
    console.log("Searching for documents with keyword: ", req.query.query_word);
    let query = " MATCH (d:Document)" +
                " WHERE toUpper(d.humanName) CONTAINS $query_word OR" +
                      " toUpper(d.machineName) CONTAINS $query_word OR" +
                      " toUpper(d.stageRelevance) CONTAINS $query_word OR" +
                      " ANY(word IN d.issues WHERE word =~ $query_regex) OR" +
                      " ANY(word IN d.problems WHERE word =~ $query_regex) OR" +
                      " ANY(word IN d.procedures WHERE word =~ $query_regex)" +
                " RETURN d";
    let params = { query_word: req.query.query_word.toUpperCase(), query_regex: '(?i).*' + req.query.query_word + '.*' }
    executeRequest(query, params, res);
});

const createDocumentQuery = (final_param) => 'CREATE (d:Document {' +
    'machineName: $machine_name, humanName: $human_name, docType: $doc_type, ' +
    'issues: $issues, procedures: $procedures, problems: $problems, ' +
    'stageRelevance: $stage_relevance, ' + final_param + ': $' + final_param +
    '}) RETURN d';

const buildDocumentParams = (req) => {
    return {
        machine_name: req.query.machine_name, // note: must be UNIQUE
        human_name: req.query.human_name,
        doc_type: req.params.doc_type, // extract or full

        issues: paramToArray(req, 'issues'), // make sure in JSON on other side, e.g., housing, water, etc.
        procedures: paramToArray(req, 'procedures'), // e.g., rights, actions, contacts
        problems: paramToArray(req, 'problems'), // e.g., land proclamation

        stage_relevance: req.query.stage_relevance // BEGINNER, INTERMEDIATE, ADVANCED
    };
}

const paramToArray = (req, paramName) => {
    return req.query[paramName] ? JSON.parse(req.query[paramName]) : [];
}

// pagerank

app.get('/pagerank/setup', (req, res) => {
    console.log("Setting up pagerank procedures");
    return executeRequest("CALL pagerank.setup()", {}, res);
})

app.get('/pagerank/write', (req, res) => {
    console.log("Writing raw pagerank to graph");
    return executeRequest("CALL pagerank.write()", {}, res);
})

app.get('/pagerank/normalize', (req, res) => {
    console.log("Writing normalized pagerank to graph");
    let query = "CALL pagerank.normalize($entity_type, $sub_type)";
    let params = { entity_type: req.query.entity_type, sub_type: req.query.sub_type };
    return executeRequest(query, params, res);
})

app.get('/pagerank/stats', (req, res) => {
    console.log("Getting pagerank stats");
    wrapPagerankReadRequest(req, res, "stats", false);
})

app.get('/pagerank/scores', (req, res) => {
    console.log("Getting pagerank scores");
    wrapPagerankReadRequest(req, res, "scores", false);
})

app.get('/pagerank/tiers', (req, res) => {
    console.log("Getting pagerank scores");
    return executeRequest("RETURN pagerank.tiers()", {}, res);
})

app.get('/pagerank/meanEntities', (req, res) => {
    console.log("Getting mean entities reached");
    wrapPagerankReadRequest(req, res, "meanEntities", false);
})

app.get('/pagerank/meanRelationships', (req, res) => {
    console.log("Getting mean relationships reached");
    wrapPagerankReadRequest(req, res, "meanRelationships", false);
})

app.get('/pagerank/compareMetrics', (req, res) => {
    console.log('Comparing top 100 users by pagerank and closeness');
    let query = "RETURN pagerank.compareMetrics($to_compare, toInteger($depth))";
    let params = { to_compare: req.query.to_compare, depth: req.query.depth };
    executeRequest(query, params, res);
})

const wrapPagerankReadRequest = (req, res, extension, procedure) => {
    console.log("Building request with procedure: ", procedure);

    let normParam = procedureNeedsNorm(extension) ? ", toBoolean($normalized)" : "";
    let depthParam = procedureNeedsDepth(extension) ? ", toInteger($depth)" : "";
    let query = (procedure ? "CALL" : "RETURN") + " pagerank." + extension + "($entity_type, $sub_type, " +
                "toInteger($first_rank), toInteger($last_rank)" + normParam + depthParam + ")";

    let params = buildPagerankParams(req);
    if (procedureNeedsDepth(extension)) params.depth = req.query.depth;
    if (procedureNeedsNorm(extension)) params.normalized = req.query.normalized;

    executeRequest(query, params, res);
}

const buildPagerankParams = (req) => {
    return {
        entity_type: req.query.entity_type,
        sub_type: req.query.sub_type,
        first_rank: req.query.first_rank,
        last_rank: req.query.last_rank,
    };
}

const procedureNeedsNorm = (procedure) => {
    return procedure == "stats" || procedure == "scores";
}

const procedureNeedsDepth = (procedure) => {
    return procedure == "meanEntities" || procedure == "meanRelationships";
}

app.listen(3000, () => console.log(`Listening on port 3000`));