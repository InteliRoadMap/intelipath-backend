package com.inteliroadmap.backend.services;

public interface EmailService {

    void sendFeedbackNotificationEmail(String email, String receiverName, String senderName, String content) ;
}
