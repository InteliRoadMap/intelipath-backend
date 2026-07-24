# intelipath-backend

Spring Boot backend for InteliPath.

## 🚀 Running the full stack

Docker Compose orchestration (Postgres + this backend + the AI service) lives in the
separate [`infrastructure`](https://github.com/InteliRoadMap/infrastructure) repo. Clone it
next to this repo and the two app repos:

```
BackEnd/
  infrastructure/
  intelipath-backend/   <- this repo
  intelipath-service/
```

Then follow the quick-start in the infrastructure repo's README.

## Prerequisites

- Copy `.env.example` → `.env` and fill in credentials before starting the stack.
- Swagger UI (`http://localhost:8080/swagger-ui.html`) is available only under the `dev`
  Spring profile; it is disabled in `prod`.
