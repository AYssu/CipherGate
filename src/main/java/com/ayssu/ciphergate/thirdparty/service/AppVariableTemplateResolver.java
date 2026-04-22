package com.ayssu.ciphergate.thirdparty.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AppVariableTemplateResolver {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^{}]+)}");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter STANDARD_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DEPTH = 5;
    private static final String RAND_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public String resolve(String raw, AppVariableTemplateContext ctx) {
        if (!StringUtils.hasText(raw) || raw.indexOf('$') < 0) {
            return raw;
        }
        AppVariableTemplateContext safeCtx = ctx == null ? new AppVariableTemplateContext() : ctx;
        String resolved = raw;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Matcher matcher = TEMPLATE_PATTERN.matcher(resolved);
            if (!matcher.find()) {
                break;
            }
            matcher.reset();
            StringBuffer out = new StringBuffer();
            while (matcher.find()) {
                String expr = matcher.group(1);
                String replacement = resolveExpression(expr, safeCtx);
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(out);
            String next = out.toString();
            if (next.equals(resolved)) {
                break;
            }
            resolved = next;
        }
        return resolved;
    }

    private String resolveExpression(String expr, AppVariableTemplateContext ctx) {
        if (!StringUtils.hasText(expr)) {
            return "";
        }
        String normalized = expr.trim();
        int defaultSep = indexOfTopLevel(normalized, '|');
        if (defaultSep > 0) {
            String left = normalized.substring(0, defaultSep).trim();
            String fallback = normalized.substring(defaultSep + 1);
            String value = resolveExpression(left, ctx);
            return StringUtils.hasText(value) ? value : fallback;
        }
        String fnValue = resolveFunction(normalized, ctx);
        if (fnValue != null) {
            return fnValue;
        }
        return switch (normalized) {
            case "app.id" -> asString(ctx.getAppId());
            case "app.key" -> asString(ctx.getAppKey());
            case "user.id" -> asString(ctx.getUserId());
            case "user.username" -> asString(ctx.getUsername());
            case "user.member_expires_at" -> asIso(ctx.getMemberExpiresAt());
            case "ws.conn_id" -> asString(ctx.getWsConnId());
            case "ws.connected_at" -> asString(ctx.getWsConnectedAtEpochMs());
            case "ws.online_seconds" -> asString(resolveOnlineSeconds(ctx));
            case "client.ip" -> asString(ctx.getClientIp());
            case "device.id" -> asString(ctx.getDeviceId());
            case "device.name" -> asString(ctx.getDeviceName());
            case "device.os" -> asString(ctx.getDeviceOs());
            case "user.login_count" -> asString(ctx.getUserLoginCount());
            case "user.last_login_at" -> asIso(ctx.getUserLastLoginAt());
            case "user.last_login_ip" -> asString(ctx.getUserLastLoginIp());
            case "user.login_count+1" -> asString(resolveLoginCountPlusOne(ctx.getUserLoginCount()));
            default -> "${" + expr + "}";
        };
    }

    private String resolveFunction(String expr, AppVariableTemplateContext ctx) {
        if ("time".equals(expr)) {
            return String.valueOf(Instant.now().getEpochSecond());
        }
        if ("time_ms".equals(expr)) {
            return String.valueOf(Instant.now().toEpochMilli());
        }
        if ("now".equals(expr)) {
            return Instant.now().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER);
        }
        if ("uuid".equals(expr)) {
            return UUID.randomUUID().toString();
        }
        ParsedFunction fn = parseFunction(expr);
        if (fn == null) {
            return null;
        }
        return switch (fn.name()) {
            case "date" -> fn.args().size() == 1 ? formatNow(evalArg(fn.args().get(0), ctx)) : null;
            case "datetime" -> fn.args().size() == 1 ? formatNow(evalArg(fn.args().get(0), ctx)) : null;
            case "unix" -> fn.args().size() == 1 ? shiftUnix(evalArg(fn.args().get(0), ctx)) : null;
            case "rand.int" -> resolveRandInt(fn.args(), ctx);
            case "rand.str" -> fn.args().size() == 1 ? randomString(parseInt(evalArg(fn.args().get(0), ctx), 16)) : null;
            case "uuid" -> fn.args().isEmpty() ? UUID.randomUUID().toString() : null;
            case "nonce" -> fn.args().size() == 1 ? randomString(parseInt(evalArg(fn.args().get(0), ctx), 16)) : null;
            case "upper" -> fn.args().size() == 1 ? evalArg(fn.args().get(0), ctx).toUpperCase(Locale.ROOT) : null;
            case "lower" -> fn.args().size() == 1 ? evalArg(fn.args().get(0), ctx).toLowerCase(Locale.ROOT) : null;
            case "trim" -> fn.args().size() == 1 ? evalArg(fn.args().get(0), ctx).trim() : null;
            case "substr" -> resolveSubstr(fn.args(), ctx);
            case "replace" -> resolveReplace(fn.args(), ctx);
            case "if" -> resolveIf(fn.args(), ctx);
            case "sha256" -> fn.args().size() == 1 ? sha256(evalArg(fn.args().get(0), ctx)) : null;
            case "base64" -> fn.args().size() == 1 ? base64(evalArg(fn.args().get(0), ctx)) : null;
            case "urlencode" -> fn.args().size() == 1 ? URLEncoder.encode(evalArg(fn.args().get(0), ctx), StandardCharsets.UTF_8) : null;
            default -> null;
        };
    }

    private static String formatNow(String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.now().format(formatter);
    }

    private static String shiftUnix(String offsetExpr) {
        long offset = parseLong(offsetExpr, 0L);
        long now = Instant.now().getEpochSecond();
        return String.valueOf(now + offset);
    }

    private String resolveRandInt(List<String> args, AppVariableTemplateContext ctx) {
        if (args.size() != 2) {
            return null;
        }
        int min = parseInt(evalArg(args.get(0), ctx), 0);
        int max = parseInt(evalArg(args.get(1), ctx), 0);
        if (max < min) {
            int t = min;
            min = max;
            max = t;
        }
        return String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    private String resolveSubstr(List<String> args, AppVariableTemplateContext ctx) {
        if (args.size() != 3) {
            return null;
        }
        String base = evalArg(args.get(0), ctx);
        int begin = parseInt(evalArg(args.get(1), ctx), 0);
        int end = parseInt(evalArg(args.get(2), ctx), base.length());
        begin = Math.max(0, Math.min(begin, base.length()));
        end = Math.max(begin, Math.min(end, base.length()));
        return base.substring(begin, end);
    }

    private String resolveReplace(List<String> args, AppVariableTemplateContext ctx) {
        if (args.size() != 3) {
            return null;
        }
        String base = evalArg(args.get(0), ctx);
        String oldVal = evalArg(args.get(1), ctx);
        String newVal = evalArg(args.get(2), ctx);
        return base.replace(oldVal, newVal);
    }

    private String resolveIf(List<String> args, AppVariableTemplateContext ctx) {
        if (args.size() != 3) {
            return null;
        }
        boolean cond = evalCondition(args.get(0), ctx);
        return cond ? evalArg(args.get(1), ctx) : evalArg(args.get(2), ctx);
    }

    private boolean evalCondition(String raw, AppVariableTemplateContext ctx) {
        String cond = raw == null ? "" : raw.trim();
        String[] ops = new String[]{">=", "<=", "==", "!=", ">", "<"};
        for (String op : ops) {
            int idx = cond.indexOf(op);
            if (idx > 0) {
                String left = evalArg(cond.substring(0, idx), ctx);
                String right = evalArg(cond.substring(idx + op.length()), ctx);
                Double ln = tryParseDouble(left);
                Double rn = tryParseDouble(right);
                if (ln != null && rn != null) {
                    return compareNumber(ln, rn, op);
                }
                return compareString(left, right, op);
            }
        }
        return Boolean.parseBoolean(evalArg(cond, ctx));
    }

    private static boolean compareNumber(double l, double r, String op) {
        return switch (op) {
            case ">" -> l > r;
            case "<" -> l < r;
            case ">=" -> l >= r;
            case "<=" -> l <= r;
            case "==" -> Double.compare(l, r) == 0;
            case "!=" -> Double.compare(l, r) != 0;
            default -> false;
        };
    }

    private static boolean compareString(String l, String r, String op) {
        int cmp = l.compareTo(r);
        return switch (op) {
            case "==" -> l.equals(r);
            case "!=" -> !l.equals(r);
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    private static ParsedFunction parseFunction(String expr) {
        int left = expr.indexOf('(');
        if (left <= 0 || !expr.endsWith(")")) {
            return null;
        }
        String name = expr.substring(0, left).trim();
        String argsBody = expr.substring(left + 1, expr.length() - 1);
        List<String> args = splitTopLevelArgs(argsBody);
        return new ParsedFunction(name, args);
    }

    private static List<String> splitTopLevelArgs(String body) {
        List<String> args = new ArrayList<>();
        if (!StringUtils.hasText(body)) {
            return args;
        }
        int start = 0;
        int depth = 0;
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if ((c == '\'' || c == '"') && (i == 0 || body.charAt(i - 1) != '\\')) {
                if (!inQuote) {
                    inQuote = true;
                    quote = c;
                } else if (quote == c) {
                    inQuote = false;
                }
            }
            if (inQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                args.add(body.substring(start, i).trim());
                start = i + 1;
            }
        }
        args.add(body.substring(start).trim());
        return args;
    }

    private static int indexOfTopLevel(String s, char target) {
        int depth = 0;
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '\'' || c == '"') && (i == 0 || s.charAt(i - 1) != '\\')) {
                if (!inQuote) {
                    inQuote = true;
                    quote = c;
                } else if (quote == c) {
                    inQuote = false;
                }
            }
            if (inQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == target && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private String evalArg(String rawArg, AppVariableTemplateContext ctx) {
        if (rawArg == null) {
            return "";
        }
        String arg = rawArg.trim();
        if ((arg.startsWith("'") && arg.endsWith("'")) || (arg.startsWith("\"") && arg.endsWith("\""))) {
            return arg.substring(1, arg.length() - 1);
        }
        String resolved = resolveExpression(arg, ctx);
        if (resolved.equals("${" + arg + "}")) {
            return arg;
        }
        return resolved;
    }

    private static int parseInt(String s, int defaultVal) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return defaultVal;
        }
    }

    private static long parseLong(String s, long defaultVal) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return defaultVal;
        }
    }

    private static Double tryParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String randomString(int len) {
        int size = Math.max(1, Math.min(len, 256));
        StringBuilder sb = new StringBuilder(size);
        ThreadLocalRandom rd = ThreadLocalRandom.current();
        for (int i = 0; i < size; i++) {
            sb.append(RAND_CHARS.charAt(rd.nextInt(RAND_CHARS.length())));
        }
        return sb.toString();
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Long resolveOnlineSeconds(AppVariableTemplateContext ctx) {
        if (ctx.getWsOnlineSeconds() != null) {
            return Math.max(0L, ctx.getWsOnlineSeconds());
        }
        Long connectedAt = ctx.getWsConnectedAtEpochMs();
        if (connectedAt == null || connectedAt <= 0) {
            return null;
        }
        long sec = (Instant.now().toEpochMilli() - connectedAt) / 1000L;
        return Math.max(0L, sec);
    }

    private static Integer resolveLoginCountPlusOne(Integer loginCount) {
        int base = loginCount == null ? 0 : loginCount;
        return base + 1;
    }

    private static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String asIso(java.time.LocalDateTime dt) {
        if (dt == null) {
            return "";
        }
        return dt.format(STANDARD_DATETIME_FORMATTER);
    }

    private record ParsedFunction(String name, List<String> args) {
    }
}
