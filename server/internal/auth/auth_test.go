package auth

import (
	"testing"
)

func TestTokenPairGenerationAndValidation(t *testing.T) {
	secret := []byte("01234567890123456789012345678901")
	mgr := NewManager(secret)

	userID := "usr-123"
	email := "test@simsli.de"

	pair, err := mgr.GenerateTokenPair(userID, email)
	if err != nil {
		t.Fatalf("unexpected error generating token pair: %v", err)
	}

	if pair.AccessToken == "" {
		t.Fatal("expected non-empty access token")
	}
	if pair.RefreshToken == "" {
		t.Fatal("expected non-empty refresh token")
	}

	claims, err := mgr.ValidateAccessToken(pair.AccessToken)
	if err != nil {
		t.Fatalf("failed to validate access token: %v", err)
	}

	if claims.UserID != userID {
		t.Errorf("expected user ID %q, got %q", userID, claims.UserID)
	}
	if claims.Email != email {
		t.Errorf("expected email %q, got %q", email, claims.Email)
	}
}

func TestInvalidAccessToken(t *testing.T) {
	mgr := NewManager([]byte("01234567890123456789012345678901"))
	_, err := mgr.ValidateAccessToken("invalid.token.here")
	if err == nil {
		t.Fatal("expected error on invalid token, got nil")
	}
}
