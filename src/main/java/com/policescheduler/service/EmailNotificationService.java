package com.policescheduler.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private static final String FROM_EMAIL = "kspqueries@gmail.com";
    private static final String TO_EMAIL = "pradyota.ai@gmail.com";
    private static final String SECRET_NAME = "ksp-mail-key";

    @Value("${app.s3.region:ap-south-1}")
    private String awsRegion;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    private JavaMailSender mailSender;

    @PostConstruct
    public void init() {
        if (!emailEnabled) {
            log.info("Email notifications disabled");
            return;
        }
        try {
            String password = fetchSecretFromAWS();
            this.mailSender = createMailSender(password);
            log.info("Email notification service initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize email service (emails will be skipped): {}", e.getMessage());
            this.mailSender = null;
        }
    }

    private String fetchSecretFromAWS() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build()) {
            GetSecretValueResponse response = client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(SECRET_NAME).build());
            return response.secretString();
        }
    }

    private JavaMailSender createMailSender(String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(FROM_EMAIL);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return sender;
    }

    @Async
    public void sendTicketNotification(Long ticketId, String subject, String message, String category, String priority, String submittedBy) {
        if (mailSender == null) {
            log.warn("Email service not initialized, skipping notification for ticket #{}", ticketId);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(FROM_EMAIL);
            helper.setTo(TO_EMAIL);
            helper.setSubject("KSP-Query: Ticket #" + ticketId + " - " + subject);
            helper.setText(buildHtmlContent(ticketId, subject, message, category, priority, submittedBy), true);

            mailSender.send(mimeMessage);
            log.info("Ticket notification email sent for ticket #{}", ticketId);
        } catch (MessagingException e) {
            log.error("Failed to send email notification for ticket #{}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Sends an OTP email for password reset to the specified email address.
     * This method is synchronous so that ForgotPasswordService can catch failures.
     *
     * @param toEmail the recipient email address
     * @param otp the 6-digit OTP code
     * @throws RuntimeException if sending the email fails
     */
    public void sendOtpEmail(String toEmail, String otp) {
        if (mailSender == null) {
            // Dev mode: log OTP to console when email service is not configured
            log.warn("========================================================");
            log.warn("  EMAIL SERVICE NOT INITIALIZED - DEV MODE OTP");
            log.warn("  Email: {}", toEmail);
            log.warn("  OTP Code: {}", otp);
            log.warn("========================================================");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(FROM_EMAIL);
            helper.setTo(toEmail);
            helper.setSubject("KSP Workboard \u2014 Password Reset OTP");
            helper.setText(buildOtpHtmlContent(otp), true);

            mailSender.send(mimeMessage);
            log.info("OTP email sent successfully to {}", toEmail.replaceAll("(?<=.{3}).(?=.*@)", "*"));
        } catch (MessagingException e) {
            log.error("Failed to send OTP email: {}", e.getMessage());
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private String buildOtpHtmlContent(String otp) {
        StringBuilder otpDigits = new StringBuilder();
        for (int i = 0; i < otp.length(); i++) {
            otpDigits.append("""
                <td style="width:44px; height:52px; text-align:center; font-size:28px; font-weight:700; color:#0d9488; font-family:'Courier New', Courier, monospace; background:#f0fdfa; border:2px solid #99f6e4; border-radius:8px;">
                    %s
                </td>
            """.formatted(otp.charAt(i)));
            if (i < otp.length() - 1) {
                otpDigits.append("<td style=\"width:8px;\"></td>");
            }
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="margin:0; padding:0; font-family:'Segoe UI', Arial, sans-serif; background-color:#f1f5f9;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px; margin:0 auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                    <!-- Header -->
                    <tr>
                        <td style="background:linear-gradient(135deg, #312e81 0%%, #0d9488 100%%); padding:28px 32px;">
                            <h1 style="margin:0; color:#ffffff; font-size:20px; font-weight:700; letter-spacing:-0.5px;">
                                &#128274; KSP Workboard &mdash; Password Reset
                            </h1>
                            <p style="margin:6px 0 0; color:rgba(255,255,255,0.7); font-size:13px;">
                                CAR Police Station, Mangaluru
                            </p>
                        </td>
                    </tr>

                    <!-- OTP Content -->
                    <tr>
                        <td style="padding:36px 32px 20px; text-align:center;">
                            <p style="margin:0 0 8px; font-size:14px; color:#64748b; text-transform:uppercase; letter-spacing:1px; font-weight:600;">
                                Your OTP Code
                            </p>
                            <table cellpadding="0" cellspacing="0" style="margin:16px auto;">
                                <tr>
                                    %s
                                </tr>
                            </table>
                        </td>
                    </tr>

                    <!-- Expiry Notice -->
                    <tr>
                        <td style="padding:0 32px 20px; text-align:center;">
                            <p style="margin:0; font-size:14px; color:#d97706; font-weight:500;">
                                &#9200; This code expires in 10 minutes.
                            </p>
                        </td>
                    </tr>

                    <!-- Security Warning -->
                    <tr>
                        <td style="padding:0 32px 28px;">
                            <div style="padding:14px 18px; background:#fffbeb; border:1px solid #fde68a; border-radius:8px; border-left:4px solid #d97706;">
                                <p style="margin:0; font-size:13px; color:#92400e; line-height:1.6;">
                                    &#9888;&#65039; Do not share this code with anyone. If you did not request a password reset, please ignore this email.
                                </p>
                            </div>
                        </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                        <td style="padding:20px 32px 28px; border-top:1px solid #f1f5f9;">
                            <p style="margin:0; font-size:12px; color:#94a3b8; text-align:center;">
                                This is an automated notification from KSP Workboard.<br>
                                Please do not reply to this email.
                            </p>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(otpDigits.toString());
    }

    private String buildHtmlContent(Long ticketId, String subject, String message, String category, String priority, String submittedBy) {
        String raisedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
        String priorityColor = switch (priority.toUpperCase()) {
            case "CRITICAL" -> "#dc2626";
            case "HIGH" -> "#ea580c";
            case "MEDIUM" -> "#d97706";
            default -> "#059669";
        };

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="margin:0; padding:0; font-family: 'Segoe UI', Arial, sans-serif; background-color:#f1f5f9;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px; margin:0 auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                    <!-- Header -->
                    <tr>
                        <td style="background:linear-gradient(135deg, #312e81 0%%, #0d9488 100%%); padding:28px 32px;">
                            <h1 style="margin:0; color:#ffffff; font-size:20px; font-weight:700; letter-spacing:-0.5px;">
                                &#127963; KSP WorkBoard — New Query Raised
                            </h1>
                            <p style="margin:6px 0 0; color:rgba(255,255,255,0.7); font-size:13px;">
                                CAR Police Station, Mangaluru
                            </p>
                        </td>
                    </tr>

                    <!-- Ticket Summary -->
                    <tr>
                        <td style="padding:28px 32px 0;">
                            <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e2e8f0; border-radius:10px; overflow:hidden;">
                                <tr>
                                    <td style="padding:16px 20px; background:#f8fafc; border-bottom:1px solid #e2e8f0;">
                                        <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Query ID</span>
                                        <p style="margin:4px 0 0; font-size:18px; font-weight:700; color:#312e81;">#%d</p>
                                    </td>
                                    <td style="padding:16px 20px; background:#f8fafc; border-bottom:1px solid #e2e8f0; text-align:right;">
                                        <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Priority</span>
                                        <p style="margin:4px 0 0;">
                                            <span style="display:inline-block; padding:4px 12px; border-radius:20px; font-size:12px; font-weight:700; color:#fff; background:%s;">
                                                %s
                                            </span>
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2" style="padding:16px 20px;">
                                        <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Title</span>
                                        <p style="margin:4px 0 0; font-size:16px; font-weight:600; color:#0f172a;">%s</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>

                    <!-- Details Grid -->
                    <tr>
                        <td style="padding:20px 32px 0;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td width="50%%" style="padding:8px 0;">
                                        <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Category</span>
                                        <p style="margin:4px 0 0; font-size:14px; color:#334155; font-weight:500;">%s</p>
                                    </td>
                                    <td width="50%%" style="padding:8px 0;">
                                        <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Status</span>
                                        <p style="margin:4px 0 0;">
                                            <span style="display:inline-block; padding:3px 10px; border-radius:20px; font-size:11px; font-weight:600; color:#d97706; background:#fffbeb; border:1px solid #fde68a;">OPEN</span>
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="50%%" style="padding:8px 0;">
                                        <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Raised By</span>
                                        <p style="margin:4px 0 0; font-size:14px; color:#334155; font-weight:500;">%s</p>
                                    </td>
                                    <td width="50%%" style="padding:8px 0;">
                                        <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Raised Date</span>
                                        <p style="margin:4px 0 0; font-size:14px; color:#334155; font-weight:500;">%s</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>

                    <!-- Message Body -->
                    <tr>
                        <td style="padding:20px 32px;">
                            <span style="font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#64748b; font-weight:600;">Query Description</span>
                            <div style="margin:8px 0 0; padding:16px; background:#f8fafc; border-radius:8px; border-left:4px solid #0d9488;">
                                <p style="margin:0; font-size:14px; line-height:1.7; color:#334155; white-space:pre-wrap;">%s</p>
                            </div>
                        </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                        <td style="padding:20px 32px 28px; border-top:1px solid #f1f5f9;">
                            <p style="margin:0; font-size:12px; color:#94a3b8; text-align:center;">
                                This is an automated notification from KSP WorkBoard.<br>
                                Please do not reply to this email.
                            </p>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(ticketId, priorityColor, priority, subject, category, submittedBy, raisedDate, message);
    }
}
