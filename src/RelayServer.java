import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RelayServer {
    private static final int PORT = 8080;
    public static Map<String,ConnectionHandler> clients = new ConcurrentHashMap<>();
    // we use ConcurrentHashMaps for better Thread compatibility and scalability
    public static void main(String[] args) {
        try(ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Connection established");
            while(true) {

                System.out.println("Looking for clients...");

                // for listening to the client connections
                Socket clientSocket = serverSocket.accept();
                String clientId = UUID.randomUUID().toString();

                // we need to start a thread for the connected client

                System.out.println("Client "+ clientId +" connected");

                ConnectionHandler connectionHandler = new ConnectionHandler(clientSocket, clientId);
                clients.put(clientId,connectionHandler);

                // Send Client its client ID
                connectionHandler.sendMessage("ID: " + clientId);

                // inform the other clients about new connection :
                for (Map.Entry<String, ConnectionHandler> set : clients.entrySet()) {
                    if(!Objects.equals(set.getKey(), clientId)) {
                        ConnectionHandler c = set.getValue();
                        c.sendMessage("NEW_CLIENT "+ clientId);
                    }
                }

                // informing the newly connected client about all other existing connections
                for (Map.Entry<String, ConnectionHandler> set : clients.entrySet()) {
                    if(!Objects.equals(set.getKey(), clientId)) {
                        connectionHandler.sendMessage("CLIENT " + set.getKey());
                    }
                }
                System.out.println("Informed the other clients");

                // What is connectionHandler : It is a class for creating objects for individual
                // clients to handle them and also a runnable for a thread, which will be initialized for
                // each client

                new Thread(connectionHandler).start();
            }
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

}

class ConnectionHandler implements Runnable {
    private Socket clientSocket;
    private String clientId;
    private DataInputStream in;
    private DataOutputStream out;

    public ConnectionHandler(Socket clientSocket, String clientId) {
        this.clientSocket = clientSocket;
        this.clientId = clientId;
        try {
            in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        //  this method will send a message to this connected client
        try{
            out.writeUTF(message);
            out.flush();
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        // here we will listen for file sending commands from the client
        // command : SEND <clientId> <Filename>
        try {
            while(true) {
                String cmd = in.readUTF();
                if(cmd.startsWith("SEND")) {
                    String[] parts = cmd.split(" ");
                    String targetId = parts[1];
                    String filename = parts[2];

                    // Reading file data
                    long fileSize = in.readLong();
                    sendFile(targetId, filename, fileSize);
                }
            }
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendFile(String targetId, String filename, long fileSize) {
        try {
            ConnectionHandler targetConnection = RelayServer.clients.get(targetId);
            if(targetConnection == null ) {
                sendMessage("ERROR Target_Client_Not_Found");
                return;
            }

            targetConnection.sendMessage("RECEIVING "+ filename + " " + fileSize);

            byte[] buffer = new byte[8192];
            long remaining = fileSize;

            while (remaining > 0) {
                int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                targetConnection.out.write(buffer, 0, read);
                remaining -= read;
            }
            targetConnection.out.flush();
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}