package biz.ugur.busroutebackend.shared.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailNotificationService implements EmailNotificationService {

    private final MailProperties properties;
    private final java.time.Clock clock;
    private final AlertQuietHours quietHours;

    @Override
    public Mono<Void> sendComplaintNotification(String title, String type, String description) {
        return dispatch(properties.getAdminEmail(),
                "Новая жалоба: " + title,
                buildEmailBody(title, type, description),
                "complaint");
    }

    @Override
    public Mono<Void> sendBusinessSubmittedAlert(String businessName, String businessType,
                                                  String contactPhone, String contactEmail) {
        return dispatch(properties.getAdminEmail(),
                "Новая заявка бизнеса: " + businessName,
                buildBusinessSubmittedBody(businessName, businessType, contactPhone, contactEmail),
                "business-submitted");
    }

    @Override
    public Mono<Void> sendBusinessApprovedNotification(String recipientEmail, String businessName) {
        if (!hasRecipient(recipientEmail)) return Mono.empty();
        return dispatch(recipientEmail,
                "Заявка одобрена: " + businessName,
                buildBusinessApprovedBody(businessName),
                "business-approved");
    }

    @Override
    public Mono<Void> sendBusinessRejectedNotification(String recipientEmail,
                                                        String businessName, String reason) {
        if (!hasRecipient(recipientEmail)) return Mono.empty();
        return dispatch(recipientEmail,
                "Заявка отклонена: " + businessName,
                buildBusinessRejectedBody(businessName, reason),
                "business-rejected");
    }

    @Override
    public Mono<Void> sendBusinessSuspendedNotification(String recipientEmail,
                                                         String businessName, String reason) {
        if (!hasRecipient(recipientEmail)) return Mono.empty();
        return dispatch(recipientEmail,
                "Компания приостановлена: " + businessName,
                buildBusinessSuspendedBody(businessName, reason),
                "business-suspended");
    }

    @Override
    public Mono<Void> sendPaymentCompletedNotification(String recipientEmail, String businessName,
                                                        double amount, String currency,
                                                        String orderNumber) {
        if (!hasRecipient(recipientEmail)) return Mono.empty();
        return dispatch(recipientEmail,
                "Оплата прошла успешно: " + orderNumber,
                buildPaymentCompletedBody(businessName, amount, currency, orderNumber),
                "payment-completed");
    }

    @Override
    public Mono<Void> sendPaymentFailedNotification(String recipientEmail, String businessName,
                                                     String reason, String orderNumber) {
        if (!hasRecipient(recipientEmail)) return Mono.empty();
        return dispatch(recipientEmail,
                "Оплата не прошла: " + orderNumber,
                buildPaymentFailedBody(businessName, reason, orderNumber),
                "payment-failed");
    }

    @Override
    public Mono<Void> sendGpsAlert(java.util.List<String> recipients,
                                    String tenant,
                                    biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind kind,
                                    String subject,
                                    String body) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("[GPS_ALERT] no recipients configured for {} {}", tenant, kind);
            return Mono.empty();
        }
        String tag = "gps-alert-" + kind.name().toLowerCase();
        return reactor.core.publisher.Flux.fromIterable(recipients)
                .concatMap(to -> dispatch(to, subject, body, tag))
                .then();
    }

    private Mono<Void> dispatch(String to, String subject, String body, String kind) {
        if (!hasRecipient(to)) {
            log.debug("[email/{}] skipped: no recipient", kind);
            return Mono.empty();
        }
        if (quietHours.active()) {
            log.debug("[email/{}] suppressed: quiet hours", kind);
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    sendEmail(to, subject, body);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.info("[email/{}] sent to {}", kind, to))
                .onErrorResume(e -> {
                    log.error("[email/{}] failed for {}: {}", kind, to, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private static boolean hasRecipient(String recipientEmail) {
        return recipientEmail != null && !recipientEmail.isBlank();
    }

    private void sendEmail(String to, String subject, String htmlBody) throws IOException {
        String host = properties.getHost();
        int port = properties.getPort();
        String username = properties.getUsername();
        String password = properties.getPassword();
        String from = properties.getFrom();

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10_000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expectCode(reader, "220");

            send(writer, "EHLO localhost");
            readAllLines(reader);

            send(writer, "STARTTLS");
            expectCode(reader, "220");

            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(socket, host, port, true);
            sslSocket.startHandshake();

            BufferedReader sslReader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter sslWriter = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8));

            send(sslWriter, "EHLO localhost");
            readAllLines(sslReader);

            send(sslWriter, "AUTH LOGIN");
            expectCode(sslReader, "334");

            send(sslWriter, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
            expectCode(sslReader, "334");

            send(sslWriter, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
            expectCode(sslReader, "235");

            send(sslWriter, "MAIL FROM:<" + from + ">");
            expectCode(sslReader, "250");

            send(sslWriter, "RCPT TO:<" + to + ">");
            expectCode(sslReader, "250");

            send(sslWriter, "DATA");
            expectCode(sslReader, "354");

            String mimeMessage = buildMimeMessage(from, to, subject, htmlBody);
            sslWriter.write(mimeMessage);
            sslWriter.flush();

            send(sslWriter, "\r\n.");
            expectCode(sslReader, "250");

            send(sslWriter, "QUIT");
        }
    }

    private String buildMimeMessage(String from, String to, String subject, String htmlBody) {
        String encodedSubject = "=?UTF-8?B?" +
                Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";

        return "From: " + from + "\r\n" +
                "To: " + to + "\r\n" +
                "Subject: " + encodedSubject + "\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: base64\r\n" +
                "\r\n" +
                Base64.getMimeEncoder(76, "\r\n".getBytes()).encodeToString(
                        htmlBody.getBytes(StandardCharsets.UTF_8));
    }

    private void send(BufferedWriter writer, String command) throws IOException {
        writer.write(command + "\r\n");
        writer.flush();
    }

    private void expectCode(BufferedReader reader, String code) throws IOException {
        String line = reader.readLine();
        if (line == null || !line.startsWith(code)) {
            throw new IOException("SMTP error: expected " + code + ", got: " + line);
        }
        while (line != null && line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
        }
    }

    private void readAllLines(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        while (line != null && line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
        }
    }

    private String buildEmailBody(String title, String type, String description) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; padding: 20px;">
                    <h2 style="color: #dc3545;">Новая жалоба</h2>
                    <table style="border-collapse: collapse; width: 100%%;">
                        <tr>
                            <td style="padding: 8px; font-weight: bold; color: #555;">Тип:</td>
                            <td style="padding: 8px;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; font-weight: bold; color: #555;">Заголовок:</td>
                            <td style="padding: 8px;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; font-weight: bold; color: #555;">Описание:</td>
                            <td style="padding: 8px;">%s</td>
                        </tr>
                    </table>
                    <hr style="margin-top: 20px; border: none; border-top: 1px solid #ddd;">
                    <p style="color: #888; font-size: 12px;">Bus Route Admin Panel</p>
                </body>
                </html>
                """.formatted(type, title, description);
    }

    private String buildBusinessSubmittedBody(String name, String type,
                                               String phone, String email) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; padding: 20px;">
                    <h2 style="color: #0d6efd;">Новая заявка бизнеса</h2>
                    <p>На модерации появилась заявка от компании:</p>
                    <table style="border-collapse: collapse;">
                        <tr><td style="padding:6px;color:#555;">Название:</td><td style="padding:6px;font-weight:bold;">%s</td></tr>
                        <tr><td style="padding:6px;color:#555;">Тип:</td><td style="padding:6px;">%s</td></tr>
                        <tr><td style="padding:6px;color:#555;">Телефон:</td><td style="padding:6px;">%s</td></tr>
                        <tr><td style="padding:6px;color:#555;">E-mail:</td><td style="padding:6px;">%s</td></tr>
                    </table>
                    <p style="margin-top:16px;">Откройте админ-панель для модерации.</p>
                </body></html>
                """.formatted(e(name), e(type), e(phone), e(email));
    }

    private String buildBusinessApprovedBody(String name) {
        return """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                    <h2 style="color:#198754;">Заявка одобрена</h2>
                    <p>Ваша компания <strong>%s</strong> одобрена.</p>
                    <p>Теперь вы можете создавать рекламные размещения и оплачивать их.</p>
                </body></html>
                """.formatted(e(name));
    }

    private String buildBusinessRejectedBody(String name, String reason) {
        return """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                    <h2 style="color:#dc3545;">Заявка отклонена</h2>
                    <p>Заявка компании <strong>%s</strong> отклонена.</p>
                    <p><strong>Причина:</strong> %s</p>
                    <p>Вы можете подать новую заявку после устранения замечаний.</p>
                </body></html>
                """.formatted(e(name), e(reason));
    }

    private String buildBusinessSuspendedBody(String name, String reason) {
        return """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                    <h2 style="color:#ffc107;">Компания приостановлена</h2>
                    <p>Показ рекламы компании <strong>%s</strong> временно приостановлен.</p>
                    <p><strong>Причина:</strong> %s</p>
                    <p>Для восстановления свяжитесь с администратором.</p>
                </body></html>
                """.formatted(e(name), e(reason));
    }

    private String buildPaymentCompletedBody(String name, double amount, String currency,
                                              String orderNumber) {
        return """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                    <h2 style="color:#198754;">Оплата прошла</h2>
                    <p>Платёж компании <strong>%s</strong> по заказу <code>%s</code>
                       на сумму <strong>%.2f %s</strong> успешно проведён.</p>
                    <p>Размещение активировано и будет показано в приложении согласно расписанию.</p>
                </body></html>
                """.formatted(e(name), e(orderNumber), amount, e(currency));
    }

    private String buildPaymentFailedBody(String name, String reason, String orderNumber) {
        return """
                <html><body style="font-family:Arial,sans-serif;padding:20px;">
                    <h2 style="color:#dc3545;">Оплата не прошла</h2>
                    <p>Платёж компании <strong>%s</strong> по заказу <code>%s</code> не был завершён.</p>
                    <p><strong>Причина:</strong> %s</p>
                    <p>Вы можете создать новый платёж из панели управления.</p>
                </body></html>
                """.formatted(e(name), e(orderNumber), e(reason));
    }

    private static String e(String value) {
        if (value == null) return "—";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
