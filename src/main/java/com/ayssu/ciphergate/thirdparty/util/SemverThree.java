package com.ayssu.ciphergate.thirdparty.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 三方约定的三段数字版本号 {@code x.x.x}（每段为非负整数，无 v 前缀）。
 */
public final class SemverThree {

    private static final Pattern THREE = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private SemverThree() {
    }

    public static boolean isThreePartNumeric(String s) {
        if (s == null) {
            return false;
        }
        return THREE.matcher(s.trim()).matches();
    }

    /**
     * @return 负数表示 a &lt; b，0 相等，正数 a &gt; b
     */
    public static int compare(String a, String b) {
        int[] pa = parseRequired(a);
        int[] pb = parseRequired(b);
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(pa[i], pb[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static int[] parseRequired(String s) {
        Matcher m = THREE.matcher(s.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("not x.x.x: " + s);
        }
        return new int[]{
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3))
        };
    }
}
