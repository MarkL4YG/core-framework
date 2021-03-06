package de.fearnixx.jeak.teamspeak.except;

@SuppressWarnings("squid:S1165")
public class ConsistencyViolationException extends RuntimeException {

    private transient Object sourceObject = null;

    public ConsistencyViolationException() {
    }

    public ConsistencyViolationException(String message) {
        super(message);
    }

    public ConsistencyViolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConsistencyViolationException(Throwable cause) {
        super(cause);
    }

    public ConsistencyViolationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public ConsistencyViolationException setSourceObject(Object sourceObject) {
        this.sourceObject = sourceObject;
        return this;
    }

    public Object getSourceObject() {
        return sourceObject;
    }
}
