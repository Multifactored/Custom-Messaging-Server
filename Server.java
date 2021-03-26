/*
 * This project was developed by @Multifactored.
 * Contact me about it if you want, supposed to be a private program for messaging.
 * 
 * Threaded messaging application, server component
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;
import java.text.SimpleDateFormat;
import java.util.concurrent.locks.*;

public class Server extends Thread{

    //List of current connected clients that are broadcasting.
    static List<SocketAddress> clients = new ArrayList<SocketAddress>();
    //List of logged in clients with the date of when they logged in. Keeps all online users. Checks 4 timeout.
    static HashMap<SocketAddress, String> loggedInClientsMap = new HashMap<SocketAddress, String>();
    //Lists the username connected to the socket address.
    static HashMap<SocketAddress, String> usernameSocketMap = new HashMap<SocketAddress, String>();
    //List of all clients who have ever logged in, with the time of their last log in.
    //When a client logs in, their entry is either placed (if new) or updated,Username,Time
    static HashMap<String, String> historyClientMap = new HashMap<String, String>();
    //Stores user credientials. Ooh, security threat.
    static HashMap<String, String> credientialPairs = new HashMap<String, String>();
    //Stores offline messages to be forwarded. Username, Message.
    static HashMap<String, String> offlineMessages = new HashMap<String, String>();
    //Stores the blacklisted users, both from server and from user\nuser.
    static ArrayList<String> blacklistedUsers = new ArrayList<String>();

    static PriorityQueue<String> broadcastQueue = new PriorityQueue<String>();
    static PriorityQueue<String[]> messageQueue = new PriorityQueue<String[]>();

    static DatagramSocket serverSocket;
    static int UPDATE_INTERVAL = 1000; //milliseconds
    static ReentrantLock syncLock = new ReentrantLock();
    
	public static void main(String[] args)throws Exception {

        //Checking correct args
        if (args.length != 3){
            System.out.println("Usage: java server port block_duration timeout");
            return;
        }

        //Loading in credientials.
        parseCredientials();

        //Assign Port number
        int serverPort = Integer.parseInt(args[0]);
        serverSocket = new DatagramSocket(serverPort);
        String blockDuration = args[1];
        String timeout = args[2];
        System.out.printf("Server is hosted on %d. Ready: \n", serverPort);
        
        //prepare buffers
        String[] data = null;
        byte[] receiveData = null;
        SocketAddress sAddr;
        //Start the other sending thread on Server.
        Server us = new Server();
        us.start();
        
        //Main listener socket
        while (true){
            String[] serverMessage = null;
            //receive UDP datagram
            receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            
            //get data, convert to strings
            data = convertToStrings(receivePacket.getData());
            System.out.println("RECEIVED REQUEST: " + data[0]);
            //get lock
            syncLock.lock();
            sAddr = receivePacket.getSocketAddress();

            //Commands
            //Make attempted login. Count state is kept clientside.
            if(data[0].startsWith("LoginAttempt")){
                System.out.println("Attempted Login made by\nUsername: " + data[1]);
                System.out.println("Password: " + data[2]);
                //sleep(1000);

                boolean success = tryLogin(data[1], data[2], sAddr);
                serverMessage = new String[3];
                if (success) serverMessage[0] = "Successful Login";
                else serverMessage[0] = "Failed Login";
                serverMessage[1] = blockDuration;
                serverMessage[2] = timeout;
            }
            else if(data[0].trim().equals("logout")){
                serverMessage = new String[1];
                if (clients.contains(sAddr)){
                    //broadcast
                    broadcastQueue.add(usernameSocketMap.get(sAddr)+" logged out");
                    //All three need to be updated on log out.
                    clients.remove(sAddr);
                    loggedInClientsMap.remove(sAddr);
                    usernameSocketMap.remove(sAddr);
                    serverMessage[0] = "User has successfully logged out.";
                }
                else{
                    serverMessage[0] = "Error! User not found.";
                }
            }
            else if (data[0].equals("broadcast")){
                serverMessage = new String[1];
                //construct the message
                //Need to vary on block.
                String broadcastMessage = data[2]+": "+data[1];
                broadcastQueue.add(broadcastMessage);
                serverMessage[0] = "Successful broadcast!";
            }
            else if (data[0].equals("message")){
                //Check if the dst user actually exists.
                if (credientialPairs.containsKey(data[1].trim())){
                    //Placeholder: Message to show if user has been blocked. Block checking goes here.

                    //construct the message to put in queue.
                    //[0] - Recieving User, [1] - Message pre-appended.
                    String[] message = new String[2];
                    message[0] = data[1].trim();
                    String sendingUser = usernameSocketMap.get(sAddr);
                    message[1] = sendingUser+": "+data[2].trim();
                    messageQueue.add(message);
                }
                else{
                    serverMessage = new String[1];
                    serverMessage[0] = "Error: User does not exist.";
                }
            }
            else{
                serverMessage = new String[1];
                serverMessage[0] = "Error: Server unknown command.";
            }
            
            //prepare to send reply back if any
            byte[] sendData = new byte[1024];
            sendData = convertToBytes(serverMessage);
            if (sendData != null){
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, sAddr);
                serverSocket.send(sendPacket);
                System.out.println("Sent Packet!");
            }

            //This should be the main
            //System.out.println(Thread.currentThread().getName());
            syncLock.unlock();
        } // end of while (true)
        
    } // end of main()
    
// broadcasting thread
    public void run(){
        while(true){
            syncLock.lock();
            /*for (int j=0; j < clients.size();j++){
                long millis = System.currentTimeMillis();
                Date date_time = new Date(millis);
                String message= "Current time is " + date_time;
                sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clients.get(j));
                try{
                    serverSocket.send(sendPacket);
                } catch (IOException e){ }
                String clientInfo =clients.get(j).toString();
                //Not printing the leading /
                System.out.println("Sending time to " + clientInfo.substring(1) + " at time " + date_time);
            }
            //release lock*/
            //Check message queue
            if (!messageQueue.isEmpty()){
                //We assume user exists, but check if online.
                //Possible offline solution: Check if online, keep attempting to send here.
                //Cycle through!
                PriorityQueue<String[]> newMessageQueue = new PriorityQueue<String[]>();
                while (!messageQueue.isEmpty()){
                    String[] message = messageQueue.remove();
                    SocketAddress sAddr = null;
                    //usernameSocketMap maps sAddr to user, and only if online.
                    if (getKeyByValue(usernameSocketMap, message[0]) != null){
                        //We assume user is not blocked, this check happens earlier. 
                        sAddr = getKeyByValue(usernameSocketMap, message[0]);
                        byte[] sendData = new byte[1024];
                        String[] sendingMessage = new String[1];
                        sendingMessage[0] = message[1];
                        sendData = convertToBytes(sendingMessage);
                        if (sendData != null){
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, sAddr);
                            try {
                                serverSocket.send(sendPacket);
                            } catch (IOException e) { }
                        }
                        else System.out.println("Big Error in sending messages.");
                        System.out.println("Sending message to "+message[0]);
                    }
                    else{
                        newMessageQueue.add(message);
                    }
                }
                messageQueue = newMessageQueue;
            }
            
            //Check broadcast queue
            if (!broadcastQueue.isEmpty()){
                String[] message = new String[1];
                message[0] = broadcastQueue.remove();
                byte[] sendData = new byte[1024];
                sendData = convertToBytes(message);
                for (int i = 0; i < clients.size(); i++){
                    //Needs to check if blacklisted.
                    //Check if self sending. Uncomment this.
                    String clientUser = usernameSocketMap.get(clients.get(i));
                    if (message[0].startsWith(clientUser)) continue;
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clients.get(i));
                    try {
                        serverSocket.send(sendPacket);
                    } catch (IOException e) { }
                    String clientInfo = clients.get(i).toString();
                    System.out.println("Sending broadcast to "+clientInfo.substring(1));
                }
            }

            syncLock.unlock();
            //sleep for UPDATE_INTERVAL
            try{
                Thread.sleep(UPDATE_INTERVAL);//in milliseconds
            } catch (InterruptedException e){
                System.out.println(e);
            }
        // System.out.println(Thread.currentThread().getName());
        }
    } 

    //Attempt a login, check blacklist. Does not create the blacklist.
    public static boolean tryLogin(String user, String pass, SocketAddress sAddr){
        //Check blacklist
        if (!blacklistedUsers.isEmpty()){
            if (blacklistedUsers.contains(user)) return false;
        }

        //Check if currently logged in
        if (!loggedInClientsMap.isEmpty()){
            if (loggedInClientsMap.containsValue(user)) return false;
        }

        //check username exists
        if (credientialPairs.containsKey(user) == false) return false;
        //check credientials valid
        else if (credientialPairs.get(user).trim().equals(pass.trim())){
            System.out.println("Correct password! Logging in "+user);
            broadcastQueue.add(user+" logged in");
            try { 
                Thread.sleep((long)200);
            } catch (InterruptedException e) { }
            clients.add(sAddr);
            loggedInClientsMap.put(sAddr, "Placeholder Time");
            usernameSocketMap.put(sAddr, user);
            //Put in historyclientmap, check if exists already by removing
            historyClientMap.remove(user);
            historyClientMap.put(user, "PlaceholderTime");
            return true;
        }
        else{
            System.out.println("Incorrect Password "+pass+" to dictionary "+credientialPairs.get(user));
        } 
        return false;
    }

    public static void parseCredientials(){
        File credientials = new File("credentials.txt");
        if (credientials.exists() == false){
            //PLACEHOLDER, may need to remove on submission.
            System.out.println("No credientials file loaded.");
            return;
        }
        BufferedReader reader = null;
        try{
            reader = new BufferedReader(new FileReader(credientials));
            String line = null;
            while ((line = reader.readLine()) != null){
                String[] pair = line.split(" ");
                credientialPairs.put(pair[0], pair[1]);
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }
        finally{
            try {
                if (reader != null) reader.close();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    //If any of these two are edited, must edit the other
    private static String[] convertToStrings(byte[] byteStrings) {
        String[] data = new String[byteStrings.length];
        String concatString = new String(byteStrings);
        data = concatString.split("`");
        return data;
    }
    
    //Each line is terminated by '`' special char.
    private static byte[] convertToBytes(String[] strings) {
        if (strings == null) return null;
        //If there's a null error use this line
        //System.out.printf("Strings has %d strings\n", strings.length);
        byte[] data = new byte[1024];
        String totalString = strings[0];
        for (int i = 1; i < strings.length; i++) {
            totalString = totalString + '`';
            totalString = totalString.concat(strings[i]);
        }
        data = totalString.getBytes();
        return data;
    }

    public static <T, E> T getKeyByValue(Map<T, E> map, E value){
        for (Entry<T, E> entry : map.entrySet()){
            if (Objects.equals(value, entry.getValue())){
                return entry.getKey();
            }
        }
        return null;
    }
}