package service

import (
	"context"
	"errors"
)

var (
	ErrLimitExceeded = errors.New("free-tier limit exceeded; upgrade to Simsli Pro to add more items")
)

type ResourceType string

const (
	ResourceStores  ResourceType = "stores"
	ResourceItems   ResourceType = "items"
	ResourceMembers ResourceType = "members"
	ResourceEntries ResourceType = "entries"
)

type HouseholdPlan struct {
	Plan             string
	Status           string
	MaxStores        int // -1 for unlimited
	MaxItems         int
	MaxMembers       int
	MaxActiveEntries int
}

type BillingService interface {
	GetPlan(ctx context.Context, householdID string) HouseholdPlan
	CheckLimit(ctx context.Context, householdID string, resource ResourceType, currentCount int) error
	IsBillingEnabled() bool
}

// UnlimitedBillingService is used for self-hosted mode (unlimited by default)
type UnlimitedBillingService struct{}

func NewUnlimitedBillingService() *UnlimitedBillingService {
	return &UnlimitedBillingService{}
}

func (s *UnlimitedBillingService) GetPlan(ctx context.Context, householdID string) HouseholdPlan {
	return HouseholdPlan{
		Plan:             "unlimited",
		Status:           "active",
		MaxStores:        -1,
		MaxItems:         -1,
		MaxMembers:       -1,
		MaxActiveEntries: -1,
	}
}

func (s *UnlimitedBillingService) CheckLimit(ctx context.Context, householdID string, resource ResourceType, currentCount int) error {
	return nil // Always allowed
}

func (s *UnlimitedBillingService) IsBillingEnabled() bool {
	return false
}

// CloudBillingService enforces BIZ-1 limits on cloud free-tier households
type CloudBillingService struct{}

func NewCloudBillingService() *CloudBillingService {
	return &CloudBillingService{}
}

func (s *CloudBillingService) GetPlan(ctx context.Context, householdID string) HouseholdPlan {
	// Defaults to Free tier limits (BIZ-1): 3 stores, 20 items, 2 users, 25 live list entries
	return HouseholdPlan{
		Plan:             "free",
		Status:           "active",
		MaxStores:        3,
		MaxItems:         20,
		MaxMembers:       2,
		MaxActiveEntries: 25,
	}
}

func (s *CloudBillingService) CheckLimit(ctx context.Context, householdID string, resource ResourceType, currentCount int) error {
	limits := s.GetPlan(ctx, householdID)
	switch resource {
	case ResourceStores:
		if limits.MaxStores > 0 && currentCount >= limits.MaxStores {
			return ErrLimitExceeded
		}
	case ResourceItems:
		if limits.MaxItems > 0 && currentCount >= limits.MaxItems {
			return ErrLimitExceeded
		}
	case ResourceMembers:
		if limits.MaxMembers > 0 && currentCount >= limits.MaxMembers {
			return ErrLimitExceeded
		}
	case ResourceEntries:
		if limits.MaxActiveEntries > 0 && currentCount >= limits.MaxActiveEntries {
			return ErrLimitExceeded
		}
	}
	return nil
}

func (s *CloudBillingService) IsBillingEnabled() bool {
	return true
}
