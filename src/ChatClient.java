import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class ChatClient {
    public static void main(String[] args) throws IOException{
        if (args.length != 3) {
            System.out.println("사용법: java ChatClient <서버_IP> <포트> <닉네임>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String nickname = args[2];

        try (Socket socket = new Socket(host, port)) {
            System.out.println("채팅 서버에 연결되었습니다 (" + host + ":" + port + ")");

            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            Thread readerThread = new Thread(new ServerMessageReader(socket));
            readerThread.start();

            out.println("NICK " + nickname);

            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String userInput;
            while ((userInput = consoleReader.readLine()) != null) {
                out.println(userInput);
                if ("/quit".equalsIgnoreCase(userInput.trim())) {
                    break;
                }
            }
            readerThread.join();

        } catch (UnknownHostException e) {
            System.err.println("호스트 " + host + "를 찾을 수 없습니다.");
        } catch (IOException e) {
            System.err.println(host + "에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("클라이언트가 중단되었습니다.");
        } finally {
            System.out.println("서버와의 연결이 종료되었습니다.");
        }
    }

    private static class ServerMessageReader implements Runnable {
        private final Socket socket;
        private BufferedReader in;

        public ServerMessageReader(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    System.out.println(serverMessage);
                }
            } catch (IOException e) {
                System.out.println("서버와의 연결이 끊어졌습니다.");
            } finally {
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }
}
