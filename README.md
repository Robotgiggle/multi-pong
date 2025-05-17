# Usage
To host a server, build and run PongServer and enter a port number. All relevant info will then be displayed in the server log window. Closing this window will shut down the server.

To play the game, build and run PongClient and press the start button. Provide a username, along with the IP address and port of the server you want to join. This will place you in a waiting state until a second player joins the same server. Once this happens, the game will start after 2 seconds.

Each player controls their paddle using their mouse, and tries to block the ball as much as possible. If the ball flies off your side of the screen, your opponent gets a point. Whoever collects 10 points first wins the game!

Once the game ends, each player will be offered the option to play again. If both players select 'yes', the game will restart after 1 second. If either player selects 'no' or closes the popup, the game will not restart and each player will be sent back to the start screen once they resolve their popup.

# Protocol
The server and client programs communicate using a specific protocol, which you can replicate if you want to design your own version of either. Each message begins with a 4-letter code, which may be followed by various other information depending on the message type. Each message type is described below.

Messages used for initial setup:
- `JOIN <username>`: Sent immediately by client after connecting to server, providing the player's username.
- `SUCC <playerNum>`: Reply to the JOIN message that indicates a successful connection. Also indicates whether this client is player 1 or player 2.
- `FULL`: Reply to the JOIN message that indicates the server is already full.
- `USRS\n<username1>\n<username2>`: Sent from the server to each client once both players have joined, providing the two usernames and indicating that the game should begin. Uses newlines as separators so that usernames can contain spaces.

Messages used during gameplay:
- `MOUS <mouseY>`: Sent by client in response to USRS or GAME, to indicate the current position of the player's mouse.
- `GAME <leftPaddleY> <rightPaddleY> <ballX> <ballY> <player1Score> <player2Score>`: Sent by server in response to MOUS, updates the client with the current state of the game.
- `WINS <winnerName>`: Sent by server in response to MOUS if someone has won the game. Provides the name of the winning player.
- `RSET`: Sent by client after the end of a game, if the player has indicated that they want to restart.
- `DISC <reason>`: Sent by server to indicate that the client should disconnect, and provides a reason.
- `DISC`: Sent by client if it receives a DISC message from the server, if the window is closed, or after the end of the game if the player has indicated that they do not want to restart. Indicates that the server should close its side of the connection.

