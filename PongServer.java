package pongserver;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class PongServer {
    static GameState gs;
    static JTextArea log;
    
    public static void main(String[] args) {
        // build server log window
        JFrame frame = new JFrame("Server Log");
        frame.setSize(350, 250);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                if (gs == null) return;
                if (gs.players[0] != null) gs.players[0].send("DISC Server shutting down");
                if (gs.players[1] != null) gs.players[1].send("DISC Server shutting down");
            }
        });
        log = new JTextArea();
        log.setEditable(false);
        JScrollPane scroller = new JScrollPane();
        scroller.setViewportView(log);
        frame.add(scroller);
        frame.setVisible(true);
        
        // set up the server
        String portStr = JOptionPane.showInputDialog(frame, "Enter a port number");
        ServerSocket servSock = null;
        try {
            servSock = new ServerSocket(Integer.parseInt(portStr)); 
            logInfo("Server started on port " + portStr);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Failed to bind port " + portStr, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        } catch (IllegalArgumentException ex2) {
            JOptionPane.showMessageDialog(frame, "'" + portStr + "' is not a valid port", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        
        // initialze data, start up game thread
        gs = new GameState();
        new GameHandler().start();
        
        // listen for client connections
        while (true) {
            try {
                SocketWrapper sock = new SocketWrapper(servSock.accept());
                String message = sock.read();
                if (message.substring(0,4).equals("JOIN")) {
                    String name = message.substring(5);
                    synchronized (gs) {
                        if (gs.playersConnected == 0) {
                            gs.setupPlayer(sock, name, 1);
                        } else if (gs.playersConnected == 1) {
                            gs.setupPlayer(sock, name, 2);
                            // send names to each player to trigger start of game
                            gs.players[0].send("USRS\n" + gs.playerNames[0] + "\n" + gs.playerNames[1]);
                            gs.players[1].send("USRS\n" + gs.playerNames[0] + "\n" + gs.playerNames[1]);
                            logInfo("Starting new game");
                            // start ball 2 seconds later
                            gs.startBall(2000);
                        } else if (gs.playersConnected == 2) {
                            sock.send("FULL");
                            sock.close();
                            logInfo("Blocked connection from '" + name + "' since the lobby is full");
                        }
                    }
                } else {
                    sock.close();
                    logInfo("Blocked connection with invalid initial message");
                }
            } catch (IOException ex) { 
                logInfo("Socket I/O error: " + ex.toString()); 
            }
        }
    }
    
    static void logInfo(String msg) {
        log.append(msg + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
    
    static class SocketWrapper {
        Socket sock;
        BufferedReader sin;
        PrintStream sout;
        
        public SocketWrapper(Socket newSock) throws IOException {
            sock = newSock;
            sin = new BufferedReader(new InputStreamReader(newSock.getInputStream()));
            sout = new PrintStream(newSock.getOutputStream());
        }
        
        public void send(String msg) {
            sout.println(msg);
        }
        
        public void send(Object msgObj) {
            sout.println(msgObj);
        }
        
        public String read() throws IOException {
            return sin.readLine();
        }
        
        public void close() {
            try { sock.close(); } 
            catch (IOException ex) { logInfo("Error closing socket"); }
        }
    }
    
    static class Vector2 {
        public double x;
        public double y;
        
        public Vector2(double newX, double newY) {
            x = newX;
            y = newY;
        }
        
        public static Vector2 fromAngle(double angle) {
            return new Vector2(Math.cos(angle), Math.sin(angle));
        }
        
        public static Vector2 add(Vector2 first, Vector2 second) {
            return new Vector2(first.x + second.x, first.y + second.y);
        }
        
        public static Vector2 mult(Vector2 vec, double scal) {
            return new Vector2(vec.x * scal, vec.y * scal);
        }
    }
    
    static class GameState {
        // threads
        public PlayerHandler[] playerHandlers = new PlayerHandler[2];
        // connection stuff
        public SocketWrapper[] players = new SocketWrapper[2];
        public boolean[] resetFlags = new boolean[2];
        public String[] playerNames = new String[2];
        public int playersConnected = 0;
        // game stuff
        public int winner = 0;
        public int leftPaddleY = 225;
        public int rightPaddleY = 225;
        public int[] playerScores = new int[2];
        public Vector2 ballPos = new Vector2(225, 225);
        public Vector2 ballDir = new Vector2(0, 0);
        public double ballSpeed = 4;
        public boolean paused;
        
        public void resetGame() {
            ballPos.x = 225;
            ballPos.y = 225;
            ballDir.x = 0;
            ballDir.y = 0;
            ballSpeed = 4;
        }
        
        public void setupPlayer(SocketWrapper sock, String name, int which) throws IOException {
            playersConnected++;
            players[which-1] = sock;
            playerNames[which-1] = name;
            sock.send("SUCC " + which);
            playerHandlers[which-1] = new PlayerHandler(sock, which);
            playerHandlers[which-1].start();
            logInfo("Player " + which + " connected with name '" + playerNames[which-1] + "'");
        }
        
        public void startBall(int delay) {
            new Thread(){
                @Override
                public void run() {
                    // wait for the specified amount of ms
                    try { Thread.sleep(delay); }
                    catch (InterruptedException ex) {}
                    // generate random angle and launch the ball
                    double angle = new Random().nextDouble(2*Math.PI);
                    while (Math.abs(Math.sin(angle)) > 0.8)
                        angle = new Random().nextDouble(2*Math.PI);
                    ballDir = Vector2.fromAngle(angle);
                }
            }.start();
        }
        
        public boolean hitsLeftPaddle() {
            return gs.ballPos.x <= 35 
                && gs.ballPos.x > 15 
                && leftPaddleY > ballPos.y - 60 
                && leftPaddleY < ballPos.y + 60;
        }
        
        public boolean hitsRightPaddle() {
            return gs.ballPos.x >= 415 
                && gs.ballPos.x < 425 
                && rightPaddleY > ballPos.y - 60 
                && rightPaddleY < ballPos.y + 60;
        }
        
        @Override
        public String toString() {
            return String.format("GAME %d %d %d %d %d %d", 
                    leftPaddleY, rightPaddleY, (int)ballPos.x, (int)ballPos.y, playerScores[0], playerScores[1]);
        }
    }
    
    static class GameHandler extends Thread {
        @Override
        public void run() {
            while (true) {
                synchronized (gs) {
                    if (gs.playersConnected == 2) {
                        // if both reset flags are active, restart the game
                        if (gs.resetFlags[0] && gs.resetFlags[1]) {
                            gs.resetFlags = new boolean[2];
                            gs.playerScores = new int[2];
                            gs.paused = false;
                            gs.winner = 0;
                            gs.resetGame();
                            gs.startBall(1000);
                            gs.players[0].send(gs);
                            gs.players[1].send(gs);
                            logInfo("Restarting game");
                        }
                        if (gs.paused) continue;
                        // move ball (in steps of 3, to prevent clipping at high speeds)
                        double motionLeft = gs.ballSpeed;
                        while (motionLeft > 0) {
                            if (motionLeft > 3) {
                                checkBounces();
                                gs.ballPos = Vector2.add(gs.ballPos, Vector2.mult(gs.ballDir, 3));
                                motionLeft -= 3;
                            } else {
                                checkBounces();
                                gs.ballPos = Vector2.add(gs.ballPos, Vector2.mult(gs.ballDir, motionLeft));
                                motionLeft = 0;
                            }
                        }
                        // detect when someone scores a point
                        if (gs.ballPos.x < -10) {
                            gs.playerScores[1]++;
                            gs.resetGame();
                            if (gs.playerScores[1] == 10) gs.winner = 1;
                            else gs.startBall(0);
                        } else if (gs.ballPos.x > 460) {
                            gs.playerScores[0]++;
                            gs.resetGame();
                            if (gs.playerScores[0] == 10) gs.winner = 2;
                            else gs.startBall(0);
                        }
                        if (!gs.paused && gs.winner != 0) {
                            gs.paused = true;
                            logInfo("Player " + gs.winner + " wins");
                        }
                    }
                }
                try { Thread.sleep(10); }
                catch (InterruptedException ex) {}
            }
        }
        
        void checkBounces() {
            if (gs.ballDir.x < 0 && gs.hitsLeftPaddle()) {
                platformBounce(1);
            } else if (gs.ballDir.x > 0 && gs.hitsRightPaddle()) {
                platformBounce(2);
            } else if ((gs.ballPos.y <= 8 && gs.ballDir.y < 0) || (gs.ballPos.y >= 442 && gs.ballDir.y > 0)) {
                gs.ballDir.y *= -1;
            }
        }
        
        public void platformBounce(int which) {
            gs.ballDir.x *= -1;
            double offset = gs.ballPos.y - (which == 1 ? gs.leftPaddleY : gs.rightPaddleY);
            double angle = Math.atan2(gs.ballDir.y, gs.ballDir.x);
            // calculate angle adjustment based on distance from paddle center
            if (Math.abs(offset) < 12) {
                Random r = new Random();
                angle += Math.PI/6 * (r.nextDouble(2) - 1);
            } else if (Math.abs(offset) < 27) {
                angle += Math.PI/5 * Math.signum(offset) * (which == 1 ? 1 : -1);
            } else if (Math.abs(offset) < 44) {
                angle += Math.PI/4 * Math.signum(offset) * (which == 1 ? 1 : -1);
            } else {
                angle += Math.PI/3 * Math.signum(offset) * (which == 1 ? 1 : -1);
            }
            // only actually change the angle if it's not too extreme
            if (Math.abs(Math.sin(angle)) < 0.9) {
                gs.ballDir = Vector2.fromAngle(angle);
            }
            // increase ball speed with each bounce
            gs.ballSpeed += 0.1;
        }
    }
    
    static class PlayerHandler extends Thread {
        public boolean shutdown;
        SocketWrapper sock;
        int playerNum;
        
        public PlayerHandler(SocketWrapper newSock, int newNum) throws IOException {
            sock = newSock;
            playerNum = newNum;
        }
        
        public void run() {
            try {
                String messageRaw; 
                while ((messageRaw = sock.read()) != null) {
                    synchronized (gs) {
                        if (shutdown) {
                            sock.close();
                            return;
                        }
                        switch (messageRaw.substring(0,4)) {
                            case "MOUS":
                                // update paddle position
                                int newPaddleY = Integer.parseInt(messageRaw.substring(5));
                                if (playerNum == 1) gs.leftPaddleY = newPaddleY;
                                else if (playerNum == 2) gs.rightPaddleY = newPaddleY;
                                // update client with new info (or end the game)
                                if (gs.winner == 0) sock.send(gs);
                                else sock.send("WINS " + gs.playerNames[gs.winner - 1]);
                                break;
                            case "RSET":
                                logInfo("Player " + playerNum + " wants to restart");
                                gs.resetFlags[playerNum - 1] = true;
                                break;
                            case "DISC":
                                logInfo("Player " + playerNum + " disconnected");
                                closeConnections();
                                return;
                            default:
                                logInfo("Unexpected message from player " + playerNum + ": '" + messageRaw + "'");
                                break;
                        }
                    }
                    try { Thread.sleep(10); }
                    catch (InterruptedException ex) { }
                }
            } catch (IOException ex) {
                logInfo("Lost connection to player " + playerNum);
                synchronized (gs) { closeConnections(); }
            }
        }
        
        void closeConnections() {
            logInfo("Shutting down lobby");
            // if the other thread exists, tell it to shut itself down
            int otherNum = playerNum == 1 ? 1 : 0;
            if (gs.playerHandlers[otherNum] != null) {
                gs.players[otherNum].send("DISC Opponent left the game");
                gs.playerHandlers[otherNum].shutdown = true;
            }
            // close your own socket
            gs.players[playerNum-1].close(); 
            // fully reset the game state
            gs = new GameState();
        }
    }
}
