import java.io.IOException;
import java.net.ServerSocket;
import java.util.Date;

public class App {
  private static final int PORT = 8080;

  public static void main(String[] args) throws IOException {
    // TODO: take port as an argument.
    ServerSocket socket = new ServerSocket(PORT);
    System.out.printf("%s - listening on port %s\n", new Date(), PORT);

    while (true) {
      Thread thread = HttpServer.threadForConnection(socket.accept());
      System.out.printf("%s - connection opened\n", new Date());
      thread.start();
    }
  }
}
