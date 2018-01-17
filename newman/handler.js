const newman = require('newman'); // require newman in your project
const AWS = require('aws-sdk');

AWS.config.region = process.env['AWS_DEFAULT_REGION'];

function test() {
    var collectionId = '110376-cd628c63-58cb-51d8-b983-4ac1e44e94c5';
    var apiKey = '***REMOVED***';
    var environmentId = '110376-6c3fa672-673a-6cfa-59b5-4a3f2705119b';

    postmanRun(collectionId, apiKey, environmentId, function(err, summary) {
        const runSummary = {
            collection: summary.collection,
            run: {
                stats: summary.run.stats,
                timings: summary.run.timings,
                transfers: summary.run.transfers,
                failures: summary.run.failures,
                error: summary.run.error
            }
        };
        
        const response = {
            statusCode: 200,
            body: JSON.stringify(runSummary),
        };
        console.log("Generated response", runSummary)
    });
}

test();

module.exports.postmanCollectionRunner = (event, context, callback) => {
    console.log("Got event: ", event)
    var apiKey = process.env['POSTMAN_API_KEY'];
    var collectionId;
    var environmentId;
    
    if (typeof process.env['POSTMAN_COLLECTION'] !== 'undefined') {
        collectionId = process.env['POSTMAN_COLLECTION'];
        environmentId = process.env['POSTMAN_ENVIRONMENT'];
    }

    if (typeof event.queryStringParameters !== 'undefined') {
        collectionId = event.queryStringParameters.collection
        environmentId = event.queryStringParameters.environment;
    }

    if (typeof event.collection !== 'undefined' && typeof event.environment !== 'undefined') {
        collectionId = event.collection
        environmentId = event.environment;
    }

    if (!collectionId || !environmentId) {
        console.error("Collection run parameters missing");
        context.done("Collection run parameters missing", null);
    } else {
        postmanRun(collectionId, apiKey, environmentId, function(err, summary) {
            const runSummary = {
                collection: summary.collection,
                run: {
                    stats: summary.run.stats,
                    timings: summary.run.timings,
                    transfers: summary.run.transfers,
                    failures: summary.run.failures,
                    error: summary.run.error
                }
            };
            publishSns(context, runSummary);
        });
    }

}

function publishSns(context, content) {
    var sns = new AWS.SNS();

    sns.publish({
        Message: JSON.stringify(content),
        TopicArn: process.env['SNS_TOPIC']
    }, function(err, publishResponse) {
        if (err) {
            console.log(err.stack);
            return;
        }
        console.log('push sent', publishResponse);

        const response = {
            statusCode: 200,
            body: JSON.stringify(content),
        };
        context.done(null, response);
    });
}

function postmanRun(collectionId, apiKey, environmentId, callback) {
    newman.run({
        collection: `https://api.getpostman.com/collections/${collectionId}?apikey=${apiKey}`,
        environment: `https://api.getpostman.com/environments/${environmentId}?apikey=${apiKey}`
    }, callback)
.on('start', function (err, args) { // on start of run, log to console
    var date = Math.floor(new Date() / 1000);
    console.log(`[START]     [${Date.now()}] [${args.cursor.position}/${args.cursor.length}] [${args}]`);
})
.on('beforeRequest', function (err, o) {
    if (err) { return; }
    console.log(`[REQUEST]   [${Date.now()}] [${o.cursor.position}/${o.cursor.length}] [${o.item.name}] ${o.request.method} ${o.request.url}`);
})
.on('console', function (err, o) {
    if (err) { return; }
    // we first merge all messages to a string. while merging we run the values to util.inspect to colour code the
    // messages based on data type
    message = o.messages.reduce(function (log, message) { // wrap the whole message to the window size
        return (log += (log ? ', ' : '') + message);
    }, "");

    console.log(`[CONSOLE] ${message.replace(/\n\s*\n/g, '\n[CONSOLE]')}`)
})
.on('request', function (err, o) {
    if (err) { return; }

    var size = o.response && o.response.size();
    size = size && (size.header || 0) + (size.body || 0) || 0;

    if (err) {
        console.error('[ERROR]');
    } else {
        console.log(`[RESPONSE]  [${Date.now()}] [${o.cursor.position}/${o.cursor.length}] [${o.item.name}] [${o.response.code}] ${o.response.reason()} [${size}]B in [${o.response.responseTime}]ms`);
    }
})
.on('assertion', function (err, o) {
    var passed = !err;

    // handle skipped test display
    if (o.skipped) {
        console.warn(`[ASSERTION] [SKIPPED] ${o.assertion}`);
        return;
    }

    if (passed) {
        console.log(`[ASSERTION] [${Date.now()}] [${o.cursor.position}/${o.cursor.length}] [${o.item.name}] [OK] ${o.assertion}`);
    } else {
        console.error(`[ASSERTION] [${Date.now()}] [${o.cursor.position}/${o.cursor.length}] [${o.item.name}] [FAIL] ${o.assertion}`);
    }
})
.on('done', function (err, summary) {
    if (typeof err !== 'undefined' && err) { 
        console.error('collection run encountered an error.', err);
    }
    
    if (summary) {
        var stats = summary.run.stats
        console.log(`[COMPLETED] [${summary.run.timings.started}] - [${(Math.floor(new Date()))}] AverageResponseTime [${summary.run.timings.responseAverage}]ms : ITEMS [${stats.items.failed}] + [${stats.items.pending}] of [${stats.items.total}] : ASSERTIONS [${stats.assertions.failed}] of [${stats.assertions.total}]`);
    }
    
});
}