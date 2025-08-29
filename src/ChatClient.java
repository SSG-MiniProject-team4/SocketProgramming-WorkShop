import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClient {
    private static final String host = "127.0.0.1";
    private static final int port = 5000;

    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket(host, port);
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter pw = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader keyboard = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))
        ) {
            // 닉네임 등록
            System.out.print("NICK ");
            String str = keyboard.readLine();
            pw.println(str);

            String rspn = br.readLine();
            if ((rspn == null) || rspn.startsWith("ERR")) {
                System.out.println(rspn);
                socket.close();
                return;
            } else {
                System.out.println(rspn);
                ServerListener serverListener = new ServerListener(socket);
                new Thread(serverListener).start();
            }

            // 클라 -> 서버 채팅
            String msg;
            Chat:
            while (true) {
                msg = keyboard.readLine();
                switch (msg) {
                    case "" -> {
                        System.out.println("ERR: 다시 입력해 주십시오.");
                    }
                    case "/quit" -> {
                        pw.println(msg);
                        System.out.println("프로그램이 종료됩니다.");
                        break Chat;
                    }
                    case "/who" -> {
                        pw.println(msg);
                    }
                    default -> {
                        pw.println(msg);
                    }
                }

            }
        } catch (IOException e) {
            System.err.println("ERR : 문제가 발생하였습니다." + e.getMessage());
        }

    }
    static class ServerListener implements Runnable {
        private Socket socket;

        public ServerListener(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // 서버 -> 클라 채팅
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));){
                String resp;
                while ((resp = br.readLine()) != null) {
                    System.out.println(resp); // 서버로부터의 메시지 출력
                }
                System.out.println("서버가 종료되었습니다. 프로그램을 종료합니다.");
            } catch (IOException e) {
                System.out.println("서버와의 연결이 끊어졌습니다.");
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                Runtime.getRuntime().exit(0);
            }
        }
    }
}
