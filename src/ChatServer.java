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
   // private static final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();
    private static final Map<String, ClientHandler> clients = Collections.synchronizedMap(new HashMap<>());
    //브로드캐스트로 채팅을 전달하기 위해 사용한 synchronizedMap

    public static void main(String[] args) {
        System.out.println("[Server] Starting on port " + PORT);

        //Ctrl+C 발생 시 스레드 풀 강제 종료해서 자원을 해제함
        terminate();

        try(ServerSocket serverSocket = new ServerSocket(5000)){
            while(true){//여러 client들의 socket을 받으니 while문으로 accept 시켜주기
                Socket socket = serverSocket.accept();
                //System.out.println("[Server] Client "+ id+ " connected from "+ socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket);
                //clientHandler를 이용하여 socket 처리
                POOL.submit(handler);
            }
        } catch (IOException e) {
            System.out.println("[Server] I/O Exception occurs.");
            terminate();
        }
    }
    //runnable은 thread에서 실행할 작업을 정의하기 위한 interface이므로 run을 overriding 해주기
    private static class ClientHandler implements Runnable{
        private final Socket socket;
        private String nickName;
        private PrintWriter out;//broadcast할 때 접속해있는 모든 client에게 메시지를 보내야하므로 선언
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        //채팅 기능 + 나머지 명령어를 한번에 수행하기 위한 BlockingQueue

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            boolean flag = true;
            Thread outputThread = null;
            //명령어 처리와 관계없이 client A가 채팅을 입력하면 broadcast로 채팅을 보내야하니 output을 thread 처리
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),true)){
                this.out = out;
                //nickname 얻어내서 중복 처리
                String nickname = in.readLine();
                nickname = nickname.trim();

                synchronized (clients) {// 한번에 한 client만 clients에 접근하도록하여 nickname 넣기
                    if(!checkNickname(nickname)){
                        out.println("ERR: You are not allowed(nickname is already existed or forbidden)");
                        out.flush();
                        socket.close();
                        flag = false; // finally에서 socket만 닫으려고... left 안나오게끔
                        return;//닉네임을 잘못입력하면 socket을 닫아버림으로써 명령어 기능 실행 못하게 하기
                    }

                    //nickname이 조건에 맞으면 set에 nickname 추가
                    nickName = nickname;
                    System.out.println(nickname + " joined");
                    out.println("OK: Welcome " + nickname + "!");
                    clients.put(nickname, this);
                }

                //chatting과 명령어 처리를 진행하기 위해서는 새로운 thread를 통해 멀티 태스킹 해줘야함
                outputThread =  new Thread(() -> {
                    try{
                        while(!socket.isClosed()){
                            String msg = messages.take(); //queue에 들어있는 메시지를 차례대로 client에 write
                            out.println(msg);
                        }
                    } catch(InterruptedException e){} //스레드 종료
                });
                outputThread.start();

                Outter:
                while(true) {
                    messages.offer("Please enter your command.");
                    String command = in.readLine();//명령어

                    if (!command.startsWith("/") || command == null) {
                        out.println("ERR: Invalid command."); //명령어에 대한 server의 resp1
                        continue; //명령어 format에 안맞으면 다시 명령어 받기
                    }

                    String[] commandList = command.split(" ");//w nickname message를 구분하기 위해..
                    StringBuilder message = new StringBuilder();//message는 split된 것을 다시 합쳐야하므로
                    for (int i = 2; i < commandList.length; i++) {
                        message.append(commandList[i]);
                        message.append(" ");//공백유지해서 합치기
                    }

                    commandList[0] = commandList[0].substring(1); // 명령어에서 /제거하고 뽑아내기
                    switch (commandList[0].toLowerCase()) {
                        case "quit" -> {quit();  break Outter;}
                        case "who" -> printNickNames();
                        case "w" -> sendMessage(commandList[1], message);
                        default -> out.println("ERR: Invalid command"); //명령어에 대한 server의 resp2
                    }

                }

            } catch (IOException e) {
                System.out.println("[Server] I/O Exception occurs.");
                terminate();
            }finally{ //quit 입력시 -> 정상종료 | 비정상 종료는 terminate()를 main에서 호출하게끔 되어있음
                try {socket.close();} catch(IOException e){ System.err.println(e.getMessage());}
                if(flag && nickName != null){ //닉네임이 정상적일 때만
                    clients.remove(nickName);
                    System.out.println(nickName + " left");
                }
            }
        }

        private boolean checkNickname(String nickname){
            if(nickname == null || nickname.trim().isEmpty()) return false;
            synchronized (clients) {//nickname을 check할 때는 check+return이라는 복합 연산을 진행하므로
                // 동기화 상태로 check 해줘야함
                return !clients.containsKey(nickname);
            }
        }

        private void quit() {
            out.println("Quitting"); //명령어에 대한 server의 resp3
            clients.remove(nickName);
        }

        private void printNickNames() {
            StringBuilder sb = new StringBuilder("USERS: "); //접속한 client를 string 형태로 출력하기 위해
            synchronized (clients) { //iterator가 multithread에서 안전하지 않기 때문에 동기화
                for(String user: clients.keySet()){
                    sb.append(user).append(", ");
                }
                sb.delete(sb.length()-2, sb.length());//마지막 , 지우기
            }
            messages.offer(sb.toString());//queue에 넣어주면 client에 알아서 write됨
             //명령어에 대한 server의 resp4
        }

        private void sendMessage(String nickname, StringBuilder message) {
            if(nickname.isEmpty() || !nickName.equals(nickname.trim()) || message.isEmpty()){
                out.println("ERR: Your message cannot be broadcasting. Please enter your nickname and message");
                return;
            }
            //브로드캐스트로 모든 client에 message 전달해야함
            System.out.println(nickname + "broadcasting.");
           synchronized (clients) {//clients를 사용할 때 동기화해서 message를 넣어줘야 client에서의 다른 명령어 처리를
               //멈춰두고 메시지를 broadcast할 수 있음
               for (ClientHandler handler : clients.values()) {
                   if(handler.out != null){
                       handler.messages.offer("[" + nickName + "] " + message.toString()); //명령어에 대한 server의 resp5
                   }
               }
           }
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
