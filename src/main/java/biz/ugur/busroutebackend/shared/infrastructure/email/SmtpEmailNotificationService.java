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

    @Override
    public Mono<Void> sendComplaintNotification(String title, String type, String description) {
        return Mono.fromCallable(() -> {
                    sendEmail(
                            properties.getAdminEmail(),
                            "Новая жалоба: " + title,
                            buildEmailBody(title, type, description)
                    );
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.info("Complaint email sent to {}", properties.getAdminEmail()))
                .onErrorResume(e -> {
                    log.error("Failed to send complaint email: {}", e.getMessage(), e);
                    return Mono.empty();
                })
                .then();
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
}
