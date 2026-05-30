package com.goodix.ble.gr.lib.com.transport;

public final class MacUtils {
    private static final char[] HEX_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static long macToValue(CharSequence mac) {
        long val = 0;
        if (mac != null) {
            for (int i = 0; i < mac.length(); i++) {
                final char ch = mac.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    val <<= 4;
                    val |= ((ch - '0') & 0xFL);
                } else if (ch >= 'A' && ch <= 'F') {
                    val <<= 4;
                    val |= (((ch - 'A') + 10) & 0xFL);
                } else if (ch >= 'a' && ch <= 'f') {
                    val <<= 4;
                    val |= (((ch - 'a') + 10) & 0xFL);
                }
            }
        }
        return val;
    }

    public static String valueToMac(long mac) {
        final StringBuilder builder = new StringBuilder(6 * 2 + 5);
        int i = 0;
        while (true) {
            int b = (int) ((mac >> 40) & 0xFFL);
            i++;
            mac <<= 8;
            builder.append(HEX_ALPHABET[b >> 4])
                    .append(HEX_ALPHABET[b & 0xF]);
            if (i < 6) {
                builder.append(':');
                continue;
            }
            break;
        }
        return builder.toString();
    }

    public static String changeMacAddress(String address, int delta) {
        if (address == null) return "00:00:00:00:00:00";
        final long macValue = macToValue(address);
        long leastByteOfMac = macValue & 0xFFL;
        long otherByteOfMac = macValue & 0xFFFF_FFFF_FF00L;
        leastByteOfMac = (leastByteOfMac + delta) & 0xFFL;
        final long newMacValue = otherByteOfMac | leastByteOfMac;
        return valueToMac(newMacValue);
    }

    private MacUtils() {}
}
