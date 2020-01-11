import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

public class HttpServer {
  private final Socket socket;

  public static Thread threadForConnection(Socket socket) {
    HttpServer server = new HttpServer(socket);
    return new Thread(server::connect);
  }

  private HttpServer(Socket socket) {
    this.socket = socket;
  }

  private void connect() {
    BufferedReader in = null;
    PrintWriter printWriter = null;
    BufferedOutputStream outputStream = null;
    try {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      printWriter = new PrintWriter(socket.getOutputStream());
      outputStream = new BufferedOutputStream(socket.getOutputStream());

      handle(in, printWriter, outputStream);
    } catch (IOException e) {
      System.err.printf("%s - server error: %s\n", new Date(), e);
    } finally {
      try {
        in.close();
        printWriter.close();
        outputStream.close();
        socket.close();
      } catch (Exception e) {
        System.err.printf("%s - couldn't close stream: %s\n", new Date(), e.getMessage());
      }
    }
  }

  private void handle(BufferedReader in, PrintWriter printWriter,
      BufferedOutputStream outputStream) throws IOException {
    StringTokenizer tokenizer = new StringTokenizer(in.readLine());
    String method = tokenizer.nextToken().toUpperCase();

    if ("GET".equals(method)) {
      System.out.printf("%s - Request OK.\n", new Date());
      writeHeader(printWriter, Http.ResponseCode.OK);
    } else {
      System.out.printf("%s - %s Not Implemented.\n", new Date(), method);
      writeHeader(printWriter, Http.ResponseCode.NOT_IMPLEMENTED);
    }
  }

  private static void writeHeader(PrintWriter printWriter, Http.ResponseCode responseCode) {
    printWriter.printf("HTTP/1.1 %s\n", responseCode);
    printWriter.println("Server: JonKimbel/CatFeeder HttpServer");
    printWriter.printf("Date: %s\n", new Date());
    printWriter.println("Content-type: text/html");
    printWriter.println("Content-length: 0");
    printWriter.println();
    printWriter.flush();
  }
}
