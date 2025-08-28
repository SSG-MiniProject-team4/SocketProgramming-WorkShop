package src;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChatClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        try(Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))){

            System.out.println("[Client] connected successfully! host: "+host+" port: "+port);

            System.out.print("[Client] NICK ");
            String nickname = keyboard.readLine();
            writer.println(nickname);


        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
