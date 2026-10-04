package config

import (
	"crypto/rand"
	"encoding/hex"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port             string
	AdminPort        string
	ServerMode       string // "self_hosted" or "cloud"
	RegistrationOpen bool
	EmailAllowlist   []string
	DatabaseURL      string
	JWTSecret        []byte
	PublicURL        string
	AdminKey         string
	Debug            bool
	Version          string
	APIVersion       int
	MinAppVersion    string

	// SMTP
	SMTPHost     string
	SMTPPort     int
	SMTPUser     string
	SMTPPassword string
	SMTPFrom     string
}

// Version is the server release version, synchronized with app releases.
// Can be overridden at link time: -ldflags "-X net.marvinweber.simsli/server/internal/config.Version=x.y.z"
var Version = "0.2.0"

func Load() *Config {
	port := getEnv("SIMSLI_PORT", "8080")
	adminPort := getEnv("SIMSLI_ADMIN_PORT", "8081")
	serverMode := strings.ToLower(getEnv("SIMSLI_SERVER_MODE", "self_hosted"))
	if serverMode != "cloud" {
		serverMode = "self_hosted"
	}

	regOpen := false
	if val := os.Getenv("SIMSLI_REGISTRATION_OPEN"); val != "" {
		regOpen = strings.ToLower(val) == "true" || val == "1"
	}

	debug := false
	if val := os.Getenv("SIMSLI_DEBUG"); val != "" {
		debug = strings.ToLower(val) == "true" || val == "1"
	}

	var allowlist []string
	if val := os.Getenv("SIMSLI_EMAIL_ALLOWLIST"); val != "" {
		for _, part := range strings.Split(val, ",") {
			trimmed := strings.ToLower(strings.TrimSpace(part))
			if trimmed != "" {
				allowlist = append(allowlist, trimmed)
			}
		}
	}

	dbURL := getEnv("SIMSLI_DATABASE_URL", getEnv("DATABASE_URL", "sqlite://data/simsli.db"))

	jwtSecretStr := os.Getenv("SIMSLI_JWT_SECRET")
	var jwtSecret []byte
	if jwtSecretStr != "" {
		jwtSecret = []byte(jwtSecretStr)
	} else {
		// Generate random 32-byte secret for dev if not provided
		jwtSecret = make([]byte, 32)
		if _, err := rand.Read(jwtSecret); err != nil {
			panic("failed to generate random JWT secret: " + err.Error())
		}
	}

	publicURL := strings.TrimRight(getEnv("SIMSLI_PUBLIC_URL", "http://localhost:"+port), "/")
	adminKey := os.Getenv("SIMSLI_ADMIN_KEY")

	smtpPort, _ := strconv.Atoi(getEnv("SIMSLI_SMTP_PORT", "587"))

	return &Config{
		Port:             port,
		AdminPort:        adminPort,
		ServerMode:       serverMode,
		RegistrationOpen: regOpen,
		EmailAllowlist:   allowlist,
		DatabaseURL:      dbURL,
		JWTSecret:        jwtSecret,
		PublicURL:        publicURL,
		AdminKey:         adminKey,
		Debug:            debug,
		Version:          getEnv("SIMSLI_VERSION", Version),
		APIVersion:       1,
		MinAppVersion:    getEnv("SIMSLI_MIN_APP_VERSION", "0.1.0"),
		SMTPHost:         os.Getenv("SIMSLI_SMTP_HOST"),
		SMTPPort:         smtpPort,
		SMTPUser:         os.Getenv("SIMSLI_SMTP_USER"),
		SMTPPassword:     os.Getenv("SIMSLI_SMTP_PASSWORD"),
		SMTPFrom:         getEnv("SIMSLI_SMTP_FROM", "noreply@simsli.app"),
	}
}

func (c *Config) IsEmailAllowed(email string) bool {
	email = strings.ToLower(strings.TrimSpace(email))
	if len(c.EmailAllowlist) == 0 {
		return true // No allowlist means all allowed (subject to RegistrationOpen)
	}

	for _, pattern := range c.EmailAllowlist {
		if pattern == "*" || pattern == email {
			return true
		}
		// Wildcard domain match: *@domain.de
		if strings.HasPrefix(pattern, "*@") {
			domain := strings.TrimPrefix(pattern, "*@")
			if strings.HasSuffix(email, "@"+domain) {
				return true
			}
		}
	}
	return false
}

func (c *Config) JWTSecretHex() string {
	return hex.EncodeToString(c.JWTSecret)
}

func getEnv(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}
