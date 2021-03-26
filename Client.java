/*
 * This project was developed by @Multifactored.
 * Contact me about it if you want, supposed to be a private program for messaging.
 * 
 * Threaded messaging application, Client component
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Client extends Thread{
    //Placeholder vars, these should be passed in by server
    static int block_duration = 10000; //default
    static int timeout = 10000; //default
    static DatagramSocket clientSocket;
    static boolean shouldExit = false;
    static String username = null;

	public static void main(String[] args) throws Exception {
        if(args.length != 2){
            System.out.println("Usage: java client localhost PortNo");
            System.exit(1);
        }
        
        InetAddress IPAddress = InetAddress.getByName(args[0]);
        int serverPort = Integer.parseInt(args[1]);
		
		// create UDP datagram socket which connects to server
		clientSocket = new DatagramSocket();
  
        //vars
        byte[] sendData;
        DatagramPacket sendPacket;

        //First, we need to log in.
        System.out.println("Welcome to Delta's Chat Application! Please login.");
        int allowedAttempts = 3;
        for (int attempts = 1; attempts <= allowedAttempts; attempts++){
            username = attemptLogin(attempts, IPAddress, serverPort, clientSocket);
            if (username != null) break;
            if (attempts == allowedAttempts){
                System.out.printf("%d failed attempts reached! You have been blocked temporarily.\n", allowedAttempts);
                //stub, proper block duration
                Thread.sleep((long)block_duration);
                allowedAttempts += 3;
            }
        }
        System.out.println("Welcome, "+username+", to Delta's wack chat!");

        //Start thread that listens for server responses.
        Client us = new Client();
        us.start();

        //This loop listens for user input and commands.
        while (true){
            String[] data = null;
            String command;

            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            try{
                command = inFromUser.readLine();  
                command = command.trim();
                if (command.isEmpty()) continue;
            }
            catch (IOException e){
                e.printStackTrace();
                return;
            }

            //Command statements
            if (command.equals("logout")){
                //This needs to send a log out thing to server todo Placeholder
                String[] logoutData = new String[1];
                logoutData[0] = "logout";
                data = logoutData;
                shouldExit = true;
                System.out.println("Thanks for using this alpha chatbot!");
            }
            else if (command.equals("whoelse")){
                //stub
            }
            //All other commands have multiple arguments that may or may not need to be split.
            else{
                //Each command goes here, options to handle differently.
                if (command.startsWith("broadcast ")){
                    //We split by " " and return at most 2 elements.
                    String[] broadcastData;
                    broadcastData = command.split(" ", 2);
                    data = new String[3];
                    data[0] = broadcastData[0];
                    data[1] = broadcastData[1];
                    data[2] = username;
                }
                else if (command.startsWith("Etc")){
                    //stub
                }
                else {
                    System.out.println("Client Error: Invalid Command");
                }
            }
            //Send the packet only if used
            sendData = convertToBytes(data);
            if (sendData != null){
                sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, serverPort);
                try {
                    clientSocket.send(sendPacket);
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
            //Exits program
            if (shouldExit){
                clientSocket.close();
                break;
            }
        }
    }

    //Thread listening for server response. Prints whatever it recieves.
    public void run(){
        try {
            while(!interrupted()){
                if (shouldExit) {
                    interrupt();
                    throw new InterruptedException();
                }
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData,receiveData.length);
                System.out.println("Waiting to recieve.");
                try{
                    clientSocket.receive(receivePacket);
                }
                catch (IOException e){
                   System.out.println("Listener closing! Goodbye!");
                }
                String[] reply = convertToStrings(receivePacket.getData());
                //Avoid self broadcasts. This is redundant rn
                if (reply[0].trim().equals(username+" logged in") == false) {
                    System.out.println(reply[0]);
                }
            }
        }
        catch (InterruptedException e){
            //let thread exit
        }
    } 
    public void cancel() {interrupt();}

    public static String attemptLogin(int tryNum, InetAddress IPAddress, int serverPort, DatagramSocket clientSocket){
        String[] authPair = new String[3];

        // get input from keyboard to login, username first
        System.out.printf("Please type in your username.\n");
        System.out.printf("Username: ");
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        try{
            authPair[1] = inFromUser.readLine();  
        }
        catch (IOException e){
            e.printStackTrace();
        }

        // we now send password next.
        System.out.printf("Password: ");
        inFromUser = new BufferedReader(new InputStreamReader(System.in));
        try {
            authPair[2] = inFromUser.readLine(); 
        }
        catch (IOException e){
            e.printStackTrace();
        }
            
        byte[] sendData = new byte[1024];
        authPair[0] = "LoginAttempt";

        //Send the packet
        sendData = convertToBytes(authPair);
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, serverPort);
        try {
            clientSocket.send(sendPacket);
        }
        catch (IOException e){
            e.printStackTrace();
        }

        //wait to recieve, remove the commenting later
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData,receiveData.length);
        try{
            clientSocket.receive(receivePacket);
        }
        catch (IOException e){
            e.printStackTrace();
        }

        //Timeout and block data is sent from server for every login attempt.
        String[] data = convertToStrings(receivePacket.getData());
        block_duration = Integer.parseInt(data[1].trim());
        timeout = Integer.parseInt(data[2].trim());
        if (data[0].equals("Successful Login") ){
            System.out.printf("Successful authentication of %s\n", authPair[0]);
            return authPair[1];
        }
        else {
            System.out.println(data[0]);
        }
        return null;
    }
    
    //If any of these two are edited, must edit the other
    // ` special char.
    private static String[] convertToStrings(byte[] byteStrings) {
        String[] data = new String[byteStrings.length];
        String concatString = new String(byteStrings);
        data = concatString.split("`");
        return data;
    }
    
    //Each line is terminated by '`' special char.
    private static byte[] convertToBytes(String[] strings) {
        if (strings == null) return null;
        byte[] data = new byte[1024];
        String totalString = strings[0];
        for (int i = 1; i < strings.length; i++) {
            totalString = totalString + '`';
            totalString = totalString.concat(strings[i]);
        }
        data = totalString.getBytes();
        return data;
    }
    
}
