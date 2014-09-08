var rest = require("rest");
var rest = require("rest");
var mime = require("rest/interceptor/mime");
var ko = require("knockout");

var map, geocoder, myMarker, ws;

var client = rest.wrap(mime);

var homeLoc = new google.maps.LatLng(32.774875, -96.80415);
var mapOpts = {
  center: homeLoc,
  zoom: 15
};

/**
 * Location domain object for use in binding to forms reactively.
 *
 * @param name name of this Location
 * @param address street address of this Location
 * @param city city of this Location
 * @param state state of this Location
 * @param zip zipcode of this Location
 * @constructor
 */
var Location = function () {
  var self = this;

  self.name = ko.observable();
  self.address = ko.observable();
  self.city = ko.observable();
  self.state = ko.observable();
  self.zip = ko.observable();
  self.lat = ko.observable();
  self.lon = ko.observable();
  self.nearby = ko.observableArray();
  self.distance = ko.observable(20);

  self.addressCompact = ko.pureComputed(function () {
    var addr = "";
    if (self.address()) {
      addr += self.address() + ", ";
    }
    if (self.city()) {
      addr += self.city() + ", ";
    }
    if (self.state()) {
      addr += self.state() + " ";
    }
    if (self.zip()) {
      addr += self.zip();
    }
    return addr;
  });

  self.setMarker = function (loc, ev) {
    var marker = new google.maps.Marker({
      map: map,
      position: new google.maps.LatLng(loc.coordinates[1], loc.coordinates[0]),
      title: loc.name
    });
  }

  self.geocode = function () {
    console.log("geocoding ", self.addressCompact());

    geocoder.geocode({
      address: self.addressCompact()
    }, function (results, status) {
      if (status != google.maps.GeocoderStatus.OK) {
        console.log("error geocoding: ", status, results);
        return;
      }

      // get result
      var loc = results[0].geometry.location;
      map.setCenter(loc);

      self.lat(loc.k);
      self.lon(loc.B);

      var newLoc = {
        name: self.name(),
        address: self.address(),
        city: self.city(),
        province: self.state(),
        postalCode: self.zip(),
        coordinates: [loc.B, loc.k]
      };
      var url = "/location";

      if (myMarker) {
        // this is an update
        myMarker.setMap(null);

        client({
          path: url + "/" + myLocationId + "?distance=" + self.distance(),
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json'
          },
          entity: newLoc
        })
          .then(function (res) {
            console.log("PUT success: ", res);
          });
      } else {
        client({
          path: url,
          headers: {
            'Content-Type': 'application/json'
          },
          entity: newLoc
        })
          .then(function (res) {
            console.log("POST success: ", res);
            myLocationId = res.entity.id

            var wsUrl = "ws://localhost:5050/location/" + myLocationId + "/nearby";
            //var wsUrl = "wss://dsyerprocessor.cfapps.io:4443/location/" + myLocationId + "/nearby";
            window.setTimeout(function () {
              var ws = new WebSocket(wsUrl);
              ws.onopen = function () {
                console.log("connected...");
                self.nearby([]);
              }
              ws.onclose = function () {
                console.log("closed");
              }
              ws.onerror = function () {
                console.log("error", arguments);
              }
              ws.onmessage = function (msg) {
                console.log("got nearby: ", msg.data);
                self.nearby.push(JSON.parse(msg.data));
              }
            }, 500);
          });
      }
    });
  };
};

module.exports = function () {
  map = new google.maps.Map(document.getElementById("map-canvas"), mapOpts);
  geocoder = new google.maps.Geocoder();

  var marker = new google.maps.Marker({
    map: map,
    position: homeLoc,
    title: "You Are Here"
  });

  var loc = new Location();
  ko.applyBindings(loc);
};