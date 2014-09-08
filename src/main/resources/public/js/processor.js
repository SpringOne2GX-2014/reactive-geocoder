$(function () {
  var mapOpts = {
    center: new google.maps.LatLng(32.774875, -96.80415),
    zoom: 10
  };
  map = new google.maps.Map($("#map-canvas")[0], mapOpts);
  geocoder = new google.maps.Geocoder();

  var loc = new Location();

  $("#distance-slider").slider({
    step: 10,
    min: 10,
    max: 100,
    slide: function (ev, ui) {
      loc.distance(ui.value);
    }
  });
  ko.applyBindings(loc);
});

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
  self.distance = ko.observable(10);

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
    geocoder.geocode({ address: self.addressCompact() }, function (results, status) {
      if (status != google.maps.GeocoderStatus.OK) {
        console.log("error geocoding: ", status, results);
        return;
      }

      // get result
      var loc = results[0].geometry.location;
      map.setCenter(loc);

      self.lat(loc.k);
      self.lon(loc.B);

      console.log("self: ", ko.toJSON(self));
      console.log("location: ", JSON.stringify(loc));

      var newLoc = {
        name: self.name(),
        address: self.address(),
        city: self.city(),
        province: self.state(),
        postalCode: self.zip(),
        coordinates: [loc.B, loc.k]
      };

      if (myMarker) {
        // this is an update
        myMarker.setMap(null);

        $.ajax("/location/" + myLocationId + "?distance=" + self.distance(), {
          type: "PUT",
          contentType: "application/json",
          data: JSON.stringify(newLoc),
          processData: false,
          success: function (data, status, xhr) {
            console.log("PUT success: ", arguments);
          }
        });
      } else {
        console.log("posting: ", JSON.stringify(newLoc));

        $.ajax("/location", {
          type: "POST",
          contentType: "application/json",
          data: JSON.stringify(newLoc),
          processData: false,
          success: function (data, status, xhr) {
            console.log("POST success: ", arguments);
            myLocationId = data.id

            var wsUrl = "ws://localhost:5050/location/" + myLocationId + "/nearby";
            var wsFactory = function () {
              var fn = this;
              var retry = function () {
                window.setTimeout(fn, 1000);
              }

              var ws = new WebSocket(wsUrl);
              ws.onopen = function () {
                self.nearby([]);
              }
              ws.onclose = retry;
              ws.onerror = retry;
              ws.onmessage = function (msg) {
                console.log("got nearby: ", msg.data);
                self.nearby.push(JSON.parse(msg.data));
              }
            }
            var ws = wsFactory();
          }
        });
      }

      // set new location marker
      myMarker = new google.maps.Marker({
        map: map,
        position: loc,
        title: self.name()
      });
    });
  };
};

