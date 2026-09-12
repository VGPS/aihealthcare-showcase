package com.wgblackmon.aihealthcare.domain.service;

import java.text.MessageFormat;

/**
 * JDK-only logging facade for domain-layer classes.
 *
 * <p>Wraps {@link System.Logger} and accepts SLF4J-style {@code {}} placeholders,
 * converting them to {@link MessageFormat}-style {@code {0}, {1}, ...} indices
 * before delegating to the JDK logger. This lets domain services stay free of
 * Lombok and SLF4J imports while keeping the same log-call syntax used
 * everywhere else in the project.
 *
 * <p>Throwable detection mirrors SLF4J behaviour: if the last argument is a
 * {@link Throwable} and there are more arguments than placeholders, the last
 * argument is attached as the exception rather than formatted into the message.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-11
 * @updated 2026-09-11
 */
public final class DomainLogger {

    private final System.Logger logger;

    public DomainLogger(Class<?> clazz) {
        this.logger = System.getLogger(clazz.getName());
    }

    public void debug(String msg, Object... args) {
        emit(System.Logger.Level.DEBUG, msg, args);
    }

    public void info(String msg, Object... args) {
        emit(System.Logger.Level.INFO, msg, args);
    }

    public void warn(String msg, Object... args) {
        emit(System.Logger.Level.WARNING, msg, args);
    }

    public void error(String msg, Object... args) {
        emit(System.Logger.Level.ERROR, msg, args);
    }

    private void emit(System.Logger.Level level, String msg, Object[] args) {
        if (!logger.isLoggable(level)) {
            return;
        }

        if (args == null || args.length == 0) {
            logger.log(level, msg);
            return;
        }

        int placeholderCount = countPlaceholders(msg);

        if (args.length > placeholderCount
                && args[args.length - 1] instanceof Throwable) {
            Throwable thrown = (Throwable) args[args.length - 1];
            if (placeholderCount == 0) {
                logger.log(level, msg, thrown);
            } else {
                Object[] formatArgs = new Object[args.length - 1];
                System.arraycopy(args, 0, formatArgs, 0, formatArgs.length);
                String formatted = MessageFormat.format(toIndexed(msg), formatArgs);
                logger.log(level, formatted, thrown);
            }
        } else {
            logger.log(level, toIndexed(msg), args);
        }
    }

    static String toIndexed(String template) {
        StringBuilder sb = new StringBuilder(template.length() + 16);
        int idx = 0;
        int i = 0;
        while (i < template.length()) {
            if (i + 1 < template.length()
                    && template.charAt(i) == '{'
                    && template.charAt(i + 1) == '}') {
                sb.append('{').append(idx++).append('}');
                i += 2;
            } else {
                sb.append(template.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private static int countPlaceholders(String template) {
        int count = 0;
        int i = 0;
        while (i < template.length() - 1) {
            if (template.charAt(i) == '{' && template.charAt(i + 1) == '}') {
                count++;
                i += 2;
            } else {
                i++;
            }
        }
        return count;
    }
}
