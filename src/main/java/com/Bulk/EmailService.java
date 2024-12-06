package com.Bulk;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final ScheduledExecutorService scheduler;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.scheduler = Executors.newScheduledThreadPool(1); // Thread pool for scheduling
    }

    public void scheduleEmailSending(EmailDetails emailDetails, EmailSchedule emailSchedule) {
        long delayInSeconds = calculateDelayInSeconds(emailSchedule.getScheduledTime());
        List<List<String>> batches = createBatches(emailDetails.getRecipients(), 500);

        // If user chooses to send all emails today but has more than 500 emails
        if (batches.size() > 1) {
            try {
				sendBatchImmediately(emailDetails, batches.get(0));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // Send the first batch today
            batches.remove(0); // Remove the first batch as it has been sent today

            // Schedule the remaining batches for the next available days
            for (int i = 0; i < batches.size(); i++) {
                final int batchIndex = i; // Making the index final or effectively final
                LocalDateTime nextScheduledTime = calculateNextScheduledDateTime(batchIndex, emailSchedule);
                long delayForNextBatch = Duration.between(LocalDateTime.now(), nextScheduledTime).getSeconds();
                scheduler.schedule(() -> {
                    try {
                        sendBatch(emailDetails, batches.get(batchIndex));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, delayForNextBatch, TimeUnit.SECONDS);
            }
        } else {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    sendBatch(emailDetails, batches.get(0));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, delayInSeconds, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        }
    }

    private long calculateDelayInSeconds(LocalTime scheduledTime) {
        LocalTime currentTime = LocalTime.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledDateTime;

        if (currentTime.isBefore(scheduledTime)) {
            scheduledDateTime = now.with(scheduledTime);
        } else {
            scheduledDateTime = now.plusDays(1).with(scheduledTime);
        }

        return Duration.between(now, scheduledDateTime).getSeconds();
    }

    private void sendBatchImmediately(EmailDetails emailDetails, List<String> batch) throws Exception {
        for (String recipient : batch) {
            sendEmail(emailDetails, recipient);
        }
        System.out.println("Batch of " + batch.size() + " emails sent immediately.");
    }

    private void sendBatch(EmailDetails emailDetails, List<String> batch) throws Exception {
        for (String recipient : batch) {
            sendEmail(emailDetails, recipient);
        }
        System.out.println("Batch of " + batch.size() + " emails sent.");
    }

    private void sendEmail(EmailDetails emailDetails, String recipient) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(emailDetails.getSenderEmail());
        helper.setTo(recipient);
        helper.setSubject(emailDetails.getSubject());
        helper.setText(emailDetails.getHtmlContent(), true);

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.ssl.trust", "smtp.gmail.com");

        JavaMailSenderImpl dynamicMailSender = new JavaMailSenderImpl();
        dynamicMailSender.setHost("smtp.gmail.com");
        dynamicMailSender.setPort(587);
        dynamicMailSender.setUsername(emailDetails.getSenderEmail());
        dynamicMailSender.setPassword(emailDetails.getPassword());
        dynamicMailSender.setJavaMailProperties(properties);

        dynamicMailSender.send(message);
    }

    private List<List<String>> createBatches(List<String> recipients, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < recipients.size(); i += batchSize) {
            int end = Math.min(i + batchSize, recipients.size());
            batches.add(new ArrayList<>(recipients.subList(i, end)));
        }
        return batches;
    }

    private LocalDateTime calculateNextScheduledDateTime(int batchIndex, EmailSchedule emailSchedule) {
        LocalDateTime now = LocalDateTime.now();
        
        // Convert LocalTime to LocalDateTime by setting it to today's date.
        LocalDateTime scheduledTime = emailSchedule.getScheduledTime().atDate(now.toLocalDate());

        // Add batchIndex days to the initial scheduled time
        scheduledTime = scheduledTime.plusDays(batchIndex + 1);

        return scheduledTime;
    }

}
