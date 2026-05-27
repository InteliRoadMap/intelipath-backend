package com.inteliroadmap.backend.utils;

import java.util.Random;

public class EmailUtil {

    /**
     * Generate a 6-digit random OTP
     * @return 6-digit OTP string
     */
    public static String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}
