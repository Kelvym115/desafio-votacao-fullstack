package br.com.db.votacao.shared;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

public final class ConstraintErrors {
    private ConstraintErrors() { }

    public static boolean isConstraint(Throwable exception, String expected) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
