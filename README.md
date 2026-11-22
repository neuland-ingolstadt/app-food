# app-food

Campus food microservice for Neuland — menus from Mensa, Reimanns and Canisius, normalized and cached for the public API.

## Authorship

**This entire project was designed and written by Robert Eggl (`@roberteggl`).**

It is not a fork, rewrite attribution, or continuation of any other repository’s authorship.
All application code, architecture and tooling choices in this repository are his work.

## What it does

- REST API: `GET /meals`, `GET /meals/{mealId}` (OpenAPI: `backend-food-api`)
- Live Mensa XML from max-manager.de (Ingolstadt + Neuburg)
- Reimanns via the existing GraphQL scraper service
- Canisius via PDF scrape
- Optional DeepL (DE → EN)
- Valkey/Redis cache (or in-memory for local/dev)

Served later under `/food` in cluster ingress.

## Modules

Hexagonal Quarkus layout (same shape as `app-backend-core`):

| Module | Role |
| --- | --- |
| `domain` | Entities / value objects |
| `application` | Ports and use cases |
| `adapters-inbound-rest` | JAX-RS + generated contract |
| `adapters-outbound-persistence` | Menu cache |
| `adapters-outbound-scraping` | Mensa / Canisius / Reimanns / DeepL |
| `bootstrap` | Quarkus wiring |

## Build

```bash
./mvnw verify
./mvnw quarkus:dev -pl bootstrap
./mvnw package -pl bootstrap -am
```

Contract dependency: `app.neuland:backend-food-api` from
[app-backend-contracts](https://github.com/neuland-ingolstadt/app-backend-contracts)
(GitHub Packages).

## License

No license is attached. All rights reserved by the author unless stated otherwise.
