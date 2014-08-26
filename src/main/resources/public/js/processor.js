$(function () {
  var mapOpts = {
    center: new google.maps.LatLng(37.4440302, -94.2959981),
    zoom: 10
  };
  map = new google.maps.Map($("#map-canvas")[0], mapOpts);
  geocoder = new google.maps.Geocoder();

  ko.applyBindings(new Location());
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
      position: new google.maps.LatLng(loc.coordinates.y, loc.coordinates.x),
      title: loc.name
    });
  }

  self.geocode = function () {
    console.log("geocoding...");
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

      if (myMarker) {
        // this is an update
        myMarker.setMap(null);
      } else {
        // this is a new location
        var newLoc = {
          name: self.name(),
          address: self.address(),
          city: self.city(),
          province: self.state(),
          postalCode: self.zip(),
          coordinates: {
            x: loc.B,
            y: loc.k
          }
        };

        console.log("posting: ", JSON.stringify(newLoc));

        $.ajax("/location", {
          type: "POST",
          contentType: "application/json",
          data: JSON.stringify(newLoc),
          processData: false,
          success: function (data, status, xhr) {
            console.log("success: ", arguments);
            myLocationId = data.id

            window.setTimeout(function () {
              $.getJSON("/location/" + myLocationId + "/nearby", function (data) {
                self.nearby(data);
              });
            }, 5000);
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

