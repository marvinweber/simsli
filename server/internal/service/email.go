package service

import (
	"fmt"
	"log"
	"net.marvinweber.simsli/server/internal/config"
	"net/smtp"
)

type EmailSender interface {
	SendMagicLink(toEmail, link string) error
}

type SMTPEmailSender struct {
	cfg *config.Config
}

func NewEmailSender(cfg *config.Config) EmailSender {
	return &SMTPEmailSender{cfg: cfg}
}

func (s *SMTPEmailSender) SendMagicLink(toEmail, link string) error {
	// If SMTP is not configured, log to stdout
	if s.cfg.SMTPHost == "" {
		log.Printf("\n=======================================================\n"+
			"[AUTH] Magic link for %s:\n%s\n"+
			"=======================================================\n", toEmail, link)
		return nil
	}

	subject := "Subject: Your Simsli Sign-in Link\r\n"
	fromHeader := fmt.Sprintf("From: %s\r\n", s.cfg.SMTPFrom)
	toHeader := fmt.Sprintf("To: %s\r\n", toEmail)
	mime := "MIME-version: 1.0;\nContent-Type: text/plain; charset=\"UTF-8\";\n\n"
	body := fmt.Sprintf("Hello,\n\nUse this link to sign in to your Simsli shopping list:\n\n%s\n\nThis link is valid for 15 minutes.\n\nHappy shopping!\n", link)

	msg := []byte(fromHeader + toHeader + subject + mime + body)

	var auth smtp.Auth
	if s.cfg.SMTPUser != "" && s.cfg.SMTPPassword != "" {
		auth = smtp.PlainAuth("", s.cfg.SMTPUser, s.cfg.SMTPPassword, s.cfg.SMTPHost)
	}

	addr := fmt.Sprintf("%s:%d", s.cfg.SMTPHost, s.cfg.SMTPPort)
	if err := smtp.SendMail(addr, auth, s.cfg.SMTPFrom, []string{toEmail}, msg); err != nil {
		log.Printf("[SMTP] Failed to send email to %s: %v. Fallback link: %s", toEmail, err, link)
		return fmt.Errorf("failed to send email: %w", err)
	}

	log.Printf("[SMTP] Magic link sent to %s", toEmail)
	return nil
}
