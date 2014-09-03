# Reactive SpringOne2GX Demo

You need MongoDB running to run this demo. It's a plain Spring Boot app, so run it with Maven:

		$ mvn spring-boot:run

This demo provides an input form into which you can input your address (or even just your city and state). Click the "Geocode" button and the app geocodes that address into a Lat/Long coordinate pair using Google Maps' API. It then saves this information to the server which uses a Spring Data Repository to store the address and coordinates in MongoDB.

Upon save, your Location is pumped into a shared `Stream<Location>` that has a filter applied to it that will only process other `Location` domain instances that fall within a given geographic radius (by default 10km) from your coordinates. As other users are simultaneously filling out their own address or home city and saving that to the server, their information is incorporated into your Stream and you will see the name and city of other users who live near your coordinates in the list. You can add a Marker to the map to see where that person lives specifically.

To access the demo, open `http://localhost:5050/` (or the equivalent CloudFoundry address if deployed publicly) in your browser.

Follow these steps to see data:

- Enter a Name and some parts of an address (doesn't have to be exact as City and State/Country is fine)
- Click "Save"
- See any "Nearby" entries in the table below
- Reload page in browser and enter another Name + City within 10km and click "Save"
- You should see the other person you entered previously
- Move slider to higher distance value and click "Save"
- You should see people that are farther away, assuming data exists at further distances