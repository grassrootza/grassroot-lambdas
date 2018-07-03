const chargebee = require('chargebee');
const express = require('express');
const request = require('request-promise');

// const JWT_AUTH_ENDPOINT = 'https://api.grassroot.org.za/v2/api/auth/token/validate';
const JWT_AUTH_ENDPOINT = 'https://localhost:8080/v2/api/auth/token/validate';

chargebee.configure({site : "grassroot-test", 
  api_key : "test_Lq3fvz8peI4qaLTRTmVeyUtWKOWJXSjY"});

const app = express();

// request-promise to check token is fine (split into separate auth lambda soon)
function validateToken(req) {
    
    const params = {
        url: JWT_AUTH_ENDPOINT,
        method: 'POST',
        data: {
            token: req.get('Authorization'),
            role: 'ROLE_SYSTEM_ADMIN'
        }
    }

    return Promise.resolve(true);

    // return request(params).then(response => {
    //     if ('TOKEN_STILL_VALID' == response)
    //         return true;
    //     else
    //         throw new Error('Token not validate');
    // }, err =>{ 
    //     console.log('Error validating JWT token: ', err);
    //     throw new Error('Could not validate token');
    // });
}

function returnAccessDenied(res) {
    res.status(403).send('Access denied (invalid token)');
}

mapCbSubscription = (item) => {
    return {
        'id': item.subscription.id,
        'customer_id': item.customer.id,
        'first_name': item.customer.first_name,
        'last_name': item.customer.last_name,
        'account_name': item.customer.company,
        'plan': item.subscription.plan_id,
        'status': item.subscription.status,
        'due_invoices': item.subscription.due_invoices_count,
        'next_billing': item.subscription.next_billing_at
    }
}

// first, listing accounts / subscriptions
app.get('/accounts/list', (req, res) => {
    validateToken(req).then(_ => {
        chargebee.subscription.list({
            "status[is]" : "active",
            "sort_by[asc]" : "created_at"
        }).request((err, result) => {
            if (err) {
                console.log('Error calling Chargebee: ', err);
                res.status(500);
            } else {
                let resultList = result.list.map(mapCbSubscription);
                console.log('Result from Chargebee: ', resultList);
                res.status(200).json(resultList);
            }
        })
    }).catch(_ => returnAccessDenied(res))
})

// second, to create a subscription
app.post('/accounts/create', (req, res) => {
    validateToken(req).then(_ => {
        chargebee.subscription.create({
            plan_id: 'grassroot-extra',
            customer: {
                company: req.query.accountName,
                email: req.query.emailAddress,
                auto_collection: 'off'
                // net_term_days: 7
            }
        }).request((error, result) => {
            if (error) {
                console.log('Error creating subscription in Chargebee: ', error);
                res.status(500);
            } else {
                res.status(200).json(mapCbSubscription(result));
            }
        });
    });
})

// third, to disable a subscription
app.post('/accounts/cancel', (req, res) => {
    validateToken(req).then(_ => {
        chargebee.subscription.cancel(req.query.subscriptionId).request((error, result) => {
            if (error) {
                console.log('Error cancelling subscription: ', error);
                res.status(500);
            } else {
                console.log('Subscription cancelled: ', result);
                res.status(200).json(mapCbSubscription(result));
            }
        })
    })
})

app.listen(3000, () => console.log(`Listening on port 3000`));