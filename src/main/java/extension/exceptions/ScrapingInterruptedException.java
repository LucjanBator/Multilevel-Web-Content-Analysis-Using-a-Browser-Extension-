package extension.exceptions;

public class ScrapingInterruptedException extends RuntimeException {
    public ScrapingInterruptedException(Exception message) {
        super(message);
    }
    public ScrapingInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}