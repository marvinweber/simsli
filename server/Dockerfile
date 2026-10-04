# Build stage
FROM golang:alpine AS builder

WORKDIR /app

# Download dependencies
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

ARG VERSION=0.2.0

# Build static binary (CGO_ENABLED=0 thanks to pure-Go SQLite)
RUN GOMEMLIMIT=1500MiB GOGC=50 CGO_ENABLED=0 GOOS=linux go build -p 1 -ldflags="-s -w -X net.marvinweber.simsli/server/internal/config.Version=${VERSION}" -o /simsli-server ./cmd/simsli

# Production stage
FROM alpine:3.20

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app
VOLUME /data

COPY --from=builder /simsli-server /usr/local/bin/simsli-server

ENV SIMSLI_PORT=8080
ENV SIMSLI_ADMIN_PORT=8081
ENV SIMSLI_DATABASE_URL="sqlite:///data/simsli.db"

EXPOSE 8080 8081

ENTRYPOINT ["simsli-server"]
