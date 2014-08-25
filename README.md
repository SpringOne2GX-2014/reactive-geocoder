# Reactive SpringOne2GX Demo

You need MongoDB running to run this demo. It's a plain Spring Boot app, so run it with Maven:

		$ mvn spring-boot:run

You need to create a new resource to get the proper event listeners set up, which are done in the `POST` handler:

		$ curl -v -XPOST http://localhost:5050/location

You'll get back a 303 with a `Location` header pointing to the REST URL to manipulate the resource.

		> POST /location HTTP/1.1
		> User-Agent: curl/7.30.0
		> Host: localhost:5050
		> Accept: */*
		>
		< HTTP/1.1 303 See Other
		< Location: http://localhost:5050/location/53fb64d03004e4a8dcd9eaad
		< Content-Length: 0
		< Connection: keep-alive

Use that URL to `GET` and `PUT` data to update the `Location` in MongoDB:

		$ curl -v -XPUT -H "Content-Type: application/json" -d '{"coordinates":{"x":1.0,"y":1.0}}' http://localhost:5050/location/53fb64d03004e4a8dcd9eaad

Events are published whenever a save happens in Spring Data. That event is propagated out to Reactor and through the various `Consumer`s attached to the `Reactor` whenever a resource is created using `POST`.

