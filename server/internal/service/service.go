package service

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"strings"
	"time"

	"github.com/google/uuid"
	"net.marvinweber.simsli/server/internal/auth"
	"net.marvinweber.simsli/server/internal/config"
	"net.marvinweber.simsli/server/internal/model"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/repository"
)

var (
	ErrUnauthorized     = errors.New("unauthorized")
	ErrForbidden        = errors.New("forbidden")
	ErrRegistrationClosed = errors.New("registration is closed on this server")
	ErrEmailNotAllowed  = errors.New("email address is not allowed on this server")
)

type Service struct {
	repo    *repository.Repository
	authMgr *auth.Manager
	hub     *realtime.Hub
	cfg     *config.Config
	email   EmailSender
	billing BillingService
}

func NewService(
	repo *repository.Repository,
	authMgr *auth.Manager,
	hub *realtime.Hub,
	cfg *config.Config,
	email EmailSender,
	billing BillingService,
) *Service {
	return &Service{
		repo:    repo,
		authMgr: authMgr,
		hub:     hub,
		cfg:     cfg,
		email:   email,
		billing: billing,
	}
}

func (s *Service) GetServerInfo() model.ServerInfo {
	return model.ServerInfo{
		ServerMode:       s.cfg.ServerMode,
		DatabaseDriver:   s.repo.GetDatabaseDriver(),
		RegistrationOpen: s.cfg.RegistrationOpen,
		BillingEnabled:   s.billing.IsBillingEnabled(),
		Version:          "1.0.0",
	}
}

// RequestMagicLink creates a magic link and sends it
func (s *Service) RequestMagicLink(ctx context.Context, email string) (string, error) {
	email = strings.ToLower(strings.TrimSpace(email))
	if email == "" || !strings.Contains(email, "@") {
		return "", errors.New("invalid email address")
	}

	if !s.cfg.IsEmailAllowed(email) {
		return "", ErrEmailNotAllowed
	}

	// If registration is closed, verify the user exists
	if !s.cfg.RegistrationOpen {
		_, err := s.repo.GetUserByEmail(ctx, email)
		if err != nil {
			if errors.Is(err, repository.ErrNotFound) {
				return "", ErrRegistrationClosed
			}
			return "", err
		}
	}

	rawToken, err := auth.GenerateMagicLinkToken()
	if err != nil {
		return "", err
	}

	now := time.Now().UTC()
	ml := &model.MagicLink{
		Token:     rawToken,
		Email:     email,
		CreatedAt: now,
		ExpiresAt: now.Add(15 * time.Minute),
	}

	if err := s.repo.CreateMagicLink(ctx, ml); err != nil {
		return "", fmt.Errorf("failed to save magic link: %w", err)
	}

	// Construct deep link or web verify link
	// The mobile app handles simsli://auth/verify?token=...
	verifyURL := fmt.Sprintf("%s/api/v1/auth/verify?token=%s", s.cfg.PublicURL, rawToken)

	// Send email or log to console
	_ = s.email.SendMagicLink(email, verifyURL)

	return rawToken, nil
}

// VerifyMagicLink validates the token, logs in / registers the user, and issues a JWT token pair
func (s *Service) VerifyMagicLink(ctx context.Context, token string) (*auth.TokenPair, *model.User, error) {
	ml, err := s.repo.GetMagicLink(ctx, token)
	if err != nil {
		return nil, nil, errors.New("invalid or expired magic link")
	}

	if ml.UsedAt != nil {
		return nil, nil, errors.New("magic link has already been used")
	}

	if time.Now().UTC().After(ml.ExpiresAt) {
		return nil, nil, errors.New("magic link has expired")
	}

	if err := s.repo.MarkMagicLinkUsed(ctx, token); err != nil {
		return nil, nil, fmt.Errorf("failed to mark token used: %w", err)
	}

	// Find or create user
	user, err := s.repo.GetUserByEmail(ctx, ml.Email)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			user = &model.User{
				ID:    uuid.NewString(),
				Email: ml.Email,
			}
			if err := s.repo.CreateUser(ctx, user); err != nil {
				return nil, nil, fmt.Errorf("failed to create user: %w", err)
			}
		} else {
			return nil, nil, err
		}
	}

	tokens, err := s.authMgr.GenerateTokenPair(user.ID, user.Email)
	if err != nil {
		return nil, nil, err
	}

	// Store refresh token hash in DB
	rt := &model.RefreshToken{
		TokenHash: auth.HashToken(tokens.RefreshToken),
		UserID:    user.ID,
		ExpiresAt: time.Now().UTC().Add(30 * 24 * time.Hour),
	}
	if err := s.repo.CreateRefreshToken(ctx, rt); err != nil {
		return nil, nil, fmt.Errorf("failed to save refresh token: %w", err)
	}

	return tokens, user, nil
}

// RefreshTokens rotates refresh tokens and issues a new access token
func (s *Service) RefreshTokens(ctx context.Context, rawRefreshToken string) (*auth.TokenPair, error) {
	tokenHash := auth.HashToken(rawRefreshToken)
	rt, err := s.repo.GetRefreshToken(ctx, tokenHash)
	if err != nil {
		return nil, auth.ErrInvalidToken
	}

	if rt.RevokedAt != nil || time.Now().UTC().After(rt.ExpiresAt) {
		return nil, auth.ErrInvalidToken
	}

	user, err := s.repo.GetUserByID(ctx, rt.UserID)
	if err != nil {
		return nil, auth.ErrInvalidToken
	}

	// Revoke old refresh token
	_ = s.repo.RevokeRefreshToken(ctx, tokenHash)

	newTokens, err := s.authMgr.GenerateTokenPair(user.ID, user.Email)
	if err != nil {
		return nil, err
	}

	// Save new refresh token
	newRT := &model.RefreshToken{
		TokenHash: auth.HashToken(newTokens.RefreshToken),
		UserID:    user.ID,
		ExpiresAt: time.Now().UTC().Add(30 * 24 * time.Hour),
	}
	if err := s.repo.CreateRefreshToken(ctx, newRT); err != nil {
		return nil, err
	}

	return newTokens, nil
}

// RevokeRefreshToken logs out the user session
func (s *Service) RevokeRefreshToken(ctx context.Context, rawRefreshToken string) error {
	tokenHash := auth.HashToken(rawRefreshToken)
	return s.repo.RevokeRefreshToken(ctx, tokenHash)
}

// Households

func (s *Service) CreateHousehold(ctx context.Context, userID, householdID, name string) (*model.Household, error) {
	if householdID == "" {
		householdID = uuid.NewString()
	}
	if name == "" {
		name = "My Household"
	}

	hh := &model.Household{
		ID:   householdID,
		Name: name,
	}

	if err := s.repo.CreateHouseholdWithOwner(ctx, hh, userID); err != nil {
		return nil, err
	}

	s.hub.Broadcast(householdID)
	return s.repo.GetHousehold(ctx, householdID)
}

func (s *Service) GetHousehold(ctx context.Context, userID, householdID string) (*model.Household, error) {
	isMember, err := s.repo.IsHouseholdMember(ctx, householdID, userID)
	if err != nil {
		return nil, err
	}
	if !isMember {
		return nil, ErrForbidden
	}
	return s.repo.GetHousehold(ctx, householdID)
}

func (s *Service) GetHouseholdMembers(ctx context.Context, userID, householdID string) ([]model.HouseholdMember, error) {
	isMember, err := s.repo.IsHouseholdMember(ctx, householdID, userID)
	if err != nil {
		return nil, err
	}
	if !isMember {
		return nil, ErrForbidden
	}
	return s.repo.GetHouseholdMembers(ctx, householdID)
}

func (s *Service) RemoveHouseholdMember(ctx context.Context, requesterUserID, householdID, targetUserID string) error {
	isOwner, err := s.repo.IsHouseholdOwner(ctx, householdID, requesterUserID)
	if err != nil {
		return err
	}
	if !isOwner {
		return ErrForbidden
	}
	if requesterUserID == targetUserID {
		return errors.New("household owner cannot remove themselves")
	}

	if err := s.repo.RemoveHouseholdMember(ctx, householdID, targetUserID); err != nil {
		return err
	}

	s.hub.Broadcast(householdID)
	return nil
}

func (s *Service) CreateInvite(ctx context.Context, userID, householdID string) (*model.InviteToken, error) {
	isOwner, err := s.repo.IsHouseholdOwner(ctx, householdID, userID)
	if err != nil {
		return nil, err
	}
	if !isOwner {
		return nil, ErrForbidden
	}

	// 8-character random uppercase alphanumeric token
	tokenStr := generateShortToken(8)
	now := time.Now().UTC()
	it := &model.InviteToken{
		Token:       tokenStr,
		HouseholdID: householdID,
		CreatedBy:   userID,
		CreatedAt:   now,
		ExpiresAt:   now.Add(24 * time.Hour),
	}

	if err := s.repo.CreateInviteToken(ctx, it); err != nil {
		return nil, err
	}
	return it, nil
}

func (s *Service) AcceptInvite(ctx context.Context, userID, token string) (string, error) {
	householdID, err := s.repo.AcceptInvite(ctx, token, userID)
	if err != nil {
		return "", err
	}

	s.hub.Broadcast(householdID)
	return householdID, nil
}

// Sync & Deltas

func (s *Service) GetDeltas(ctx context.Context, userID, householdID string, since time.Time) (*model.DeltaResponse, error) {
	isMember, err := s.repo.IsHouseholdMember(ctx, householdID, userID)
	if err != nil {
		return nil, err
	}
	if !isMember {
		return nil, ErrForbidden
	}

	return s.repo.GetDeltas(ctx, householdID, since)
}

func (s *Service) Flush(ctx context.Context, userID string, req *model.FlushRequest) error {
	isMember, err := s.repo.IsHouseholdMember(ctx, req.HouseholdID, userID)
	if err != nil {
		return err
	}
	if !isMember {
		return ErrForbidden
	}

	// Apply mutations
	if err := s.repo.Flush(ctx, req); err != nil {
		return err
	}

	// Trigger real-time broadcast to all connected devices in this household
	s.hub.Broadcast(req.HouseholdID)
	return nil
}

// Admin / Dashboard Helpers

func (s *Service) ListPendingMagicLinks(ctx context.Context) ([]model.MagicLink, error) {
	return s.repo.ListPendingMagicLinks(ctx, 20)
}

func (s *Service) ListUsers(ctx context.Context, limit, offset int) ([]model.User, int, error) {
	users, err := s.repo.ListUsers(ctx, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	count, _ := s.repo.CountUsers(ctx)
	return users, count, nil
}

func (s *Service) ListHouseholds(ctx context.Context, limit, offset int) ([]model.Household, int, error) {
	hhs, err := s.repo.ListHouseholds(ctx, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	count, _ := s.repo.CountHouseholds(ctx)
	return hhs, count, nil
}

func (s *Service) AdminCreateUser(ctx context.Context, email string) (*model.User, error) {
	email = strings.ToLower(strings.TrimSpace(email))
	existing, err := s.repo.GetUserByEmail(ctx, email)
	if err == nil && existing != nil {
		return existing, nil
	}

	user := &model.User{
		ID:    uuid.NewString(),
		Email: email,
	}
	if err := s.repo.CreateUser(ctx, user); err != nil {
		return nil, err
	}
	return user, nil
}

func generateShortToken(n int) string {
	const charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // omit confusing 0/O, 1/I
	b := make([]byte, n)
	for i := range b {
		b[i] = charset[rand.Intn(len(charset))]
	}
	return string(b)
}
