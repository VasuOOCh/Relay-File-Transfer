import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class RelayClient implements Runnable {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String clientId;
    private static Scanner sc = new Scanner(System.in);
    private boolean running = false;

    public static void main(String[] args) {
        System.out.println("Type the server address to connect");
        String serverAdd = sc.nextLine();

        RelayClient client = new RelayClient();
        client.connect(serverAdd, 8080);
        client.running = true;
        //  we want to start a thread to listen for incoming messages
        new Thread(client).start();

        System.out.println("Type SEND <targetId> to send a file or EXIT to leave connection");

        while(true) {
            String cmd = sc.nextLine();

            if(cmd.startsWith("SEND")) {
                String[] parts = cmd.split(" ");
                String targetId = parts[1];
                client.sendFile(client.selectFile(), targetId);
            } else if (cmd.equals("EXIT")) {
                System.out.println("Closing connection...");
                try {
                    client.socket.close();
                    client.running = false;
//                    sc.close();
                    System.exit(0);
                }
                catch (IOException e) {
                    e.getMessage();
                    e.printStackTrace();
                }
            }else {
                System.out.println("Type a valid command");
            }

        }
    }


    public void connect(String serverAdd, int PORT) {
        try {
            socket = new Socket(serverAdd.trim(), PORT);;
            System.out.println("Connection made with server "+ serverAdd);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            String msg =in.readUTF();
            if(msg.startsWith("ID:")) {
                String[] parts = msg.split(" ");
                clientId = parts[1];
                System.out.println("My client ID : " + clientId);
            }

        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while(running) {
            try {
                String cmd = in.readUTF();
                if(cmd.startsWith("RECEIVING")) {
                    String[] parts = cmd.split(" ");
                    String filename = parts[1];
                    long fileSize = Long.parseLong(parts[2]);
                    System.out.println("Receiving file "+ filename + " size : " + fileSize + " bytes");

                    receiveFile(fileSize, filename);
                }else if(cmd.startsWith("NEW_CLIENT")) {
                    String[] parts = cmd.split(" ");
                    String clientId = parts[1];
                    System.out.println("New client connected to server : " + clientId);
                }else if(cmd.startsWith("CLIENT")) {
                    String[] parts = cmd.split(" ");
                    String clientId = parts[1];
                    System.out.println("Available client: " + clientId);
                }
            }
            catch (IOException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public File selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(null);
        if(result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }else {
            return null;
        }
    }

    public void sendFile(File file, String targetId) {
        try {

            if(file == null) {
                System.out.println("Select a valid file");
                return;
            }

            FileInputStream fileInputStream = new FileInputStream(file);
            out.writeUTF("SEND "+ targetId.trim() + " " + file.getName());
            out.writeLong(file.length());

            byte[] b = new byte[8192]; // external buffer size set to 8KB
            long remaining = file.length();

            while(remaining > 0) {
                int read = fileInputStream.read(b, 0, (int)Math.min(b.length, remaining));
                out.write(b, 0 ,read);
                remaining -= read;
            }

            System.out.println("File sent successfully");

        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public void receiveFile(long fileSize, String filename) {
        try {
            File file = new File(filename);
            FileOutputStream fileOutputStream = new FileOutputStream(file);

            long remaining = fileSize;
            byte[] b = new byte[8192];

            while (remaining > 0) {
                int read = in.read(b, 0, (int)Math.min(b.length, remaining));
                fileOutputStream.write(b,0,read);
                remaining -= read;
            }

            System.out.println("File received successfully");
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
