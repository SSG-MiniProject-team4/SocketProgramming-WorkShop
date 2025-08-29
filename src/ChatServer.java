
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private static final int PORT = 5000;
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static ConcurrentHashMap<String, PrintWriter> map = new ConcurrentHashMap<>();
    private static List<String> nickList = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        System.out.println("[서버] 서버 시작: " + PORT);
        // Ctrl+C 시 스레드 풀 정리
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[서버] 종료하는 중...");
            POOL.shutdownNow();
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String nickname = br.readLine();
                if (map.containsKey(nickname)) {
                    pw.println("ERR: 중복되는 이름입니다.");
                    socket.close();
                } else if (nickname.isBlank()) {
                    pw.println("ERR: 이름을 입력하여 주십시오.");
                    socket.close();
                } else if (nickname.contains(" ")) {
                    pw.println("ERR: 공백은 허용되지 않습니다.");
                    socket.close();
                } else {
                    pw.println(nickname + " joined");
                    POOL.submit(new ClientHandler(socket, nickname));
                }
            }

        } catch (IOException e) {
            System.err.println("[서버] 에러 발생: " + e.getMessage());
        } finally {
            POOL.shutdownNow();
            Runtime.getRuntime().exit(0);
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final String nickname;

        ClientHandler(Socket socket, String nickname) {
            this.socket = socket;
            this.nickname = nickname;
            System.out.println(nickname + " connected");
        }

        @Override
        public void run() {
            broadcast(nickname + " joined");
            try (
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter pw = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
            ) {
                map.put(nickname, pw);
                nickList.add(nickname);

                String contents;
                Chat:
                while ((contents = br.readLine()) != null) {
                    switch (contents) {
                        case "/quit" -> {
                            System.out.println(nickname + " disconnected");
                            break Chat;
                        }
                        case "/who" -> {
                            String str = String.join(", ", nickList);
                            pw.println("USERS " + str);
                        }
                        default -> {
                            broadcast("[" + nickname + "] " + contents);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("ERR: " + nickname + " | " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                map.remove(nickname);
                nickList.remove(nickname);
                broadcast(nickname + " left");
            }

        }
    }
    // 전체 채팅 기능
    private static synchronized void broadcast(String str) {
        for (PrintWriter pw : map.values()) {
            pw.println(str);
        }
    }
}
