# frisby-web

A lightweight, production-ready Java library for building HTTP clients and embedded HTTP
servers.  No mandatory framework dependencies — bring your own serializer, wire it to your
own metrics backend, and add only the modules you actually use.

---

## Modules

| Artifact                 | Purpose                                                                     |
|--------------------------|-----------------------------------------------------------------------------|
| `serial`                 | `JsonSerializer` and `GenericType` interfaces — shared by client and server |
| `client`                 | HTTP client built on JDK `java.net.http.HttpClient`                         |
| `basic-security`         | Client-side HTTP Basic Auth and Bearer Token providers                      |
| `oauth2-security`        | Client-side OAuth 2.0 client-credentials provider                           |
| `server`                 | Embedded HTTP server (Jersey 3.x + Jetty 12)                                |
| `server-basic-security`  | Server-side Basic Auth authentication                                       |
| `server-oauth2-security` | Server-side Bearer Token authentication                                     |
| `jackson-serializer`     | Jackson-backed `JsonSerializer` with sensible defaults                      |

---

## Requirements

- Java 17+
- Maven 3.6.3+

---

## Maven Setup

Import the BOM in your `<dependencyManagement>` block, then declare only the modules you need
without specifying versions:

```xml
<!-- dependencyManagement -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>bom</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

```xml
<!-- HTTP Client -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>client</artifactId>
</dependency>

<!-- Client Basic Auth / Bearer Token authentication -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>basic-security</artifactId>
</dependency>

<!-- Client OAuth 2.0 client-credentials authentication -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>oauth2-security</artifactId>
</dependency>

<!-- Embedded Server -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server</artifactId>
</dependency>

<!-- Server Basic Auth / Bearer Token authentication -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-basic-security</artifactId>
</dependency>

<!-- Server OAuth 2.0 Bearer Token authentication -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-oauth2-security</artifactId>
</dependency>

<!-- Jackson serializer — usable with both client and server -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>jackson-serializer</artifactId>
</dependency>
```

---

## Quick Start — HTTP Client

```java
import software.frisby.web.client.Client;
import software.frisby.web.client.Configuration;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.jackson.JacksonSerializer;

// One client instance per target service — reuse for the lifetime of the application
Client client = Client.builder()
        .configuration(c -> c
                .uri(URI.create("https://api.example.com"))
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .serializer(JacksonSerializer.builder().build()))
        .build();

// GET — typed response
User user = client.get()
        .path("/users/{id}", "id", userId)
        .send(User.class)
        .body();

// GET — generic collection
List<Order> orders = client.get()
        .path("/orders")
        .send(new GenericType<List<Order>>() {})
        .body();

// POST — JSON body, typed response
Order order = client.post()
        .path("/orders")
        .body(new CreateOrderRequest(userId, items))
        .send(Order.class)
        .body();

// DELETE — no response body
client.delete()
        .path("/orders/{id}", "id", orderId)
        .send();
```

See the [full client guide](docs/client.md) for compression, authentication, async requests,
logging, multipart upload, and more.

---

## Quick Start — HTTP Server

```java
import software.frisby.web.server.Server;
import software.frisby.web.serial.jackson.JacksonSerializer;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/orders")
public class OrderResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Order> list() {
        return orderService.findAll();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(CreateOrderRequest request) {
        Order order = orderService.create(request);
        return Response.status(201).entity(order).build();
    }
}

Server server = Server.builder()
        .configuration(c -> c
                .port(8080)
                .serializer(JacksonSerializer.builder().build()))
        .resources(new OrderResource(orderService))
        .healthCheck()
        .build();

server.start();
Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
```

See the [full server guide](docs/server.md) for TLS, HTTP/2, CORS, authentication, virtual
threads, graceful shutdown, structured logging, and more.

---

## Documentation

| Guide                                | Contents                                                                                       |
|--------------------------------------|------------------------------------------------------------------------------------------------|
| [HTTP Client](docs/client.md)        | `Client`, `Configuration`, verb specs, compression, auth, logging, events, exception hierarchy |
| [HTTP Server](docs/server.md)        | `Server`, `ServerConfiguration`, concurrency, TLS, HTTP/2, CORS, auth, logging, observability  |
| [Architecture](docs/architecture.md) | Design decisions, module structure, extension points                                           |

---

## Building

```bash
mvn verify          # compile + unit tests + JaCoCo coverage report
mvn clean verify    # full clean build
mvn install         # install all modules to local Maven repository
```

---

## License

[MIT](LICENSE)
