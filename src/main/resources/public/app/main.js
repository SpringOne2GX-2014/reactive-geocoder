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

  self.locationId = ko.observable();
  self.name = ko.observable();
  self.address = ko.observable();
  self.city = ko.observable();
  self.state = ko.observable();
  self.zip = ko.observable();
  self.lat = ko.observable();
  self.lon = ko.observable();
  self.nearby = ko.observableArray();
  self.nearbyMarkers = ko.observableArray();
  self.distance = ko.observable(20);
  self.active = ko.observable('form');
  self.websocket = ko.observable();
  self.connecting = ko.observable();

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

  self.formActive = function () {
    self.active('form');
  }

  self.nearbyActive = function () {
    self.active('nearby');
    self.liveUpdates();
  }

  self.clearMarkers = function () {
    var m;
    for (var i in (m = self.nearbyMarkers())) {
      m[i].setMap(null);
    }
  }

  self.liveUpdates = function () {
    if (!self.locationId()) {
      return;
    }

    //var wsUrl = "wss://geocoder.cfapps.io:4443/location/" + self.locationId() + "/nearby?distance=" + self.distance();
    var wsUrl = "ws://localhost:5050/location/" + self.locationId() + "/nearby?distance=" + self.distance();
    console.log("opening WebSocket connection to ", wsUrl);
    var ws;
    if ((ws = self.websocket())) {
      ws.close();
    }

    ws = new WebSocket(wsUrl);
    ws.onopen = function () {
      console.log("connected...");
      self.clearMarkers();
      self.nearby([]);
      self.active('nearby');
    }
    ws.onclose = function () {
      console.log("closed");
    }
    ws.onerror = function () {
      console.log("error", arguments);
    }
    ws.onmessage = function (msg) {
      var l = JSON.parse(msg.data);
      console.log("got nearby: ", l);
      self.nearby.push(l);

      var marker = new google.maps.Marker({
        map: map,
        position: new google.maps.LatLng(l.coordinates[1], l.coordinates[0]),
        title: l.name
      });
      self.nearbyMarkers.push(marker);
    }

    self.websocket(ws);
    self.connecting(0);
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

      if (self.locationId()) {
        console.log("updating...");
        // this is an update
        if (myMarker) {
          myMarker.setMap(null);
        }

        client({
          path: url + "/" + self.locationId(),
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json'
          },
          entity: newLoc
        }).then(function (res) {
          console.log("PUT success: ", res);
        });
      } else {
        console.log("creating...");
        client({
          path: url,
          headers: {
            'Content-Type': 'application/json'
          },
          entity: newLoc
        }).then(function (res) {
          console.log("POST success: ", res);
          self.locationId(res.entity.id);
          self.liveUpdates();
        });

        // set new location marker
        myMarker = new google.maps.Marker({
          map: map,
          position: loc,
          title: self.name()
        });
      }
    });
  };
};

module.exports = function () {
  map = new google.maps.Map(document.getElementById("map-canvas"), mapOpts);
  geocoder = new google.maps.Geocoder();

  myMarker = new google.maps.Marker({
    map: map,
    position: homeLoc,
    title: "You Are Here"
  });

  var loc = new Location();
  ko.applyBindings(loc);
  $(document).foundation({
    slider: {
      on_change: function () {
        var val = $("[data-slider]").attr("data-slider");
        loc.distance(val);
        if (loc.connecting()) {
          window.clearTimeout(loc.connecting());
        }
        loc.connecting(window.setTimeout(loc.liveUpdates, 1000));
      }
    }
  });
};