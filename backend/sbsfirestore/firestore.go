package sbsfirestore

import (
	"context"
	"log/slog"

	"cloud.google.com/go/firestore"
	"google.golang.org/api/option"
)

type Config struct {
	FirestoreProject string
	FirestoreCreds   string
}

func NewFirestoreClient(ctx context.Context, config Config) (*firestore.Client, error) {
	slog.Info("initializing Cloud Firestore client...", "project", config.FirestoreProject)
	var opts []option.ClientOption
	if config.FirestoreCreds != "" {
		opts = append(opts, option.WithCredentialsFile(config.FirestoreCreds))
	}
	var err error
	client, err := firestore.NewClient(ctx, config.FirestoreProject, opts...)
	if err != nil {
		slog.Error("failed to initialize Firestore client", "error", err)
		return nil, err
	}

	return client, nil
}
