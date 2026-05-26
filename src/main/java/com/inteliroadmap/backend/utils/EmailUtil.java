package com.inteliroadmap.backend.utils;

import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class EmailUtil {
    public static String generateOtp(){
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}
