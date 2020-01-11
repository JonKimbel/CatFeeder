public interface Http {
  public enum ResponseCode {
    OK("200 OK"),
    NOT_IMPLEMENTED("501 Not Implemented"),

    ;

    private final String stringRepresentation;

    private ResponseCode(String stringRepresentation) {
      this.stringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
      return stringRepresentation;
    }
  }
}
