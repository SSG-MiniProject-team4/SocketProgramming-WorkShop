package src;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatServer {
    private static final int PORT = 5000;
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final AtomicInteger CLIENT_COUNT = new AtomicInteger(1);
    private static Set<String> nicknames = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        System.out.println("[Server] Starting on port " + PORT);

        //Ctrl+C 발생 시 스레드 풀 강제 종료해서 자원을 해제함
        terminate();

        try(ServerSocket serverSocket = new ServerSocket(5000)){
            while(true){
                Socket socket = serverSocket.accept();
                int id = CLIENT_COUNT.getAndIncrement();
                System.out.println("[Server] Client "+ id+ " connected from "+ socket.getRemoteSocketAddress());
                POOL.submit(new ClientHandler(socket, id));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ClientHandler implements Runnable{
        private final Socket socket;
        private final int ClientId;
        private String nickName;

        public ClientHandler(Socket socket, int ClientId) {
            this.socket = socket;
            this.ClientId = ClientId;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))){

                //nickname 얻어내서 중복 처리
                String nickname = in.readLine();
                if(!checkNickname(nickname)){
                    out.println("ERR: You are not allowed(nickname is already existed or forbidden)");
                    System.out.println("[Server] Client " + ClientId + " disconnected.");
                    try {socket.close();} catch (IOException e) {}
                    return;
                }

                //nickname이 조건에 맞으면 set에 nickname 추가
                System.out.println(nickname + " joined");
                out.println("OK: Welcome " + nickname + "!");
                nickName = nickname;
                nicknames.add(nickname);

                while(true){
                    out.println("What do you wanted to do?");
                    String command = in.readLine();

                    if(!command.startsWith("/")){
                        out.println("ERR: Invalid command.");
                        continue;
                    }

                    String[] commandList = command.split(" ");
                    StringBuilder message = new StringBuilder();
                    for(int i = 2; i <commandList.length; i++){
                        message.append(commandList[i]);
                    }

                    commandList[0] = commandList[0].substring(1); // 명령어에서 /제거하고 뽑아내기
                    switch(commandList[0].toLowerCase()){
                        case "quit" -> quit();
                        case "who" -> printNickNames(out);
                        case "w" -> sendMessage(commandList[1], message);
                        default -> out.println("ERR: Invalid command");
                    }

                }




            } catch (IOException e) {
                throw new RuntimeException(e);
            }finally{
                try {socket.close();} catch(IOException e){}
                System.out.println(nickName + " left");
            }
        }

        private boolean checkNickname(String nickname){
            if(nickname == null || nickname.isEmpty()) return false;
            return nicknames.contains(nickname);
        }

        private void quit(){
            nicknames.remove(nickName);
            try {socket.close();} catch (IOException e) {}
        }

        private void printNickNames(PrintWriter out) {
            Iterator<String> iter = nicknames.iterator();
            out.print("USERS ");
            while(iter.hasNext()){
                System.out.print(iter.next()+", ");
            }
            out.println();
        }

        private void sendMessage(String nickname, StringBuilder message){
            if(nickname.isEmpty() || !nickName.equals(nickname.trim()) || message.isEmpty()){
                //예외처리
            }
            //브로드캐스트로 모든 client에 message 전달해야함
        }
    }

    //반복될만한 code method로 빼놓기
    public static void terminate(){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            POOL.shutdownNow();
        }));
    }
}
