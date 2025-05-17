package pongclient;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import javax.swing.*;

public class PongClient {
    // connection stuff
    static SocketWrapper sock;
    // game stuff
    static GamePanel panel;
    static String[] playerScores = new String[2];
    static String[] playerNames = new String[2];
    static String localName;
    static int whichPlayer = 0;
    static int leftPaddleY = 225;
    static int rightPaddleY = 225;
    static int mouseY;
    static int ballX = 225;
    static int ballY = 225;
    static int status;
    
    public static void main(String[] args) {
        // build the window
        JFrame frame = new JFrame("Multi-Pong");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                if (sock != null) {
                    sock.send("DISC");
                    disconnect();
                }
                System.exit(0);
            }
        });
        panel = new GamePanel();
        panel.setPreferredSize(new Dimension(450, 450));
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);
        
        // add mouse detection
        frame.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                if (status == 0) {
                    int realX = e.getX() - 6;
                    int realY = e.getY() - 30;
                    if (realX >= 140 && realX <= 310 && realY >= 270 && realY <= 330) {
                        startGame();
                    }
                }
            }
        });
        frame.addMouseMotionListener(new MouseMotionAdapter(){
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseY = e.getY() - 30;
                if (status % 2 == 1) panel.repaint();
            }
        });
    }
    
    public static void startGame() {
        // popup to prompt user for name, IP, and port
        JTextField usernameField = new JTextField(10);
        JTextField ipAddrField = new JTextField(10);
        JTextField portField = new JTextField(5);
        JPanel connectPanel = new JPanel();
        connectPanel.setLayout(new BoxLayout(connectPanel, BoxLayout.Y_AXIS));
        connectPanel.add(new JLabel("Username:"));
        connectPanel.add(usernameField);
        connectPanel.add(new JLabel("Server IP:"));
        connectPanel.add(ipAddrField);
        connectPanel.add(new JLabel("Server Port:"));
        connectPanel.add(portField);
        int result = JOptionPane.showConfirmDialog(panel, connectPanel, "Connect to a server", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION) {
            return;
        }
        localName = usernameField.getText();
        
        // attempt to connect with the provided credentials
        try {
            sock = new SocketWrapper(new Socket(ipAddrField.getText(), Integer.parseInt(portField.getText())));
            sock.send("JOIN " + localName);
            String response = sock.read();
            if (response.substring(0,4).equals("SUCC")) {
                status = 1;
                whichPlayer = Integer.parseInt(response.substring(5));
                panel.repaint();
                new GameThread().start();
            } else if (response.equals("FULL")) {
                disconnect();
                popupWarning("Server already has two connected players");
            } else {
                disconnect();
                popupError("Unexpected response from server: '" + response + "'");
            }  
        } catch (IOException ex) {
            disconnect();
            popupError("Error connecting to server: " + ex.toString());
        } catch (NumberFormatException ex2) {
            disconnect();
            popupError("Port must be a number");
        }
    }
    
    public static void popupError(String msg) {
        JOptionPane.showMessageDialog(panel, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    public static void popupWarning(String msg) {
        JOptionPane.showMessageDialog(panel, msg, "Warning", JOptionPane.WARNING_MESSAGE);
    }
    
    public static void disconnect() {
        if (sock != null) {
            // close the socket
            sock.close();
            // reset client-side game info
            leftPaddleY = 225;
            rightPaddleY = 225;
            ballX = 225;
            ballY = 225;
            // load start screen
            status = 0;
            panel.repaint();
        }
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
            catch (IOException ex) { System.out.println("Error closing socket"); }
        }
    }
    
    static class GameThread extends Thread {
        @Override
        public void run() {
            try {
                String messageRaw;
                while ((messageRaw = sock.read()) != null) {
                    switch (messageRaw.substring(0,4)) {
                        case "USRS":
                            // recieve player names as next two lines
                            playerNames = new String[2];
                            playerNames[0] = sock.read();
                            playerNames[1] = sock.read();
                            sock.send("MOUS " + mouseY);
                            break;
                        case "GAME":
                            // send mouse position to server
                            sock.send("MOUS " + mouseY);
                            // recieve positions of screen objects from server
                            String[] values = messageRaw.split(" ");
                            leftPaddleY = Integer.parseInt(values[1]);
                            rightPaddleY = Integer.parseInt(values[2]);
                            ballX = Integer.parseInt(values[3]);
                            ballY = Integer.parseInt(values[4]);
                            playerScores[0] = values[5];
                            playerScores[1] = values[6];
                            // update screen to display new positions
                            status = 2;
                            panel.repaint();
                            break;
                        case "WINS":
                            // prompt user to either play again or disconnect
                            status = 3;
                            String popupText = messageRaw.substring(5) + " won the game!\nDo you want to play again?";
                            int result = JOptionPane.showConfirmDialog(panel, popupText, "Play again?", JOptionPane.YES_NO_OPTION);
                            panel.repaint();
                            if (result == JOptionPane.YES_OPTION) {
                                sock.send("RSET");
                            } else {
                                sock.send("DISC");
                                disconnect();
                                status = 0;
                                panel.repaint();
                                return;
                            }
                            break;
                        case "DISC":
                            sock.send("DISC");
                            disconnect();
                            popupWarning("Disconnected by server: " + messageRaw.substring(5));
                            return;
                        default:
                            System.out.println("Unexpected message from server: '" + messageRaw + "'");
                            break;
                    }
                }
            } catch (IOException ex) {
                disconnect();
                popupError("Server connection error: " + ex.toString());
                return;
            }
        }
    }
    
    static class GamePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            Graphics2D g2 = (Graphics2D)g;
            
            drawBackground(g2);
            if (status == 0) {
                drawStartScreen(g2);
            } else {
                drawGameText(g2);
                if (whichPlayer == 1) {
                    drawPaddle(g2, 20, (status % 2 == 1) ? mouseY : leftPaddleY);
                    drawPaddle(g2, 430, rightPaddleY);
                } else if (whichPlayer == 2) {
                    drawPaddle(g2, 20, leftPaddleY);
                    drawPaddle(g2, 430, (status % 2 == 1) ? mouseY : rightPaddleY);
                }
                drawBall(g2, ballX, ballY);
            }
        }
        
        void drawTextCentered(Graphics g, String text, int x, int y) {
            FontMetrics met = g.getFontMetrics();
            int charHeight = (int)(met.getAscent()*0.8);
            g.drawString(text, x - met.stringWidth(text)/2, y + charHeight/2);
        }
        
        void drawBackground(Graphics g) {
            g.setColor(new Color(199, 220, 255));
            g.fillRect(0, 0, 225, 450);
            g.setColor(new Color(255, 220, 220));
            g.fillRect(225, 0, 225, 450);
        }
        
        void drawStartScreen(Graphics g) {
            // title
            g.setFont(new Font("Arial",Font.PLAIN,70));
            g.setColor(new Color(43, 121, 255));
            drawTextCentered(g, "Multi", 135, 140);
            g.setColor(new Color(100, 100, 100));
            drawTextCentered(g, "-", 225, 140);
            g.setColor(new Color(255, 57, 43));
            drawTextCentered(g, "Pong", 322, 140);
            // start button
            g.setColor(new Color(180, 180, 180));
            g.fillRect(225-85, 300-30, 170, 60);
            g.setColor(new Color(150, 150, 150));
            g.fillRect(225-80, 300-25, 160, 50);
            g.setColor(new Color(100, 100, 100));
            g.setFont(new Font("Arial",Font.PLAIN,50));
            drawTextCentered(g, "Start", 225, 299);
        }
        
        void drawGameText(Graphics g) {
            // waiting message
            if (status % 2 == 1) {
                g.setFont(new Font("Arial",Font.PLAIN,25));
                g.setColor(new Color(180, 180, 180));
                drawTextCentered(g, "Waiting for opponent...", 225, 180);
            }
            // player 1 name and score
            g.setFont(new Font("Arial",Font.PLAIN,30));
            g.setColor(new Color(145, 186, 255));
            drawTextCentered(g, (status == 1) ? localName : playerNames[0], 110, 20);
            drawTextCentered(g, (status == 1) ? "0" : playerScores[0], 206, 20);
            // player 2 name and score
            g.setColor(new Color(255, 145, 145));
            drawTextCentered(g, (status == 1) ? "???" : playerNames[1], 340, 20);
            drawTextCentered(g, (status == 1) ? "0" : playerScores[1], 241, 20);
            
        }
        
        void drawPaddle(Graphics g, int x, int y) {
            g.setColor((x < 200) ? new Color(145, 186, 255) : new Color(255, 145, 145));
            g.fillRect(x-8, y-60, 16, 120);
            g.setColor((x < 200) ? new Color(43, 121, 255) : new Color(255, 57, 43));
            g.fillRect(x-6, y-58, 12, 116);
        }
        
        void drawBall(Graphics g, int x, int y) {
            g.setColor(new Color(50, 50, 50));
            g.fillOval(x-12, y-12, 24, 24);
            g.setColor(new Color(80, 80, 80));
            g.fillOval(x-10, y-10, 20, 20);
            g.setColor(new Color(160, 160, 160));
            g.fillOval(x-7, y-8, 7, 6);
        }
    }
}
