var express = require('express');

const GOOGLE_MAPS_API_KEY = process.env.GOOGLE_MAPS_API_KEY;

const mapsClient = require('@google/maps').createClient({
    key: GOOGLE_MAPS_API_KEY,
    Promise: Promise
});

const app = express();

app.get('/province', (req, res) => {
	console.log('echoing back: ', req.query.province);
	res.status(200).send(req.query.province);
});

app.get('/lookup', (req, res) => {
    const province = req.query.province;

    const searchTerm = req.query.searchTerm + (!!province ? (', ' + province) : '');
    console.log(`calling autocomplete for search term: ${searchTerm}, with province: ${province}`);

    const query = {
        input: searchTerm,
        types: '(regions)',
        components: { country: 'za' }
    }

    console.log('query: ', query);

    mapsClient.placesAutoComplete(query).asPromise()
        .then(response => {
            console.log('google response: ', response);
            let possibilities = response['json']['predictions'];
            let mappedPossibilties = possibilities.map(mapPossibility);
            // console.log('mapped possibilities: ', mappedPossibilties);
            res.status(200).json(mappedPossibilties);
        })
        .catch(err => {
            res.status(500).json(err);
        })
});

app.get('/postal/:code', (req, res) => {
    const postalCode = req.params.code;
    console.log(`postal code: ${postalCode}`);

    mapsClient.geocode({components: {
        country: 'ZA',
        postal_code: postalCode
    }}).asPromise()
        .then(response => {
            console.log('geocode response: ', response);
            res.status(200).json(response);
        })
        .catch(err => {
            res.status(500).json(err);
        })
});

function mapPossibility(possibility) {
    return {
        description: possibility['description'].replace(', South Africa', ''),
        place_id: possibility['place_id'],
        lowest_type: possibility['types'][0]
    }
}

app.get('/details/:placeId', (req, res) => {
    const placeId = req.params.placeId;
    mapsClient.place({
        placeid: placeId
    })
    .asPromise()
    .then(response => {
        console.log('response from maps: ', response);
        const transformed = mapPlace(response.json.result);
        console.log('transformed: ', transformed);
        res.status(200).json(transformed);
    })
    .catch(err => res.status(500).json(err));
})

function mapPlace(place) {
    console.log("received place: ", place);
    const mappedPlace = {
        place_id: place.place_id,
        description: place.formatted_address,
        lowest_type: place.address_components[0].types[0],
        longitude: place.geometry.location.lng,
        latitude: place.geometry.location.lat
    }
    console.log('mapped to place: ', mappedPlace);

    let postalCode = place.address_components.find(component => component.types[0] == 'postal_code');
    if (postalCode && postalCode.short_name)
        mappedPlace['postal_code'] = postalCode.short_name;

    let townDetails = place.address_components.find(component => component.types[0] == 'locality');
    console.log('found a town? : ', townDetails);
    if (townDetails && townDetails.long_name)
        mappedPlace['town_name'] = townDetails.long_name;

    let provinceDetails = place.address_components.find(component => component.types[0] == 'administrative_area_level_1');
    console.log('province found = ', provinceDetails);
    if (provinceDetails && provinceDetails.short_name)
        mappedPlace['province'] = 'ZA_' + provinceDetails.short_name;

    // console.log(`provinceIndex= ${provinceIndex} and mapped place = ${mappedPlace['province']}`);
    return mappedPlace;
}

app.listen(3000, () => console.log(`Listening on port 3000`));

// module.exports = app;
