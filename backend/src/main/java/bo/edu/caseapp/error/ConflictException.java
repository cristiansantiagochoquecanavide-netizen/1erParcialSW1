package bo.edu.caseapp.error;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
