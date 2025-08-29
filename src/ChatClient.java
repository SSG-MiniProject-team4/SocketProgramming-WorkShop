package src;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ChatClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        try(Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))){

            //일단 server와 연결이 잘되었는지 우선적으로 확인해주기
            System.out.println("[Client] connected successfully! host: "+host+" port: "+port);

            //닉네임 받기
            System.out.print("[Client] NICK ");
            String nickname = keyboard.readLine();
            writer.println(nickname); //닉네임 유효성 처리는 server에서 처리해줌

            String message = in.readLine(); //유효성 검사에 대한 server의 대답
            if(message != null){
                System.out.println(message);
                if(!message.startsWith("OK:")){
                    System.out.println("Exiting...");
                    return; // 닉네임부터 잘못적었으니
                }
            }

            //client가 server로부터 오는 메시지를 읽고 console에 바로 출력하기 위해
            //chatting과 명령어 처리를 하기 위해서
            Thread outputThread = new Thread(() -> {
               try {
                   String line;
                   while((line = in.readLine())!= null){
                       synchronized (System.out){
                           System.out.println("\n" + line);
                       }
                   }
               } catch(IOException e){}
            });
            outputThread.setDaemon(true);//main끝나면 자동으로 thread close되도록
            outputThread.start();


            while(true) {
                String command = keyboard.readLine();//명령어 keyboard로 받아서 서버로 보내기
                writer.println(command);

                if(command == null) break; // EOF 일때 예외처리

                //server로부터 오는 메시지를 outputThread를 통해 처리하므로 여기에 따로 적을 필요 없음
                if("/quit".equalsIgnoreCase(command.trim())) break;
            }

            System.out.println("[Client] Goodbye!"); //client 종료하기 전 인사
        } catch (UnknownHostException e) {
            System.err.println("[Client] Unknown host: " + host);
        } catch (IOException e) {
            System.err.println("[Client] I/O error: " + e.getMessage());
        }
    }
}
