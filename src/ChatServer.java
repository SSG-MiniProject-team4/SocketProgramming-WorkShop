import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ChatServer {
    private static final int PORT = 5001;

    private static final ExecutorService pool = Executors.newCachedThreadPool();

    private static final Map<String, CLientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("[Server] port:" + PORT + " 에서 서버 실행");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[서버] 종료를 시작합니다...");
            broadcast("SYSTEM: 서버가 곧 종료됩니다.");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pool.shutdownNow();
            System.out.println("[서버] 서버 강제 종료.");
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = serverSocket.accept();
                    pool.submit(new CLientHandler(socket));
                } catch (IOException e) {
                    System.out.println("[Server] client연결 오류" + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("[Server] 포트" + PORT + " not listen");
        } finally {
            pool.shutdown();
        }

    }

    private static void broadcast(String message) {
        for(CLientHandler client : clients.values()){
            client.sendMessage(message);
        }
        System.out.println("[전체 메세지]" + message);
    }

    private static class CLientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String nickname;

        public CLientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try{
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // 닉네임 설정
                setupNickname();
                if(nickname == null) return;

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/")) {
                        handleCommand(message);
                    } else {
                        broadcast("[" + nickname + "] " + message);
                    }
                }

            } catch(IOException e){
                System.out.println(e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void setupNickname() throws IOException{
            sendMessage("NICK <이름> ");
            String nickCommand = in.readLine();

            if (nickCommand == null || !nickCommand.toUpperCase().startsWith("NICK ")) {
                sendMessage("ERR 잘못된 명령어입니다. 'NICK <닉네임>'으로 시작해야 합니다. 연결을 종료합니다.");
                return;
            }

            String potentialNickname = nickCommand.substring(5).trim();
            // 닉네임 유효성 검사 (공백, 중복)
            if (potentialNickname.isEmpty() || potentialNickname.contains(" ")) {
                sendMessage("ERR 닉네임은 비어있거나 공백을 포함할 수 없습니다. 연결을 종료합니다.");
                return;
            }

            synchronized (clients) {
                if (clients.containsKey(potentialNickname)) {
                    sendMessage("ERR 이미 사용 중인 닉네임입니다. 연결을 종료합니다.");
                    return;
                }
                this.nickname = potentialNickname;
                clients.put(nickname, this);
            }
            sendMessage("OK 닉네임이 '" + nickname + "'으로 설정되었습니다.");
            broadcast(nickname + "님이 채팅에 참여했습니다.");
            System.out.println("[서버] " + socket.getRemoteSocketAddress() + " 님이 '" + nickname + "' 닉네임으로 접속했습니다.");

        }

        private void handleCommand(String command) {
            if ("/quit".equalsIgnoreCase(command)) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.getMessage();
                }
            } else if ("/who".equalsIgnoreCase(command)) {
                // /who 명령어 처리
                String userList = "현재 접속자: " + String.join(", ", clients.keySet());
                sendMessage(userList);
            } else {
                sendMessage("ERR 알 수 없는 명령어: " + command);
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        private void cleanup() {
            if (nickname != null) {
                clients.remove(nickname);
                broadcast(nickname + "님이 채팅을 떠났습니다.");
                System.out.println("[서버] " + nickname + "님의 연결이 끊어졌습니다.");
            }
            try {
                socket.close();
            } catch (IOException e) {
                // 무시
            }
        }

    }

}
